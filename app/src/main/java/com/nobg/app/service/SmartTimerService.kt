package com.nobg.app.service

import android.app.*
import android.content.Context
import android.content.Intent
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
import com.nobg.app.MainActivity
import com.nobg.app.R
import com.nobg.app.data.NobgRepository
import com.nobg.app.data.SmartTimerConfig
import com.nobg.app.data.SmartTimerMode
import com.nobg.app.shell.PrivilegedShell
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
        const val ACTION_TOGGLE_QUICK_DEFAULT = "com.nobg.app.action.SMART_TIMER_TOGGLE_DEFAULT"
        const val ACTION_TOGGLE_QUICK_15M = "com.nobg.app.action.SMART_TIMER_TOGGLE_15M"

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
            ACTION_TOGGLE_QUICK_DEFAULT -> {
                if (currentConfig.isRunning) {
                    stopSmartTimer()
                    stopSelf()
                    return START_NOT_STICKY
                } else {
                    val quickCfg = repo.getSmartTimerConfig().copy(
                        isRunning = true,
                        mode = SmartTimerMode.CLOCK_TIME,
                        intervalMinutes = 2,
                        durationMinutes = 60,
                        startTimeMillis = System.currentTimeMillis()
                    )
                    startSmartTimer(quickCfg)
                }
            }
            ACTION_TOGGLE_QUICK_15M -> {
                if (currentConfig.isRunning) {
                    stopSmartTimer()
                    stopSelf()
                    return START_NOT_STICKY
                } else {
                    val quickCfg = repo.getSmartTimerConfig().copy(
                        isRunning = true,
                        intervalMinutes = 1,
                        durationMinutes = 15,
                        autoShutdown = true,
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
        currentConfig = config.copy(
            isRunning = true,
            startTimeMillis = if (config.startTimeMillis > 0) config.startTimeMillis else System.currentTimeMillis()
        )
        startTimestamp = currentConfig.startTimeMillis
        repo.saveSmartTimerConfig(currentConfig)

        wakeLock?.acquire(12 * 60 * 60 * 1000L) // Safe max 12 hours timeout

        startForeground(NOTIF_ID, buildNotification("Đếm giờ thông minh đang hoạt động..."))
        SmartTimerWidgetProvider.updateAllWidgets(applicationContext)

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            runTimerLoop()
        }
    }

    private suspend fun runTimerLoop() = coroutineScope {
        var lastAnnouncedMinute = -1

        while (isActive) {
            val now = System.currentTimeMillis()
            val elapsedMs = now - startTimestamp
            val elapsedMinutes = (elapsedMs / (60 * 1000L)).toInt()
            val totalDurationMinutes = currentConfig.durationMinutes

            // Check if duration expired
            if (totalDurationMinutes > 0 && elapsedMinutes >= totalDurationMinutes) {
                // Speak final announcement
                speakAnnouncement("Đã hết thời gian $totalDurationMinutes phút.")

                // Delay to finish speech
                delay(4000L)

                if (currentConfig.autoShutdown) {
                    // Trigger shutdown via Shizuku if available
                    if (PrivilegedShell.isReady()) {
                        PrivilegedShell.exec("reboot -p")
                        PrivilegedShell.exec("svc power shutdown")
                    }
                }
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

            // Update notification and widget every 5 seconds
            val notifText = buildNotificationText(elapsedMinutes, totalDurationMinutes)
            updateNotification(notifText)
            SmartTimerWidgetProvider.updateAllWidgets(applicationContext)

            delay(2000L)
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

    private fun buildNotificationText(elapsedMins: Int, totalDurationMins: Int): String {
        return if (totalDurationMins > 0) {
            val remainingMins = (totalDurationMins - elapsedMins).coerceAtLeast(0)
            "Đã đếm $elapsedMins phút (Còn $remainingMins phút)"
        } else {
            "Đã đếm $elapsedMins phút"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Đếm giờ thông minh",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo đếm giờ ngầm định kỳ"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏱️ Đếm giờ thông minh")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_nobg)
            .setContentIntent(pIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dừng", pStopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun stopSmartTimer() {
        timerJob?.cancel()
        timerJob = null
        currentConfig = currentConfig.copy(isRunning = false)
        repo.saveSmartTimerConfig(currentConfig)

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        SmartTimerWidgetProvider.updateAllWidgets(applicationContext)
    }

    override fun onDestroy() {
        stopSmartTimer()
        tts?.shutdown()
        tts = null
        isTtsReady = false
        isServiceRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
