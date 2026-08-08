package com.nobg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobg.app.data.AiClientFactory
import com.nobg.app.data.AiProvider
import com.nobg.app.data.AiResult
import com.nobg.app.data.DeviceTools
import com.nobg.app.data.NobgRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Một tin nhắn trong chat AI */
data class AiChatMessage(
    val id: Long,
    val role: AiChatRole,
    val text: String,
    val isError: Boolean = false
)

enum class AiChatRole { USER, ASSISTANT }

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

    private var nextId = 0L

    init {
        if (!repo.isAiFullyConfigured()) {
            _configError.value = "Chưa bật AI hoặc chưa nhập API key (${AiProvider.fromId(repo.getAiProvider()).displayName}). Vào màn hình AI Trợ lý (trang chủ) để cấu hình."
        }
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

            val systemPrompt = "Bạn là trợ lý AI trong app NOBG (quản lý ứng dụng chạy ngầm, đóng băng app, " +
                "ép dừng, thống kê pin, đọc thông báo, hẹn giờ tắt máy, widget). " +
                "Trả lời bằng tiếng Việt, ngắn gọn, dễ hiểu. Nếu được hỏi về cách dùng app hãy hướng dẫn cụ thể. " +
                "Bạn có thể đọc dữ liệu THẬT trên máy (thông tin máy, pin, thời gian dùng app, trạng thái NOBG, danh sách app) " +
                "bằng cách gọi công cụ tương ứng. Hãy dùng công cụ khi người dùng hỏi về thông tin thiết bị của họ."

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
                    toolLabelsUsed.add(DeviceTools.labelOf(name))
                    DeviceTools.execute(name, args, getApplication(), repo)
                }
            )

            val reply = when (result) {
                is AiResult.Success -> {
                    val toolNote = if (toolLabelsUsed.isEmpty()) "" else
                        "🔧 Đã đọc trên máy: ${toolLabelsUsed.joinToString(", ")}\n\n"
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
