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
 * Client gọi Gemini API (Generative Language API) - free tier.
 * Xử lý ĐẦY ĐỦ mọi case lỗi:
 *  - Thiếu key, key sai (401/403), rate limit (429), model không tồn tại (404)
 *  - Nội dung bị chặn (safety), server lỗi (5xx), network, timeout
 *  - Response rỗng / parse lỗi / JSON mode lỗi
 * Nguyên tắc: KHÔNG BAO GIỜ ném exception ra ngoài - luôn trả về GeminiResult.
 */
class GeminiApiClient(
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String = { DEFAULT_MODEL }
) {

    companion object {
        private const val TAG = "GeminiApi"
        const val DEFAULT_MODEL = "gemini-2.0-flash"
        const val FALLBACK_MODEL = "gemini-1.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        // Giới hạn miễn phí để tránh spam API
        private const val MAX_TOKENS = 1024
        private const val RETRY_ATTEMPTS = 2
        private const val RETRY_BASE_DELAY_MS = 500L

        val RETRYABLE_TYPES = setOf(
            // KHÔNG retry 429: server đang từ chối do quota, retry chỉ làm tăng số request cháy thêm quota phút
            GeminiErrorType.SERVER_ERROR,
            GeminiErrorType.NETWORK,
            GeminiErrorType.TIMEOUT
        )
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Kết quả gọi Gemini - không bao giờ ném exception */
    sealed interface GeminiResult {
        data class Success(val text: String) : GeminiResult
        data class Error(val type: GeminiErrorType, val message: String) : GeminiResult
    }

    enum class GeminiErrorType {
        NO_API_KEY,        // Chưa nhập key
        INVALID_API_KEY,   // 401/403 - key sai/hết hạn
        RATE_LIMITED,      // 429 - vượt quota free tier
        MODEL_NOT_FOUND,   // 404 - model sai
        BLOCKED,           // Nội dung bị chặn bởi safety filter
        BAD_REQUEST,       // 400 - prompt/schema không hợp lệ
        SERVER_ERROR,      // 5xx
        NETWORK,           // Mất mạng/DNS/SSL
        TIMEOUT,           // Quá thời gian chờ
        EMPTY_RESPONSE,    // Server trả rỗng
        PARSE_ERROR,       // Không parse được JSON
        UNKNOWN
    }

    /**
     * Gửi prompt tới Gemini.
     * @param systemPrompt prompt hệ thống (định hướng hành vi)
     * @param userPrompt nội dung người dùng
     * @param jsonMode yêu cầu server trả JSON thuần (responseMimeType)
     * @param timeoutMs thời gian tối đa cho toàn bộ lần gọi (bao gồm retry)
     */
    suspend fun generateContent(
        systemPrompt: String? = null,
        userPrompt: String,
        jsonMode: Boolean = false,
        timeoutMs: Long = 15000L
    ): GeminiResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) {
            return@withContext GeminiResult.Error(
                GeminiErrorType.NO_API_KEY,
                "Chưa nhập API key Gemini. Vào Cài đặt -> AI (Gemini) để nhập key miễn phí từ Google AI Studio."
            )
        }

        val model = modelProvider().trim().ifEmpty { DEFAULT_MODEL }
        var lastError: GeminiResult.Error? = null

        // Thử model chính, nếu 404 thì fallback model cũ
        for (attempt in 0..RETRY_ATTEMPTS) {
            val result = callOnce(systemPrompt, userPrompt, jsonMode, model, apiKey, timeoutMs)
            when (result) {
                is GeminiResult.Success -> return@withContext result
                is GeminiResult.Error -> {
                    // Retry cho rate limit / server / network
                    if (result.type in RETRYABLE_TYPES && attempt < RETRY_ATTEMPTS) {
                        delay(RETRY_BASE_DELAY_MS * (attempt + 1))
                        lastError = result
                        continue
                    }
                    // Model không tồn tại -> fallback model cũ
                    if (result.type == GeminiErrorType.MODEL_NOT_FOUND && model != FALLBACK_MODEL) {
                        lastError = result
                        val fallbackResult = callOnce(systemPrompt, userPrompt, jsonMode, FALLBACK_MODEL, apiKey, timeoutMs)
                        if (fallbackResult is GeminiResult.Success) return@withContext fallbackResult
                    }
                    return@withContext result
                }
            }
        }
        lastError ?: GeminiResult.Error(GeminiErrorType.UNKNOWN, "Lỗi không xác định khi gọi Gemini.")
    }

    private suspend fun callOnce(
        systemPrompt: String?,
        userPrompt: String,
        jsonMode: Boolean,
        model: String,
        apiKey: String,
        timeoutMs: Long
    ): GeminiResult {
        return try {
            val body = buildRequestBody(systemPrompt, userPrompt, jsonMode)
            val request = Request.Builder()
                .url("$BASE_URL/$model:generateContent?key=$apiKey")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val startTime = System.currentTimeMillis()
            val response = withTimeoutGuarded(timeoutMs) {
                httpClient.newCall(request).execute()
            } ?: return GeminiResult.Error(
                GeminiErrorType.TIMEOUT,
                "Gemini phản hồi quá lâu ($timeoutMs ms). Hãy thử lại sau."
            )

            response.use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return handleHttpError(resp.code, bodyStr)
                }
                parseSuccess(bodyStr, System.currentTimeMillis() - startTime)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error calling Gemini", e)
            val isTimeout = e is java.net.SocketTimeoutException ||
                e.message?.contains("timeout", ignoreCase = true) == true
            GeminiResult.Error(
                if (isTimeout) GeminiErrorType.TIMEOUT else GeminiErrorType.NETWORK,
                if (isTimeout) "Hết thời gian chờ kết nối Gemini." else "Lỗi mạng khi kết nối Gemini: ${e.message ?: "không xác định"}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error calling Gemini", e)
            GeminiResult.Error(GeminiErrorType.UNKNOWN, "Lỗi không xác định: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Execute với timeout riêng (đã có read/connect timeout ở client, đây là lớp bảo vệ cuối) */
    private suspend fun <T> withTimeoutGuarded(timeoutMs: Long, block: () -> T): T? {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) { block() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Gemini call timed out after ${timeoutMs}ms")
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
        val contents = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            contents.put(JSONObject().put("role", "model").put("parts", JSONArray().put(JSONObject().put("text", "OK, tôi đã hiểu quy tắc."))))
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", userPrompt))))

        val generationConfig = JSONObject()
            .put("maxOutputTokens", MAX_TOKENS)
            .put("temperature", 0.4)
        if (jsonMode) {
            generationConfig.put("responseMimeType", "application/json")
        }

        return JSONObject()
            .put("contents", contents)
            .put("generationConfig", generationConfig)
            .toString()
    }

    private fun handleHttpError(code: Int, body: String): GeminiResult.Error {
        val serverMessage = extractErrorMessage(body)
        return when (code) {
            400 -> GeminiResult.Error(GeminiErrorType.BAD_REQUEST, "Yêu cầu không hợp lệ (400): ${serverMessage ?: "kiểm tra lại nội dung"}")
            401, 403 -> GeminiResult.Error(
                GeminiErrorType.INVALID_API_KEY,
                "API key không hợp lệ hoặc đã hết hạn ($code). Vào Google AI Studio (aistudio.google.com) để tạo key mới."
            )
            404 -> GeminiResult.Error(GeminiErrorType.MODEL_NOT_FOUND, "Model không tồn tại (404): ${serverMessage ?: "kiểm tra tên model"}")
            429 -> GeminiResult.Error(
                GeminiErrorType.RATE_LIMITED,
                buildString {
                    append("Đã vượt giới hạn miễn phí (429).")
                    if (serverMessage != null) append("\nServer: $serverMessage")
                    append("\nCách xử lý: tạo key MỚI từ aistudio.google.com/apikey (key từ Cloud Console có thể có quota = 0), đợi 1 phút rồi thử lại, hoặc kiểm tra quota tại Google AI Studio.")
                }
            )
            in 500..599 -> GeminiResult.Error(GeminiErrorType.SERVER_ERROR, "Máy chủ Gemini đang lỗi ($code). Thử lại sau ít phút.")
            else -> GeminiResult.Error(GeminiErrorType.UNKNOWN, "Lỗi không xác định từ server ($code): ${serverMessage ?: ""}")
        }
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

    /** Parse response chuẩn: candidates[0].content.parts[].text */
    private fun parseSuccess(body: String, elapsedMs: Long): GeminiResult {
        return try {
            val root = JSONObject(body)

            // Kiểm tra nội dung bị chặn bởi safety filter
            val promptFeedback = root.optJSONObject("promptFeedback")
            val blockReason = promptFeedback?.optString("blockReason", "") ?: ""
            if (blockReason.isNotBlank()) {
                return GeminiResult.Error(
                    GeminiErrorType.BLOCKED,
                    "Yêu cầu bị chặn bởi bộ lọc an toàn của Gemini (lý do: $blockReason)."
                )
            }

            val candidates = root.optJSONArray("candidates") ?: JSONArray()
            if (candidates.length() == 0) {
                return GeminiResult.Error(
                    GeminiErrorType.EMPTY_RESPONSE,
                    "Gemini không trả về nội dung (candidates rỗng). Hãy thử lại."
                )
            }

            val candidate = candidates.optJSONObject(0)
            val finishReason = candidate?.optString("finishReason", "")
            if (finishReason == "SAFETY") {
                return GeminiResult.Error(
                    GeminiErrorType.BLOCKED,
                    "Nội dung trả về bị chặn bởi bộ lọc an toàn của Gemini."
                )
            }

            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val texts = mutableListOf<String>()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val text = part.optString("text", "").trim()
                if (text.isNotBlank()) texts.add(text)
            }
            if (texts.isEmpty()) {
                return GeminiResult.Error(
                    GeminiErrorType.EMPTY_RESPONSE,
                    "Gemini trả về nhưng không có nội dung text. Hãy thử lại."
                )
            }

            GeminiResult.Success(texts.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini response (elapsed=${elapsedMs}ms): ${body.take(300)}", e)
            GeminiResult.Error(GeminiErrorType.PARSE_ERROR, "Không phân tích được phản hồi Gemini. Hãy thử lại.")
        }
    }

    /** Kiểm tra kết nối + key + model (dùng cho nút "Kiểm tra kết nối" trong Cài đặt) */
    suspend fun testConnection(): GeminiResult =
        generateContent(
            systemPrompt = null,
            userPrompt = "Trả lời đúng 1 từ: OK",
            timeoutMs = 10000L
        )
}
