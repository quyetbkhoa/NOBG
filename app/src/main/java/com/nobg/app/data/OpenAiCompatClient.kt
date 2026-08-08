package com.nobg.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client cho các provider dùng API chuẩn OpenAI (chat/completions):
 *  - Groq: https://api.groq.com/openai/v1
 *  - OpenRouter: https://openrouter.ai/api/v1
 * Nguyên tắc: KHÔNG BAO GIỜ ném exception ra ngoài - luôn trả về AiResult.
 */
class OpenAiCompatClient(
    private val baseUrl: String,
    private val providerLabel: String,
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String
) : AiClient {

    companion object {
        private const val TAG = "OpenAiCompat"
        private const val MAX_TOKENS = 1024
        private const val RETRY_ATTEMPTS = 1
        private const val RETRY_BASE_DELAY_MS = 600L
        private const val MAX_TOOL_ROUNDS = 3
        // KHÔNG retry 429: quota phút của free tier rất thấp, retry chỉ làm cháy thêm quota
        private val RETRYABLE_TYPES = setOf(
            AiErrorType.SERVER_ERROR,
            AiErrorType.NETWORK,
            AiErrorType.TIMEOUT
        )
    }

    /** Kết quả nội bộ của 1 lượt gọi (cần giữ message tool_calls để gửi lại ở lượt sau) */
    private sealed interface OpenAiResponse {
        data class Ok(val text: String) : OpenAiResponse
        data class Err(val error: AiResult.Error) : OpenAiResponse
        data class Tools(val assistantMessage: JSONObject, val calls: List<AiToolCall>) : OpenAiResponse
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun generateContent(
        systemPrompt: String?,
        userPrompt: String,
        jsonMode: Boolean,
        timeoutMs: Long,
        tools: List<AiToolDefinition>?,
        onToolCall: (suspend (String, JSONObject) -> String)?
    ): AiResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) {
            return@withContext AiResult.Error(
                AiErrorType.NO_API_KEY,
                "Chưa nhập API key cho $providerLabel. Vào màn hình AI Trợ lý (trang chủ) để nhập key miễn phí."
            )
        }

        val model = modelProvider().trim()
        val messages = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.put(JSONObject().put("role", "user").put("content", userPrompt))

        var activeTools = tools
        var lastError: AiResult.Error? = null
        var toolRounds = 0

        while (toolRounds <= MAX_TOOL_ROUNDS) {
            val result = callWithRetry(messages, jsonMode, model, apiKey, timeoutMs, activeTools)
            when (result) {
                is OpenAiResponse.Ok -> return@withContext AiResult.Success(result.text)
                is OpenAiResponse.Err -> {
                    // Một số server không hỗ trợ function calling: thử lại lần cuối không có tools
                    if (result.error.type == AiErrorType.BAD_REQUEST && activeTools != null) {
                        activeTools = null
                        lastError = result.error
                        continue
                    }
                    return@withContext result.error
                }
                is OpenAiResponse.Tools -> {
                    if (onToolCall == null) {
                        return@withContext AiResult.Error(
                            AiErrorType.UNKNOWN,
                            "$providerLabel yêu cầu gọi công cụ nhưng không được hỗ trợ. Thử lại."
                        )
                    }
                    messages.put(result.assistantMessage)
                    for (call in result.calls) {
                        val toolContent = try {
                            onToolCall(call.name, call.args)
                        } catch (e: Exception) {
                            "{\"error\":\"${(e.message ?: e.javaClass.simpleName).replace("\"", "'")}\"}"
                        }
                        messages.put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", call.id)
                                .put("content", toolContent)
                        )
                    }
                    toolRounds++
                }
            }
        }
        lastError?.let { return@withContext it }
        AiResult.Error(AiErrorType.UNKNOWN, "Lỗi không xác định khi gọi $providerLabel.")
    }

    private suspend fun callWithRetry(
        messages: JSONArray,
        jsonMode: Boolean,
        model: String,
        apiKey: String,
        timeoutMs: Long,
        tools: List<AiToolDefinition>?
    ): OpenAiResponse {
        var lastError: AiResult.Error? = null
        for (attempt in 0..RETRY_ATTEMPTS) {
            val result = callOnce(messages, jsonMode, model, apiKey, timeoutMs, tools)
            if (result is OpenAiResponse.Err &&
                result.error.type in RETRYABLE_TYPES &&
                attempt < RETRY_ATTEMPTS
            ) {
                delay(RETRY_BASE_DELAY_MS * (attempt + 1))
                lastError = result.error
                continue
            }
            return result
        }
        return OpenAiResponse.Err(
            lastError ?: AiResult.Error(AiErrorType.UNKNOWN, "Lỗi không xác định khi gọi $providerLabel.")
        )
    }

    private suspend fun callOnce(
        messages: JSONArray,
        jsonMode: Boolean,
        model: String,
        apiKey: String,
        timeoutMs: Long,
        tools: List<AiToolDefinition>?
    ): OpenAiResponse {
        return try {
            val body = buildRequestBody(messages, jsonMode, model, tools)
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://github.com/quyetbkhoa/NOBG")
                .header("X-Title", "NOBG")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = withTimeoutGuarded(timeoutMs) {
                httpClient.newCall(request).execute()
            } ?: return OpenAiResponse.Err(
                AiResult.Error(
                    AiErrorType.TIMEOUT,
                    "$providerLabel phản hồi quá lâu ($timeoutMs ms). Hãy thử lại sau."
                )
            )

            response.use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return OpenAiResponse.Err(handleHttpError(resp.code, bodyStr))
                }
                parseSuccess(bodyStr)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error calling $providerLabel", e)
            val isTimeout = e is java.net.SocketTimeoutException ||
                e.message?.contains("timeout", ignoreCase = true) == true
            OpenAiResponse.Err(
                AiResult.Error(
                    if (isTimeout) AiErrorType.TIMEOUT else AiErrorType.NETWORK,
                    if (isTimeout) "Hết thời gian chờ kết nối $providerLabel."
                    else "Lỗi mạng khi kết nối $providerLabel: ${e.message ?: "không xác định"}"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error calling $providerLabel", e)
            OpenAiResponse.Err(
                AiResult.Error(AiErrorType.UNKNOWN, "Lỗi không xác định: ${e.message ?: e.javaClass.simpleName}")
            )
        }
    }

    /** Execute với timeout riêng (đã có read/connect timeout ở client, đây là lớp bảo vệ cuối) */
    private suspend fun <T> withTimeoutGuarded(timeoutMs: Long, block: () -> T): T? {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) { block() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "$providerLabel call timed out after ${timeoutMs}ms")
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun buildRequestBody(
        messages: JSONArray,
        jsonMode: Boolean,
        model: String,
        tools: List<AiToolDefinition>?
    ): String {
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", MAX_TOKENS)
            .put("temperature", 0.4)
        if (jsonMode) {
            body.put("response_format", JSONObject().put("type", "json_object"))
        }
        if (!tools.isNullOrEmpty()) {
            val arr = JSONArray()
            tools.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", t.name)
                                .put("description", t.description)
                                .put("parameters", t.parameters)
                        )
                )
            }
            body.put("tools", arr)
        }
        return body.toString()
    }

    private fun handleHttpError(code: Int, body: String): AiResult.Error {
        val serverMessage = extractErrorMessage(body)
        return when (code) {
            400 -> AiResult.Error(
                AiErrorType.BAD_REQUEST,
                "Yêu cầu không hợp lệ (400): ${serverMessage ?: "kiểm tra lại nội dung hoặc model"}"
            )
            401, 403 -> AiResult.Error(
                AiErrorType.INVALID_API_KEY,
                "API key $providerLabel không hợp lệ hoặc đã hết hạn ($code). Vào ${providerKeyUrlHint()} tạo key mới."
            )
            404 -> AiResult.Error(
                AiErrorType.MODEL_NOT_FOUND,
                "Model không tồn tại (404): ${serverMessage ?: "kiểm tra lại tên model"}"
            )
            429 -> AiResult.Error(
                AiErrorType.RATE_LIMITED,
                buildString {
                    append("Đã vượt giới hạn miễn phí (429) của $providerLabel.")
                    if (serverMessage != null) append("\nServer: $serverMessage")
                    append("\nĐợi 1 phút rồi thử lại, hoặc kiểm tra quota tại ${providerKeyUrlHint()}.")
                }
            )
            in 500..599 -> AiResult.Error(AiErrorType.SERVER_ERROR, "Máy chủ $providerLabel đang lỗi ($code). Thử lại sau ít phút.")
            else -> AiResult.Error(AiErrorType.UNKNOWN, "Lỗi không xác định từ server ($code): ${serverMessage ?: ""}")
        }
    }

    private fun providerKeyUrlHint(): String = when {
        baseUrl.contains("groq") -> "console.groq.com/keys"
        baseUrl.contains("openrouter") -> "openrouter.ai/keys"
        else -> "trang quản lý key của nhà cung cấp"
    }

    /** Lấy error.message từ body JSON của API */
    private fun extractErrorMessage(body: String): String? {
        return try {
            val obj = JSONObject(body)
            obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            body.take(200)
        }
    }

    /** Parse response chuẩn OpenAI: choices[0].message.content hoặc tool_calls */
    private fun parseSuccess(body: String): OpenAiResponse {
        return try {
            val root = JSONObject(body)
            val choices = root.optJSONArray("choices") ?: JSONArray()
            if (choices.length() == 0) {
                return OpenAiResponse.Err(
                    AiResult.Error(
                        AiErrorType.EMPTY_RESPONSE,
                        "$providerLabel không trả về nội dung (choices rỗng). Hãy thử lại."
                    )
                )
            }
            val message = choices.optJSONObject(0)?.optJSONObject("message")

            // Kiểm tra tool_calls trước
            val toolCallsArr = message?.optJSONArray("tool_calls")
            if (toolCallsArr != null && toolCallsArr.length() > 0) {
                val calls = mutableListOf<AiToolCall>()
                for (i in 0 until toolCallsArr.length()) {
                    val tc = toolCallsArr.optJSONObject(i) ?: continue
                    val fn = tc.optJSONObject("function") ?: continue
                    val name = fn.optString("name", "").trim()
                    if (name.isBlank()) continue
                    val args = try {
                        JSONObject(fn.optString("arguments", "{}"))
                    } catch (_: Exception) {
                        JSONObject()
                    }
                    calls.add(AiToolCall(name, args, tc.optString("id", "")))
                }
                if (calls.isNotEmpty()) {
                    return OpenAiResponse.Tools(message!!, calls)
                }
            }

            val text = message?.optString("content", "")?.trim() ?: ""
            if (text.isBlank()) {
                // Vài model trả finish_reason khi hết token
                val finishReason = choices.optJSONObject(0)?.optString("finish_reason", "")
                return OpenAiResponse.Err(
                    AiResult.Error(
                        AiErrorType.EMPTY_RESPONSE,
                        "$providerLabel trả về trống (finish_reason=$finishReason). Hãy thử model khác hoặc thử lại."
                    )
                )
            }
            OpenAiResponse.Ok(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $providerLabel response: ${body.take(300)}", e)
            OpenAiResponse.Err(
                AiResult.Error(AiErrorType.PARSE_ERROR, "Không phân tích được phản hồi $providerLabel. Hãy thử lại.")
            )
        }
    }
}
