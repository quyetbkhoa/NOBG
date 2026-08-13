package com.nobg.app.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nobg.app.MainActivity
import com.nobg.app.R
import com.nobg.app.data.NobgRepository
import com.nobg.app.data.SmartTimerConfig
import com.nobg.app.data.SmartTimerMode
import com.nobg.app.widget.SmartTimerWidgetProvider
import kotlinx.coroutines.*
import java.util.*

class SmartTimerService : Service() {

    companion object {
        private const val TAG = "SmartTimerService"
        private const val NOTIF_ID = 2001
        private const val CHANNEL_ID = "nobg_smart_timer_channel"

        const val ACTION_START = "com.nobg.app.action.SMART_TIMER_START"
        const val ACTION_STOP = "com.nobg.app.action.SMART_TIMER_STOP"
        const val ACTION_TOGGLE_WIDGET_QUICK = "com.nobg.app.action.SMART_TIMER_TOGGLE_WIDGET_QUICK"

        @Volatile
        var isServiceRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: NobgRepository
    private lateinit var audioManager: AudioManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var timerJob: Job? = null
    private var currentConfig = SmartTimerConfig()
    private var startTimestamp = 0L

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        repo = NobgRepository(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NOBG:SmartTimerWakeLock").apply {
            setReferenceCounted(false)
        }

        createNotificationChannel()
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val viLocale = Locale("vi", "VN")
                val result = tts?.setLanguage(viLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                isTtsReady = true
                Log.d(TAG, "SmartTimer TTS initialized")
            } else {
                Log.e(TAG, "SmartTimer TTS init failed: $status")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopSmartTimer()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_WIDGET_QUICK -> {
                if (currentConfig.isRunning) {
                    stopSmartTimer()
                    stopSelf()
                    return START_NOT_STICKY
                } else {
                    val widgetConfig = repo.getSmartTimerQuickConfig()
                    val quickCfg = repo.getSmartTimerConfig().copy(
                        isRunning = true,
                        mode = widgetConfig.mode,
                        intervalMinutes = widgetConfig.intervalMinutes,
                        durationMinutes = widgetConfig.durationMinutes,
                        startTimeMillis = System.currentTimeMillis()
                    )
                    startSmartTimer(quickCfg)
                }
            }
            else -> {
                val cfg = repo.getSmartTimerConfig()
                startSmartTimer(cfg)
            }
        }

        return START_STICKY
    }

    private fun startSmartTimer(config: SmartTimerConfig) {
        if (!canPostNotifications()) {
            Log.w(TAG, "Timer start rejected because notification permission is missing")
            currentConfig = config.copy(isRunning = false, startTimeMillis = 0L)
            repo.saveSmartTimerConfig(currentConfig)
            isServiceRunning = false
            SmartTimerWidgetProvider.updateAllWidgets(applicationContext)
            stopSelf()
            return
        }

        currentConfig = config.copy(
            isRunning = true,
            startTimeMillis = if (config.startTimeMillis > 0) config.startTimeMillis else System.currentTimeMillis()
        )
        startTimestamp = currentConfig.startTimeMillis
        repo.saveSmartTimerConfig(currentConfig)

        wakeLock?.acquire(12 * 60 * 60 * 1000L) // Safe max 12 hours timeout

        val initialElapsedMs = (System.currentTimeMillis() - startTimestamp).coerceAtLeast(0L)
        startForeground(NOTIF_ID, buildNotification(elapsedMs = initialElapsedMs))
        SmartTimerWidgetProvider.updateAllWidgets(applicationContext)

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            runTimerLoop()
        }
    }

    private suspend fun runTimerLoop() = coroutineScope {
        var lastAnnouncedMinute = -1

        while (isActive) {
            try {
                val now = System.currentTimeMillis()
                val elapsedMs = now - startTimestamp
                val elapsedMinutes = (elapsedMs / (60 * 1000L)).toInt()
                val totalDurationMinutes = currentConfig.durationMinutes

                // Check if duration expired
                if (totalDurationMinutes > 0 && elapsedMinutes >= totalDurationMinutes) {
                    // Speak final announcement
                    try {
                        speakAnnouncement("Đã hết thời gian $totalDurationMinutes phút.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Final announcement failed", e)
                    }

                    // Delay to finish speech
                    delay(4000L)

                    stopSmartTimer()
                    stopSelf()
                    break
                }

                // Check if interval minute has been reached
                val interval = currentConfig.intervalMinutes.coerceAtLeast(1)
                if (elapsedMinutes > 0 && elapsedMinutes % interval == 0 && elapsedMinutes != lastAnnouncedMinute) {
                    lastAnnouncedMinute = elapsedMinutes
                    val speechText = buildSpeechText(elapsedMinutes)
                    speakAnnouncement(speechText)
                }

                // Update notification and widget every 2 seconds.
                updateNotification(elapsedMs)
                SmartTimerWidgetProvider.updateAllWidgets(applicationContext)

                delay(2000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Timer loop iteration failed, continuing", e)
                delay(2000L)
            }
        }
    }

    private fun buildSpeechText(elapsedMinutes: Int): String {
        return when (currentConfig.mode) {
            SmartTimerMode.ELAPSED_TIME -> {
                formatElapsedSpeech(elapsedMinutes)
            }
            SmartTimerMode.CLOCK_TIME -> {
                formatClockSpeech()
            }
        }
    }

    private fun formatElapsedSpeech(minutes: Int): String {
        return if (minutes < 60) {
            "$minutes phút"
        } else {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins == 0) "$hours giờ" else "$hours giờ $mins phút"
        }
    }

    private fun formatClockSpeech(): String {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        return if (minute == 0) {
            "$hour giờ"
        } else if (minute < 10) {
            "$hour giờ không $minute"
        } else {
            "$hour giờ $minute"
        }
    }

    private fun speakAnnouncement(text: String) {
        if (!isTtsReady || tts == null) return

        tts?.setSpeechRate(currentConfig.speechRate)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .build()

        if (currentConfig.audioDucking) {
            audioManager.requestAudioFocus(focusRequest)
        }

        val utteranceId = "smart_timer_${System.currentTimeMillis()}"

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, currentConfig.volume)
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (currentConfig.audioDucking) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
            }
            override fun onError(id: String?) {
                if (currentConfig.audioDucking) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun formatTimerTime(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0L)
        val hours = safeSeconds / 3600L
        val minutes = (safeSeconds % 3600L) / 60L
        val seconds = safeSeconds % 60L
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Đếm giờ thông minh",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị trạng thái và điều khiển Smart Timer đang chạy"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(elapsedMs: Long): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_screen", "SMART_TIMER")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SmartTimerService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsedSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val durationSeconds = currentConfig.durationMinutes.coerceAtLeast(0) * 60L
        val remainingSeconds = (durationSeconds - elapsedSeconds).coerceAtLeast(0L)
        val elapsedText = formatTimerTime(elapsedSeconds)
        val remainingText = if (durationSeconds > 0L) formatTimerTime(remainingSeconds) else null
        val modeText = when (currentConfig.mode) {
            SmartTimerMode.ELAPSED_TIME -> "Thời gian đã đếm"
            SmartTimerMode.CLOCK_TIME -> "Giờ hiện tại"
        }
        val title = remainingText?.let { "Timer · Còn $it" } ?: "Timer đang chạy · $elapsedText"
        val summary = "Đã chạy $elapsedText · Báo mỗi ${currentConfig.intervalMinutes} phút"
        val details = buildString {
            append("Đã chạy: $elapsedText")
            remainingText?.let { append("\nCòn lại: $it") }
            append("\nBáo sau mỗi: ${currentConfig.intervalMinutes} phút")
            append("\nKiểu đọc: $modeText")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setSubText("NOBG Smart Timer")
            .setSmallIcon(R.drawable.ic_launcher_nobg)
            .setContentIntent(pIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng", pStopIntent)
            .build()
    }

    private fun updateNotification(elapsedMs: Long) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(elapsedMs))
    }

    private fun stopSmartTimer() {
        timerJob?.cancel()
        timerJob = null
        currentConfig = currentConfig.copy(isRunning = false)
        repo.saveSmartTimerConfig(currentConfig)
        isServiceRunning = false

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        SmartTimerWidgetProvider.updateAllWidgets(applicationContext)
    }

    override fun onDestroy() {
        stopSmartTimer()
        tts?.shutdown()
        tts = null
        isTtsReady = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
