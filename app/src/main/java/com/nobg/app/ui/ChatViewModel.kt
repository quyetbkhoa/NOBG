package com.nobg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobg.app.data.AiClientFactory
import com.nobg.app.data.AiProvider
import com.nobg.app.data.AiResult
import com.nobg.app.data.DeviceTools
import com.nobg.app.data.NobgRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Một tin nhắn trong chat AI */
data class AiChatMessage(
    val id: Long,
    val role: AiChatRole,
    val text: String,
    val isError: Boolean = false
)

enum class AiChatRole { USER, ASSISTANT }

/** Yêu cầu xét duyệt của AI đang chờ người dùng quyết định (Chấp thuận / Từ chối) */
data class PendingApproval(
    val id: Long,
    val summary: String,
    val args: JSONObject,
    val deferred: CompletableDeferred<Boolean>
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NobgRepository(app)
    private val aiClient by lazy { AiClientFactory.create(repo) }

    /** Số tin nhắn gần nhất gửi kèm cho AI mỗi lượt */
    private val HISTORY_WINDOW = 20

    /** Khi tổng tin (không lỗi) vượt ngưỡng này, tóm tắt phần đầu hội thoại bằng AI để giữ trí nhớ */
    private val SUMMARY_TRIGGER = 36

    private val _messages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _configError = MutableStateFlow<String?>(null)
    val configError: StateFlow<String?> = _configError.asStateFlow()

    /** Yêu cầu xét duyệt của AI (thay đổi cài đặt) đang chờ người dùng quyết định */
    private val _pendingApproval = MutableStateFlow<PendingApproval?>(null)
    val pendingApproval: StateFlow<PendingApproval?> = _pendingApproval.asStateFlow()

    /** Tóm tắt phần hội thoại cũ (do AI tạo khi chat dài) - giữ trí nhớ dài hạn không tốn quota */
    private var conversationSummary: String? = null
    private var summaryUpToCount = 0
    private var summarizingInFlight = false

    private var nextId = 0L

    init {
        if (!repo.isAiFullyConfigured()) {
            _configError.value = "Chưa bật AI hoặc chưa nhập API key (${AiProvider.fromId(repo.getAiProvider()).displayName}). Vào màn hình AI Trợ lý (trang chủ) để cấu hình."
        }
    }

    /** Trả lời yêu cầu xét duyệt của AI */
    fun respondApproval(approved: Boolean) {
        _pendingApproval.value?.let { it.deferred.complete(approved) }
        _pendingApproval.value = null
    }

    /**
     * Chặn công cụ cần xét duyệt: dừng AI loop, hỏi người dùng qua dialog.
     * Chấp thuận -> áp dụng thay đổi và trả kết quả cho AI; Từ chối -> báo AI biết.
     */
    private suspend fun requestSettingApproval(args: JSONObject): String {
        val summary = DeviceTools.describeSettingChange(args)
        val deferred = CompletableDeferred<Boolean>()
        _pendingApproval.value = PendingApproval(++nextId, summary, args, deferred)

        val approved = withTimeoutOrNull(90_000L) { deferred.await() } ?: false
        _pendingApproval.value = null

        if (!approved) {
            return JSONObject()
                .put("approved", false)
                .put("reason", "Người dùng đã từ chối thay đổi cài đặt này. Đừng cố gắng thay đổi nữa.")
                .toString()
        }
        return DeviceTools.applySettings(args, getApplication(), repo)
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isSending.value) return

        if (!repo.isAiFullyConfigured()) {
            _configError.value = "Chưa bật AI hoặc chưa nhập API key (${AiProvider.fromId(repo.getAiProvider()).displayName}). Vào màn hình AI Trợ lý (trang chủ) để cấu hình."
            return
        }
        _configError.value = null

        val userMsg = AiChatMessage(++nextId, AiChatRole.USER, text)
        _messages.value = _messages.value + userMsg
        _inputText.value = ""
        _isSending.value = true

        viewModelScope.launch {
            // Gửi lịch sử gần nhất để có ngữ cảnh; phần cũ hơn đã được tóm tắt (nếu có)
            val history = _messages.value
                .takeLast(HISTORY_WINDOW)
                .filterNot { it.isError }
                .map { it.role to it.text }

// Zero-shot context: đọc sẵn tình trạng máy hiện tại (có cache 3s) để AI trả lời ngay
            // mà không phải tốn 1-2 lượt gọi tool lặp đi lặp lại mỗi tin nhắn
            val deviceContext = buildDeviceContext()
            val summaryNote = conversationSummary
                ?.takeIf { it.isNotBlank() }
                ?.let { "TÓM TẮT CUỘC TRÒ CHUYỆN TRƯỚC ĐÂY (không cần hỏi lại những gì đã có ở đây):\n$it\n\n" }
                ?: ""

            val systemPrompt = summaryNote + "Bạn là trợ lý AI thông minh trong app NOBG (quản lý app chạy ngầm, đóng băng app, " +
                "ép dừng, thống kê pin, đọc thông báo, hẹn giờ tắt máy, widget). Trả lời bằng tiếng Việt, ngắn gọn, tự nhiên, có trọng tâm. " +
                "Nếu được hỏi về cách dùng app hãy hướng dẫn cụ thể từng bước. " +
                "BẠN CÓ QUYỀN ĐỌC DỮ LIỆU THẬT TRÊN MÁY bằng các công cụ: " +
                "get_overall_stats (TỔNG HỢP pin + RAM + bộ nhớ + app dùng nhiều nhất + trạng thái NOBG - ưu tiên dùng tool này cho câu hỏi tổng quan), " +
                "get_device_info (hãng, model, Android, RAM, bộ nhớ trong, màn hình, thời gian bật máy), " +
                "get_battery_info (phần trăm pin, nhiệt độ, điện áp, trạng thái sạc), " +
                "get_app_usage_today (app dùng nhiều nhất hôm nay), " +
                "get_nobg_status (số app quản lý/đóng băng, trạng thái shell), " +
                "get_nobg_settings (toàn bộ cài đặt NOBG), " +
                "get_battery_history (lịch sử pin 24h), get_charging_sessions (các phiên sạc), " +
                "get_cpu_stats (thống kê CPU), " +
                "get_installed_apps / get_app_info (danh sách và chi tiết app đã cài). " +
                if (deviceContext.isNotBlank()) "NGỮ CẢNH HIỆN TẠI (số liệu thật vừa đọc xong, KHÔNG cần gọi tool lặp lại trừ khi người dùng cần số liệu mới hoặc chi tiết hơn):\n$deviceContext\n\n" else "" +
                "LUẬT BẮT BUỘC: " +
                "1. Khi người dùng hỏi về thông tin thiết bị (pin, RAM, dung lượng, nhiệt độ, sạc, app, thời gian dùng, cài đặt...), " +
                "BẠN PHẢI gọi công cụ tương ứng TRƯỚC (nếu NGỮ CẢNH HIỆN TẠI chưa có), rồi trả lời dựa trên kết quả thực tế. " +
                "2. TUYỆT ĐỐI KHÔNG nói những câu kiểu \"tôi không có quyền truy cập\", \"tôi không thể đọc dữ liệu\", " +
                "\"tôi không có quyền xem pin/máy của bạn\" - bạn CÓ quyền đọc qua công cụ. Hãy gọi công cụ trước. " +
                "3. Nếu công cụ báo lỗi quyền (ví dụ usage_access=false hoặc cần Shizuku/ADB), hãy nói rõ lỗi và HƯỚNG DẪN người dùng " +
                "cách bật quyền đó trong Cài đặt, đừng từ chối trả lời. " +
                "4. KHÔNG BAO GIỜ bịa số liệu: chỉ đưa thông tin lấy từ công cụ; nếu công cụ không có dữ liệu thì nói rõ là không có. " +
                "5. Khi người dùng RA LỆNH bật/tắt tính năng của NOBG (AI Trợ lý, tóm tắt/lọc thông báo, đọc thông báo, TTS, " +
                "âm thanh pin đầy, chủ đề...), hãy dùng công cụ set_nobg_setting - ứng dụng sẽ tự hỏi người dùng xác nhận trước khi áp dụng. " +
                "KHÔNG tự ý thay đổi cài đặt khi chưa được yêu cầu; nếu người dùng từ chối thì dừng lại, đừng lặp lại."

            // Ghi nhận các công cụ AI đã dùng để hiển thị cho người dùng
            val toolLabelsUsed = mutableListOf<String>()

            val result = aiClient.generateContent(
                systemPrompt = systemPrompt,
                userPrompt = history.joinToString("\n") { (role, t) ->
                    (if (role == AiChatRole.USER) "Người dùng: " else "Trợ lý: ") + t
                },
                timeoutMs = 20000L,
                tools = DeviceTools.definitions,
                onToolCall = { name, args ->
                    val def = DeviceTools.definitions.firstOrNull { it.name == name }
                    toolLabelsUsed.add(DeviceTools.labelOf(name))
                    if (def != null && def.requiresApproval) {
                        requestSettingApproval(args)
                    } else {
                        DeviceTools.execute(name, args, getApplication(), repo)
                    }
                }
            )

            val reply = when (result) {
                is AiResult.Success -> {
                    val toolNote = if (toolLabelsUsed.isEmpty()) "" else
                        "🔧 Đã dùng công cụ: ${toolLabelsUsed.joinToString(", ")}\n\n"
                    AiChatMessage(
                        id = ++nextId,
                        role = AiChatRole.ASSISTANT,
                        text = toolNote + result.text.trim()
                    )
                }
                is AiResult.Error -> AiChatMessage(
                    id = ++nextId,
                    role = AiChatRole.ASSISTANT,
                    text = result.message,
                    isError = true
                )
                is AiResult.ToolCall -> AiChatMessage(
                    id = ++nextId,
                    role = AiChatRole.ASSISTANT,
                    text = "AI yêu cầu gọi công cụ ${result.name} nhưng bị gián đoạn. Vui lòng thử lại.",
                    isError = true
                )
            }
            _messages.value = _messages.value + reply
            _isSending.value = false
            maybeSummarizeConversation()
        }
    }

    /**
     * Đọc tổng quan máy 1 lần (pin, RAM, bộ nhớ, app dùng nhiều, NOBG) để chèn vào system prompt.
     * Nhờ cache 3s của DeviceTools nên mỗi lượt chat chỉ tốn 1 lần đọc thật, các lượt sau dùng cache.
     */
    private suspend fun buildDeviceContext(): String {
        return try {
            val json = JSONObject(
                DeviceTools.execute("get_overall_stats", JSONObject(), getApplication(), repo)
            )
            val pin = json.optJSONObject("pin")
            val ram = json.optJSONObject("ram")
            val storage = json.optJSONObject("storage")
            val nobg = json.optJSONObject("nobg")
            val topApps = json.optJSONArray("top_apps_today")
            buildString {
                append("Pin: ")
                pin?.let {
                    append("${it.optString("percent")}%")
                    val temp = it.optString("temp_c")
                    if (temp.isNotBlank() && !it.isNull("temp_c") && temp != "-1.0") append(", ${temp}°C")
                    val src = it.optString("source")
                    if (src.isNotBlank() && src != "không xác định") append(", nguồn: $src")
                }
                append(" | RAM trống: ")
                ram?.let { append("${it.optString("available_gb")}/${it.optString("total_gb")} GB") }
                append(" | Bộ nhớ trống: ")
                storage?.let { append("${it.optString("free_gb")}/${it.optString("total_gb")} GB") }
                append(" | NOBG: ")
                nobg?.let { append("quản lý ${it.optString("managed")} app, đóng băng ${it.optString("frozen")}") }
                if (topApps != null && topApps.length() > 0) {
                    append(" | Hôm nay dùng nhiều nhất: ")
                    val labels = (0 until topApps.length()).map { i ->
                        val o = topApps.optJSONObject(i) ?: return@map "?"
                        o.optString("label").ifBlank { o.optString("package") }
                    }
                    append(labels.take(3).joinToString(", "))
                }
            }.toString()
        } catch (_: Exception) {
            ""
        }
    }

    /** Khi hội thoại quá dài, dùng AI tóm tắt phần đầu để giữ "trí nhớ" lâu dài mà không tốn quota gửi kép */
    private fun maybeSummarizeConversation() {
        val total = _messages.value.count { !it.isError }
        if (total < SUMMARY_TRIGGER) return
        if (summarizingInFlight) return
        if (total - summaryUpToCount < 10) return
        summarizingInFlight = true
        viewModelScope.launch {
            try {
                val oldPart = _messages.value
                    .filterNot { it.isError }
                    .dropLast(HISTORY_WINDOW)
                if (oldPart.isEmpty()) return@launch
                val text = oldPart.joinToString("\n") { msg ->
                    (if (msg.role == AiChatRole.USER) "Người dùng: " else "Trợ lý: ") + msg.text
                }
                val client = AiClientFactory.create(repo)
                val result = client.generateContent(
                    systemPrompt = "Bạn là bộ ghi nhớ của trợ lý AI trong app NOBG. Hãy tóm tắt cuộc trò chuyện dưới đây bằng tiếng Việt, tối đa 120 từ: " +
                        "giữ nguyên các yêu cầu của người dùng, thông tin thiết bị đã được báo cáo, và mọi điều quan trọng cần nhớ để trả lời tiếp. " +
                        "Chỉ đưa nội dung tóm lược, không lặp lại câu hỏi.",
                    userPrompt = text,
                    timeoutMs = 15000L
                )
                if (result is AiResult.Success && result.text.trim().isNotBlank()) {
                    conversationSummary = result.text.trim()
                    summaryUpToCount = total
                }
            } catch (_: Exception) {
                // Không làm hỏng chat khi tóm tắt lỗi
            } finally {
                summarizingInFlight = false
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        conversationSummary = null
        summaryUpToCount = 0
    }

    fun dismissConfigError() {
        _configError.value = null
    }
}
