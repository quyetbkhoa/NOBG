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
            // Gửi lịch sử 20 tin gần nhất để Gemini có ngữ cảnh
            val history = _messages.value
                .takeLast(20)
                .filterNot { it.isError }
                .map { it.role to it.text }

            val systemPrompt = "Bạn là trợ lý AI thông minh trong app NOBG (quản lý app chạy ngầm, đóng băng app, " +
                "ép dừng, thống kê pin, đọc thông báo, hẹn giờ tắt máy, widget). Trả lời bằng tiếng Việt, ngắn gọn, tự nhiên, có trọng tâm. " +
                "Nếu được hỏi về cách dùng app hãy hướng dẫn cụ thể từng bước. " +
                "BẠN CÓ QUYỀN ĐỌC DỮ LIỆU THẬT TRÊN MÁY bằng các công cụ: " +
                "get_device_info (hãng, model, Android, RAM, bộ nhớ trong, màn hình, thời gian bật máy), " +
                "get_battery_info (phần trăm pin, nhiệt độ, điện áp, trạng thái sạc), " +
                "get_app_usage_today (app dùng nhiều nhất hôm nay), " +
                "get_nobg_status (số app quản lý/đóng băng, trạng thái shell), " +
                "get_nobg_settings (toàn bộ cài đặt NOBG), " +
                "get_installed_apps / get_app_info (danh sách và chi tiết app đã cài). " +
                "LUẬT BẮT BUỘC: " +
                "1. Khi người dùng hỏi về thông tin thiết bị (pin, RAM, dung lượng, nhiệt độ, sạc, app, thời gian dùng, cài đặt...), " +
                "BẠN PHẢI gọi công cụ tương ứng TRƯỚC, rồi trả lời dựa trên kết quả thực tế. " +
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
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }

    fun dismissConfigError() {
        _configError.value = null
    }
}
