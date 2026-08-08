package com.nobg.app.service

import android.app.Notification
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.text.isDigitsOnly
import com.nobg.app.data.AiResult
import com.nobg.app.data.NobgRepository
import com.nobg.app.data.NotificationReadConfigEntity
import com.nobg.app.data.NotificationReadMode
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Service lắng nghe thông báo hệ thống và tự động đọc bằng TTS.
 * Hỗ trợ lọc theo danh sách thiết bị Bluetooth, trích xuất tên người gửi thông minh,
 * lọc từ khóa tin nhắn và tùy chỉnh âm lượng giọng đọc.
 */
class NotificationReaderService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifReaderService"
        private const val DEBOUNCE_MS = 1500L // Cooldown 1.5 giây cho mỗi package
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: NobgRepository
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val lastReadTimestamps = mutableMapOf<String, Long>()
    private lateinit var audioManager: AudioManager
    // Theo dõi audio focus theo từng utterance để abandon đúng khi đọc xong
    private val focusByUtterance = java.util.concurrent.ConcurrentHashMap<String, Pair<AudioFocusRequest, Boolean>>()

    private val aiClient by lazy { com.nobg.app.data.AiClientFactory.create(repo) }

    override fun onCreate() {
        super.onCreate()
        repo = NobgRepository(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val viLocale = Locale("vi", "VN")
                val result = tts?.setLanguage(viLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(repo.getTtsSpeechRate())
                isTtsReady = true
                // Cài listener ĐÚNG MỘT LẦN để không bị mất onDone/onError của utterance trước
                // (nếu cài lại mỗi lần speak, audio focus có thể không bao giờ được nhả)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        releaseAudioFocus(id)
                    }
                    override fun onError(id: String?) {
                        releaseAudioFocus(id)
                    }
                })
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        isTtsReady = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // Skip ongoing notifications (music players, services, etc.)
        if (sbn.isOngoing) return

        // Skip our own notifications
        if (sbn.packageName == "com.nobg.app") return

        scope.launch {
            try {
                if (!shouldRead(sbn)) return@launch

                // Lấy cấu hình theo không gian người dùng (Không gian 2 có userId riêng),
                // fallback về cấu hình của user chính nếu chưa cấu hình riêng
                // Lưu ý: UserHandle.identifier bị @hide, nên dùng hashCode() (AOSP: hashCode() == identifier)
                val userId = sbn.user.hashCode()
                var config = repo.getNotifReadConfig(sbn.packageName, userId)
                if (config == null && userId != 0) {
                    config = repo.getNotifReadConfig(sbn.packageName, 0)
                }
                if (config == null) return@launch
                if (!config.isEnabled) return@launch

                // Kiểm tra bộ lọc từ khóa
                if (!matchesKeywordFilter(sbn, config.keywordFilter)) return@launch

                val text = buildSpeechText(sbn, config)
                if (text.isNotBlank()) {
                    // AI xử lý (lọc ưu tiên + tóm tắt) - fail-open: lỗi thì vẫn đọc text gốc
                    val finalText = maybeAiProcess(sbn, config, text)
                    if (finalText != null) {
                        speakWithAudioFocus(finalText)
                        lastReadTimestamps[sbn.packageName] = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification from ${sbn.packageName}", e)
            }
        }
    }

    private suspend fun shouldRead(sbn: StatusBarNotification): Boolean {
        if (!repo.isNotifReadGlobalEnabled()) return false
        if (!isTtsReady) return false
        if (isDndActive()) return false
        if (isDebounced(sbn.packageName)) return false
        if (repo.isNotifReadOnlySelectedBt() && !isSelectedBluetoothConnected()) return false
        return true
    }

    private fun isDndActive(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun isDebounced(pkg: String): Boolean {
        val lastTime = lastReadTimestamps[pkg] ?: return false
        return (System.currentTimeMillis() - lastTime) < DEBOUNCE_MS
    }

    @Suppress("MissingPermission")
    private suspend fun isSelectedBluetoothConnected(): Boolean {
        try {
            // Android 12+: cần quyền BLUETOOTH_CONNECT mới gọi được getConnectedDevices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                applicationContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, BT-only reading disabled")
                return false
            }

            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return false
            val adapter = btManager.adapter ?: return false
            if (!adapter.isEnabled) return false

            val selectedDevices = repo.getSelectedBtDevices()
            if (selectedDevices.isEmpty()) return false
            val selectedAddresses = selectedDevices.map { it.address }.toSet()

            val profiles = mutableListOf(
                BluetoothProfile.A2DP,
                BluetoothProfile.HEADSET
            )
            // LE Audio (tai nghe TWS hiện đại kết nối LE Audio là chính) - Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                profiles.add(BluetoothProfile.LE_AUDIO)
            }

            val connectedAddresses = mutableSetOf<String>()
            for (profile in profiles) {
                try {
                    btManager.getConnectedDevices(profile).forEach { connectedAddresses.add(it.address) }
                } catch (_: Exception) {}
            }

            return connectedAddresses.any { it in selectedAddresses }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth connection", e)
            return false
        }
    }

    /**
     * Xử lý thông báo bằng AI (nếu đã bật và đủ cấu hình).
     * Trả về:
     *  - text đã tóm tắt hoặc text gốc nếu AI lỗi (fail-open)
     *  - null nếu AI quyết định KHÔNG quan trọng (chỉ khi lọc ưu tiên bật và AI trả về important=false)
     * KHÔNG BAO GIỜ chặn đọc: mọi lỗi/timeout đều dẫn về text gốc.
     */
    private suspend fun maybeAiProcess(
        sbn: StatusBarNotification,
        config: NotificationReadConfigEntity,
        text: String
    ): String? {
        if (!repo.isAiFullyConfigured()) return text

        // Chỉ dùng AI khi đọc nội dung thực (không dùng cho APP_NAME_ONLY / SENDER_ONLY)
        if (config.readMode != NotificationReadMode.FULL_CONTENT &&
            config.readMode != NotificationReadMode.SMART_CHAT
        ) return text

        // 1. Lọc ưu tiên - chỉ bỏ qua đọc khi AI CHẮC CHẮN trả về important=false
        if (repo.isAiFilterEnabled()) {
            val important = aiIsImportant(sbn, text)
            if (important == false) return null
        }

        // 2. Tóm tắt - nếu AI lỗi/chậm thì dùng text gốc
        if (repo.isAiSummaryEnabled()) {
            val summary = aiSummarize(text)
            if (summary != null) return summary
        }

        return text
    }

    private suspend fun aiSummarize(text: String): String? = withTimeoutOrNull(3500L) {
        val result = aiClient.generateContent(
            systemPrompt = "Bạn là trợ lý tóm tắt thông báo tiếng Việt. Tóm tắt ngắn gọn dưới 25 từ, " +
                "giữ thông tin quan trọng nhất: người gửi, nội dung chính, mã OTP nếu có. " +
                "Chỉ trả về nội dung tóm tắt, không thêm lời dẫn.",
            userPrompt = "Tóm tắt thông báo sau: $text",
            timeoutMs = 3500L
        )
        when (result) {
            is com.nobg.app.data.AiResult.Success -> result.text.trim().takeIf { it.isNotBlank() && it.length < 300 }
            is com.nobg.app.data.AiResult.Error -> {
                Log.w(TAG, "AI summarize failed: ${result.type} - ${result.message}")
                null
            }
            is com.nobg.app.data.AiResult.ToolCall -> {
                Log.w(TAG, "AI summarize unexpected tool call: ${result.name}")
                null
            }
        }
    }

    private suspend fun aiIsImportant(sbn: StatusBarNotification, text: String): Boolean? = withTimeoutOrNull(3000L) {
        val appName = getAppLabel(sbn.packageName)
        val result = aiClient.generateContent(
            systemPrompt = "Bạn là bộ lọc thông báo tiếng Việt. Thông báo QUAN TRỌNG cần báo ngay: " +
                "tin nhắn cá nhân, OTP/mã xác thực, cuộc gọi, lịch hẹn, nhắc việc, cảnh báo. " +
                "KHÔNG quan trọng: quảng cáo, khuyến mãi, tin tức, trò chơi, mạng xã hội rác, điểm danh. " +
                "Trả về JSON thuần, chỉ đúng định dạng: {\"important\": true} hoặc {\"important\": false}",
            userPrompt = "App: $appName\nThông báo: $text",
            jsonMode = true,
            timeoutMs = 3000L
        )
        when (result) {
            is com.nobg.app.data.AiResult.Success -> {
                val cleaned = result.text.trim()
                    .removePrefix("```json").removePrefix("```")
                    .removeSuffix("```").trim()
                try {
                    org.json.JSONObject(cleaned).optBoolean("important", true)
                } catch (e: Exception) {
                    Log.w(TAG, "AI filter: invalid JSON response: $cleaned", e)
                    true // fail-open: JSON hỏng thì vẫn đọc
                }
            }
            is com.nobg.app.data.AiResult.Error -> {
                Log.w(TAG, "AI filter failed: ${result.type} - ${result.message}")
                true // fail-open: lỗi thì vẫn đọc
            }
            is com.nobg.app.data.AiResult.ToolCall -> {
                Log.w(TAG, "AI filter unexpected tool call: ${result.name}")
                true // fail-open: vẫn đọc
            }
        }
    }

    /** Kiểm tra xem thông báo có khớp với từ khóa đã đặt hay không */
    private fun matchesKeywordFilter(sbn: StatusBarNotification, filter: String): Boolean {
        if (filter.isBlank()) return true // Không đặt từ khóa => Đọc tất cả

        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val textLines = extractTextLines(extras)
        val combinedContent = "$title $text $bigText ${textLines.joinToString(" ")}".lowercase()

        val keywords = filter.split(",", ";").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return true

        return keywords.any { combinedContent.contains(it) }
    }

    /** Lấy toàn bộ các dòng văn bản (chat apps hay dùng EXTRA_TEXT_LINES) */
    private fun extractTextLines(extras: Bundle?): List<String> {
        val lines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: return emptyList()
        return lines.filterNotNull().map { it.toString() }
    }

    /** Lấy nội dung đầy đủ nhất có thể từ Notification (kể cả tin nhắn dài) */
    private fun extractContent(extras: Bundle?): String {
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val lines = extractTextLines(extras)

        val parts = mutableListOf<String>()
        if (text.isNotBlank()) parts.add(text)
        if (bigText.isNotBlank() && bigText != text) parts.add(bigText)
        parts.addAll(lines.filter { it.isNotBlank() && it != text && it != bigText })
        return parts.joinToString(". ")
    }

    /** Trích xuất tên người gửi thông minh từ Notification */
    private fun extractSenderName(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras ?: return ""
        val appName = getAppLabel(sbn.packageName)

        // 1. Lấy từ conversation title hoặc title
        var title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        if (title.isNullOrBlank()) {
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        }
        if (title.isNullOrBlank()) {
            title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
        }

        if (title.isNullOrBlank()) return ""

        // Bỏ qua nếu title chính là tên app hoặc chuỗi mặc định
        if (title.equals(appName, ignoreCase = true) ||
            title.equals("Zalo", ignoreCase = true) ||
            title.equals("Messenger", ignoreCase = true) ||
            title.contains("tin nhắn mới", ignoreCase = true) ||
            title.contains("new message", ignoreCase = true) ||
            title.isDigitsOnly()
        ) {
            return ""
        }

        // Loại bỏ phần đếm số tin nhắn ví dụ "Nguyễn Văn A (3)" -> "Nguyễn Văn A"
        return title.replace(Regex("\\(\\d+\\)$"), "").trim()
    }

    /** Tạo chuỗi text để đọc dựa trên chế độ */
    private fun buildSpeechText(sbn: StatusBarNotification, config: NotificationReadConfigEntity): String {
        val appName = getAppLabel(sbn.packageName)
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val content = extractContent(extras)
        val sender = extractSenderName(sbn)

        return when (config.readMode) {
            NotificationReadMode.APP_NAME_ONLY ->
                appName

            NotificationReadMode.FULL_CONTENT -> {
                val parts = mutableListOf(appName)
                if (title.isNotBlank() && !title.equals(appName, ignoreCase = true) &&
                    !title.isDigitsOnly()
                ) parts.add(title)
                if (content.isNotBlank()) parts.add(content)
                parts.joinToString(". ")
            }

            NotificationReadMode.SMART_CHAT -> {
                if (sender.isNotBlank()) {
                    val parts = mutableListOf("$sender trên $appName")
                    if (content.isNotBlank()) parts.add(content)
                    parts.joinToString(". ")
                } else {
                    val parts = mutableListOf(appName)
                    if (title.isNotBlank() && !title.equals(appName, ignoreCase = true) &&
                        !title.isDigitsOnly()
                    ) parts.add(title)
                    if (content.isNotBlank()) parts.add(content)
                    parts.joinToString(". ")
                }
            }

            NotificationReadMode.SENDER_ONLY -> {
                if (sender.isNotBlank()) {
                    sender
                } else {
                    appName
                }
            }
        }
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun releaseAudioFocus(utteranceId: String?) {
        if (utteranceId == null) return
        val entry = focusByUtterance.remove(utteranceId) ?: return
        val (focusRequest, hasFocus) = entry
        if (hasFocus) {
            try {
                audioManager.abandonAudioFocusRequest(focusRequest)
            } catch (_: Exception) {}
        }
    }

    /** Phát TTS với Audio Focus Ducking và âm lượng tùy chỉnh */
    private fun speakWithAudioFocus(text: String) {
        tts?.setSpeechRate(repo.getTtsSpeechRate())
        tts?.setPitch(repo.getTtsPitch())

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val duckingEnabled = repo.isNotifReadDuckingEnabled()

        val focusRequest = AudioFocusRequest.Builder(
            if (duckingEnabled) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
            .setAudioAttributes(audioAttributes)
            .build()

        // Luôn xin Audio Focus (kể cả khi tắt màn hình) để đảm bảo TTS được phát trên mọi ROM,
        // ví dụ Vivo/Xiaomi thường chặn âm thanh nền nếu không có focus
        val focusResult = audioManager.requestAudioFocus(focusRequest)
        val hasFocus = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        val utteranceId = "notif_${System.currentTimeMillis()}"

        // Thiết lập âm lượng TTS từ preference (0.0f đến 1.0f)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, repo.getTtsVolume())
            putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, repo.getTtsPan())
        }

        focusByUtterance[utteranceId] = focusRequest to hasFocus

        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
    }
}
