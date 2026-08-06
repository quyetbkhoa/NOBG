package com.nobg.app.service

import android.app.Notification
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.text.isDigitsOnly
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
                    speakWithAudioFocus(text)
                    lastReadTimestamps[sbn.packageName] = System.currentTimeMillis()
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
            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return false
            val adapter = btManager.adapter ?: return false
            if (!adapter.isEnabled) return false

            val selectedDevices = repo.getSelectedBtDevices()
            if (selectedDevices.isEmpty()) return false
            val selectedAddresses = selectedDevices.map { it.address }.toSet()

            val connectedAddresses = mutableSetOf<String>()

            try {
                val a2dpDevices = btManager.getConnectedDevices(BluetoothProfile.A2DP)
                connectedAddresses.addAll(a2dpDevices.map { it.address })
            } catch (_: Exception) {}

            try {
                val headsetDevices = btManager.getConnectedDevices(BluetoothProfile.HEADSET)
                connectedAddresses.addAll(headsetDevices.map { it.address })
            } catch (_: Exception) {}

            return connectedAddresses.any { it in selectedAddresses }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth connection", e)
            return false
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

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (hasFocus) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
            }
            override fun onError(id: String?) {
                if (hasFocus) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
    }
}
