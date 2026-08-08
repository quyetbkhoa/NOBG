package com.nobg.app.data

import org.json.JSONObject

/** Kết quả gọi AI - không bao giờ ném exception ra ngoài */
sealed interface AiResult {
    data class Success(val text: String) : AiResult
    data class Error(val type: AiErrorType, val message: String) : AiResult

    /** AI yêu cầu gọi một công cụ cục bộ (function calling) */
    data class ToolCall(val name: String, val args: JSONObject, val id: String = "") : AiResult
}

/** Alias cho AiResult.ToolCall (giữ tên cũ cho các nơi đang dùng) */
typealias AiToolCall = AiResult.ToolCall

/** Định nghĩa một "công cụ" AI có thể gọi để đọc dữ liệu trên máy (function calling) */
data class AiToolDefinition(
    val name: String,
    val description: String,
    val parameters: JSONObject
)

enum class AiErrorType {
    NO_API_KEY,        // Chưa nhập key
    INVALID_API_KEY,   // 401/403 - key sai/hết hạn
    RATE_LIMITED,      // 429 - vượt quota free tier
    MODEL_NOT_FOUND,   // 404 - model sai/không tồn tại
    BLOCKED,           // Nội dung bị chặn bởi safety filter
    BAD_REQUEST,       // 400 - prompt/schema không hợp lệ
    SERVER_ERROR,      // 5xx
    NETWORK,           // Mất mạng/DNS/SSL
    TIMEOUT,           // Quá thời gian chờ
    EMPTY_RESPONSE,    // Server trả rỗng
    PARSE_ERROR,       // Không parse được phản hồi
    UNKNOWN
}

/** Giao diện chung cho mọi provider AI (Gemini, Groq, OpenRouter...) */
interface AiClient {

    /**
     * Gửi prompt tới provider AI.
     * @param systemPrompt prompt hệ thống (định hướng hành vi)
     * @param userPrompt nội dung người dùng
     * @param jsonMode yêu cầu server trả JSON thuần (nếu provider hỗ trợ)
     * @param timeoutMs thời gian tối đa cho toàn bộ lần gọi (bao gồm retry)
     * @param tools danh sách công cụ AI có thể gọi (function calling) - null = tắt
     * @param onToolCall callback thực thi công cụ cục bộ, trả về kết quả chuỗi cho AI
     */
    suspend fun generateContent(
        systemPrompt: String? = null,
        userPrompt: String,
        jsonMode: Boolean = false,
        timeoutMs: Long = 15000L,
        tools: List<AiToolDefinition>? = null,
        onToolCall: (suspend (String, JSONObject) -> String)? = null
    ): AiResult

    /** Kiểm tra kết nối + key + model (dùng cho nút "Kiểm tra kết nối" trong Cài đặt) */
    suspend fun testConnection(): AiResult =
        generateContent(
            systemPrompt = null,
            userPrompt = "Trả lời đúng 1 từ: OK",
            timeoutMs = 10000L
        )
}

/** Các provider AI được hỗ trợ */
enum class AiProvider(
    val id: String,
    val displayName: String,
    val shortDesc: String,
    val keyHint: String,
    val keyUrl: String,
    val defaultModel: String,
    val defaultModelLabel: String
) {
    GEMINI(
        id = "gemini",
        displayName = "Gemini (Google)",
        shortDesc = "Free tier ~10-15 req/phút, key tạo tại Google AI Studio",
        keyHint = "AIza…",
        keyUrl = "https://aistudio.google.com/apikey",
        defaultModel = "gemini-2.0-flash",
        defaultModelLabel = "Gemini 2.0 Flash"
    ),
    GROQ(
        id = "groq",
        displayName = "Groq (siêu nhanh)",
        shortDesc = "Llama/Qwen/DeepSeek chạy trên chip LPU, free không cần thẻ",
        keyHint = "gsk_…",
        keyUrl = "https://console.groq.com/keys",
        defaultModel = "llama-3.3-70b-versatile",
        defaultModelLabel = "Llama 3.3 70B"
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter (đa mô hình)",
        shortDesc = "Nhiều model free (:free), đăng ký tài khoản là có key",
        keyHint = "sk-or-…",
        keyUrl = "https://openrouter.ai/keys",
        defaultModel = "deepseek/deepseek-r1:free",
        defaultModelLabel = "DeepSeek R1 (free)"
    );

    companion object {
        fun fromId(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: GEMINI

        /** Các model đề xuất theo từng provider (người dùng vẫn có thể dùng model tùy chỉnh) */
        fun suggestedModels(provider: AiProvider): List<Pair<String, String>> = when (provider) {
            GEMINI -> listOf(
                "gemini-2.5-flash" to "⚡ Gemini 2.5 Flash (mới nhất, cân bằng)",
                "gemini-2.0-flash" to "🚀 Gemini 2.0 Flash (nhanh, khuyến nghị)",
                "gemini-1.5-flash" to "🕰️ Gemini 1.5 Flash (tương thích cũ)"
            )
            GROQ -> listOf(
                "llama-3.3-70b-versatile" to "🦙 Llama 3.3 70B (đa năng, khuyến nghị)",
                "llama-3.1-8b-instant" to "⚡ Llama 3.1 8B (siêu nhanh)",
                "qwen-2.5-32b" to "🧠 Qwen 2.5 32B",
                "deepseek-r1-distill-llama-70b" to "🤔 DeepSeek R1 Distill 70B (suy luận)"
            )
            OPENROUTER -> listOf(
                "deepseek/deepseek-r1:free" to "🤔 DeepSeek R1 (suy luận, free)",
                "meta-llama/llama-3.3-70b-instruct:free" to "🦙 Llama 3.3 70B (free)",
                "qwen/qwen-2.5-7b-instruct:free" to "🧠 Qwen 2.5 7B (free)",
                "google/gemini-2.0-flash-exp:free" to "⚡ Gemini 2.0 Flash Exp (free)"
            )
        }
    }
}

/** Tạo client AI theo provider đang chọn trong Cài đặt */
object AiClientFactory {

    fun create(repo: NobgRepository): AiClient {
        val provider = AiProvider.fromId(repo.getAiProvider())
        return when (provider) {
            AiProvider.GEMINI -> GeminiApiClient(
                apiKeyProvider = { repo.getAiProviderApiKey(AiProvider.GEMINI) },
                modelProvider = { repo.getAiModel() }
            )
            AiProvider.GROQ -> OpenAiCompatClient(
                baseUrl = "https://api.groq.com/openai/v1",
                providerLabel = provider.displayName,
                apiKeyProvider = { repo.getAiProviderApiKey(AiProvider.GROQ) },
                modelProvider = { repo.getAiModel() }
            )
            AiProvider.OPENROUTER -> OpenAiCompatClient(
                baseUrl = "https://openrouter.ai/api/v1",
                providerLabel = provider.displayName,
                apiKeyProvider = { repo.getAiProviderApiKey(AiProvider.OPENROUTER) },
                modelProvider = { repo.getAiModel() }
            )
        }
    }
}
