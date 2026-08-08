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
        // KHÔNG retry 429: quota phút của free tier rất thấp, retry chỉ làm cháy thêm quota
        private val RETRYABLE_TYPES = setOf(
            AiErrorType.SERVER_ERROR,
            AiErrorType.NETWORK,
            AiErrorType.TIMEOUT
        )
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
        timeoutMs: Long
    ): AiResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) {
            return@withContext AiResult.Error(
                AiErrorType.NO_API_KEY,
                "Chưa nhập API key cho $providerLabel. Vào màn hình AI Trợ lý (trang chủ) để nhập key miễn phí."
            )
        }

        val model = modelProvider().trim()
        var lastError: AiResult.Error? = null

        for (attempt in 0..RETRY_ATTEMPTS) {
            val result = callOnce(systemPrompt, userPrompt, jsonMode, model, apiKey, timeoutMs)
            when (result) {
                is AiResult.Success -> return@withContext result
                is AiResult.Error -> {
                    if (result.type in RETRYABLE_TYPES && attempt < RETRY_ATTEMPTS) {
                        delay(RETRY_BASE_DELAY_MS * (attempt + 1))
                        lastError = result
                        continue
                    }
                    return@withContext result
                }
            }
        }
        lastError ?: AiResult.Error(AiErrorType.UNKNOWN, "Lỗi không xác định khi gọi $providerLabel.")
    }

    private suspend fun callOnce(
        systemPrompt: String?,
        userPrompt: String,
        jsonMode: Boolean,
        model: String,
        apiKey: String,
        timeoutMs: Long
    ): AiResult {
        return try {
            val body = buildRequestBody(systemPrompt, userPrompt, jsonMode)
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
            } ?: return AiResult.Error(
                AiErrorType.TIMEOUT,
                "$providerLabel phản hồi quá lâu ($timeoutMs ms). Hãy thử lại sau."
            )

            response.use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return handleHttpError(resp.code, bodyStr)
                }
                parseSuccess(bodyStr)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error calling $providerLabel", e)
            val isTimeout = e is java.net.SocketTimeoutException ||
                e.message?.contains("timeout", ignoreCase = true) == true
            AiResult.Error(
                if (isTimeout) AiErrorType.TIMEOUT else AiErrorType.NETWORK,
                if (isTimeout) "Hết thời gian chờ kết nối $providerLabel."
                else "Lỗi mạng khi kết nối $providerLabel: ${e.message ?: "không xác định"}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error calling $providerLabel", e)
            AiResult.Error(AiErrorType.UNKNOWN, "Lỗi không xác định: ${e.message ?: e.javaClass.simpleName}")
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
        systemPrompt: String?,
        userPrompt: String,
        jsonMode: Boolean
    ): String {
        val messages = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.put(JSONObject().put("role", "user").put("content", userPrompt))

        val body = JSONObject()
            .put("model", modelProvider().trim())
            .put("messages", messages)
            .put("max_tokens", MAX_TOKENS)
            .put("temperature", 0.4)
        if (jsonMode) {
            body.put("response_format", JSONObject().put("type", "json_object"))
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

    /** Parse response chuẩn OpenAI: choices[0].message.content */
    private fun parseSuccess(body: String): AiResult {
        return try {
            val root = JSONObject(body)
            val choices = root.optJSONArray("choices") ?: JSONArray()
            if (choices.length() == 0) {
                return AiResult.Error(
                    AiErrorType.EMPTY_RESPONSE,
                    "$providerLabel không trả về nội dung (choices rỗng). Hãy thử lại."
                )
            }
            val message = choices.optJSONObject(0)?.optJSONObject("message")
            val text = message?.optString("content", "")?.trim() ?: ""
            if (text.isBlank()) {
                // Vài model trả finish_reason khi hết token
                val finishReason = choices.optJSONObject(0)?.optString("finish_reason", "")
                return AiResult.Error(
                    AiErrorType.EMPTY_RESPONSE,
                    "$providerLabel trả về trống (finish_reason=$finishReason). Hãy thử model khác hoặc thử lại."
                )
            }
            AiResult.Success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $providerLabel response: ${body.take(300)}", e)
            AiResult.Error(AiErrorType.PARSE_ERROR, "Không phân tích được phản hồi $providerLabel. Hãy thử lại.")
        }
    }
}
