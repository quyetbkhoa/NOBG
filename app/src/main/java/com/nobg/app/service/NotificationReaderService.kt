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
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.nobg.app.data.NobgRepository
import com.nobg.app.data.NotificationReadMode
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Service lắng nghe thông báo hệ thống và tự động đọc bằng TTS.
 * Hỗ trợ lọc theo danh sách thiết bị Bluetooth được chọn.
 */
class NotificationReaderService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifReaderService"
        private const val DEBOUNCE_MS = 3000L // Cooldown 3 giây cho mỗi package
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
                // Try Vietnamese first, fallback to default
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

                val config = repo.getNotifReadConfig(sbn.packageName) ?: return@launch
                if (!config.isEnabled) return@launch

                val text = buildSpeechText(sbn, config.readMode)
                if (text.isNotBlank()) {
                    speakWithAudioFocus(text)
                    lastReadTimestamps[sbn.packageName] = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification from ${sbn.packageName}", e)
            }
        }
    }

    /**
     * Kiểm tra tất cả điều kiện trước khi đọc thông báo:
     * 1. Công tắc tổng bật
     * 2. TTS sẵn sàng
     * 3. Không bị DND
     * 4. Debounce (chống spam)
     * 5. Kiểm tra Bluetooth nếu bật lọc BT
     */
    private suspend fun shouldRead(sbn: StatusBarNotification): Boolean {
        // 1. Công tắc tổng
        if (!repo.isNotifReadGlobalEnabled()) return false

        // 2. TTS ready
        if (!isTtsReady) return false

        // 3. DND check
        if (isDndActive()) return false

        // 4. Debounce
        if (isDebounced(sbn.packageName)) return false

        // 5. Bluetooth filter
        if (repo.isNotifReadOnlySelectedBt() && !isSelectedBluetoothConnected()) return false

        return true
    }

    /** Kiểm tra chế độ Không làm phiền (DND) */
    private fun isDndActive(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    /** Debounce: chặn đọc lại cùng 1 package trong vòng DEBOUNCE_MS */
    private fun isDebounced(pkg: String): Boolean {
        val lastTime = lastReadTimestamps[pkg] ?: return false
        return (System.currentTimeMillis() - lastTime) < DEBOUNCE_MS
    }

    /**
     * Kiểm tra xem thiết bị Bluetooth đang kết nối có nằm trong
     * danh sách thiết bị được chọn hay không.
     * Kiểm tra cả profile A2DP (media audio) và HEADSET (call audio).
     */
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

            // Check A2DP profile (media audio)
            try {
                val a2dpDevices = btManager.getConnectedDevices(BluetoothProfile.A2DP)
                connectedAddresses.addAll(a2dpDevices.map { it.address })
            } catch (_: Exception) {}

            // Check HEADSET profile (call audio)
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

    /** Tạo chuỗi text để đọc dựa trên chế độ */
    private fun buildSpeechText(sbn: StatusBarNotification, mode: NotificationReadMode): String {
        val appName = getAppLabel(sbn.packageName)
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val content = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

        return when (mode) {
            NotificationReadMode.APP_NAME_ONLY ->
                "Thông báo từ $appName"
            NotificationReadMode.FULL_CONTENT -> {
                val parts = mutableListOf("Thông báo từ $appName")
                if (title.isNotBlank()) parts.add(title)
                if (content.isNotBlank()) parts.add(content)
                parts.joinToString(". ")
            }
        }
    }

    /** Lấy tên ứng dụng từ PackageManager */
    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    /** Phát TTS với Audio Focus Ducking (giảm volume nhạc nền tạm thời) */
    private fun speakWithAudioFocus(text: String) {
        // Refresh speech rate from prefs
        tts?.setSpeechRate(repo.getTtsSpeechRate())

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .build()

        audioManager.requestAudioFocus(focusRequest)

        val utteranceId = "notif_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
            override fun onError(id: String?) {
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }
}
