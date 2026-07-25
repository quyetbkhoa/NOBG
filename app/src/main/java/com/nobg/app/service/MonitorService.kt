package com.nobg.app.service

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nobg.app.data.NobgMode
import com.nobg.app.data.NobgRepository
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.*

class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: NobgRepository
    private lateinit var usm: UsageStatsManager

    private var lastForegroundPkg: String? = null
    private var lastEventTime: Long = System.currentTimeMillis() - 2000
    private val pendingKills = mutableMapOf<String, Job>()
    private var reconcileTickCounter = 0

    // Charging prediction & session tracking state
    private var chargingStartLevel: Int = -1
    private var chargingStartTime: Long = -1L
    private var wasCharging: Boolean = false
    private var timeToFullNotifSent: Boolean = false
    private var fullBatterySoundPlayed: Boolean = false
    private val activeChargingPoints = mutableListOf<com.nobg.app.data.ChargingPoint>()

    private val NOTIF_CHARGE_ID = 1002
    private val NOTIF_FULL_BATTERY_ID = 1003
    private val CHANNEL_CHARGE_ID = "nobg_charge"
    private val CHANNEL_FULL_BATTERY_ID = "nobg_full_battery"

    companion object {
        const val CHANNEL_ID = "nobg_monitor"
        const val NOTIF_ID = 1001
        const val POLL_INTERVAL_MS = 60000L
        const val RECONCILE_EVERY_TICKS = (1800_000L / POLL_INTERVAL_MS).toInt() // ~30 minutes
    }

    override fun onCreate() {
        super.onCreate()
        repo = NobgRepository(applicationContext)
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(batteryReceiver, filter)

        startForeground(NOTIF_ID, buildNotification())
        loop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        scope.cancel()
        super.onDestroy()
    }

    private var lastBatteryLogTime: Long = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> {
                    logBatteryState(context, force = true)
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenInteractive = true
                    logBatteryState(context, force = true)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    logBatteryState(context, force = true)
                }
            }
        }
    }

    private fun logBatteryState(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastBatteryLogTime < 15 * 60 * 1000L) return
        lastBatteryLogTime = now

        scope.launch {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale) else -1

            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val isScreenOn = displayManager.displays.any { it.state == android.view.Display.STATE_ON }

            if (batteryPct >= 0) {
                repo.insertBatteryLog(batteryPct, isCharging, isScreenOn)
                trackChargingSession(batteryPct, isCharging)
                checkChargingPrediction(context, batteryPct, isCharging)
                checkFullBatterySoundAlert(context, batteryPct, isCharging)
                updateOngoingNotification(batteryPct, isCharging)
            }
        }
    }

    private suspend fun trackChargingSession(batteryPct: Int, isCharging: Boolean) {
        val now = System.currentTimeMillis()
        if (isCharging) {
            if (!wasCharging || activeChargingPoints.isEmpty()) {
                activeChargingPoints.clear()
                activeChargingPoints.add(com.nobg.app.data.ChargingPoint(batteryPct, now))
            } else {
                val lastPt = activeChargingPoints.lastOrNull()
                if (lastPt == null || lastPt.batteryPct != batteryPct) {
                    activeChargingPoints.add(com.nobg.app.data.ChargingPoint(batteryPct, now))
                }
            }
        } else {
            if (activeChargingPoints.size >= 2) {
                val startPt = activeChargingPoints.first()
                val endPt = activeChargingPoints.last()
                val durationSec = (endPt.timestampMs - startPt.timestampMs) / 1000L

                if (endPt.batteryPct > startPt.batteryPct && durationSec >= 60L) {
                    val session = com.nobg.app.data.ChargingSessionEntity(
                        startTimeMs = startPt.timestampMs,
                        endTimeMs = endPt.timestampMs,
                        startLevel = startPt.batteryPct,
                        endLevel = endPt.batteryPct,
                        totalDurationSeconds = durationSec,
                        isCompletedToFull = endPt.batteryPct >= 99,
                        pointsJson = com.nobg.app.data.ChargingPredictor.serializePointsJson(activeChargingPoints)
                    )
                    repo.insertChargingSession(session)
                }
            }
            activeChargingPoints.clear()
        }
    }

    private fun checkFullBatterySoundAlert(context: Context, batteryPct: Int, isCharging: Boolean) {
        if (!isCharging) {
            fullBatterySoundPlayed = false
            return
        }

        if (batteryPct >= 100 && !fullBatterySoundPlayed && repo.isFullBatterySoundEnabled()) {
            fullBatterySoundPlayed = true
            sendFullBatteryNotification(context)
        }
    }

    private fun sendFullBatteryNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_FULL_BATTERY_ID,
                "Cảnh báo pin đầy 100%",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo phát âm thanh khi pin sạc đầy 100%"
                setSound(soundUri, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, com.nobg.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_FULL_BATTERY_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("🔋 NOBG — Pin đã sạc đầy 100%!")
            .setContentText("Pin đã đạt 100%. Vui lòng rút sạc để bảo vệ pin thiết bị.")
            .setSound(soundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_FULL_BATTERY_ID, notif)

        try {
            val ringtone = android.media.RingtoneManager.getRingtone(context, soundUri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun checkChargingPrediction(context: Context, currentLevel: Int, isCharging: Boolean) {
        if (isCharging && !wasCharging) {
            chargingStartLevel = currentLevel
            chargingStartTime = System.currentTimeMillis()
            timeToFullNotifSent = false
        }
        wasCharging = isCharging

        if (!isCharging) {
            chargingStartLevel = -1
            chargingStartTime = -1L
            return
        }

        val elapsedMs = System.currentTimeMillis() - chargingStartTime
        val pctGained = currentLevel - chargingStartLevel
        if (chargingStartLevel < 0 || elapsedMs < 120_000 || pctGained < 3 || timeToFullNotifSent) return

        val sessions = repo.getAllChargingSessions()
        val prediction = com.nobg.app.data.ChargingPredictor.calculateNonLinearPrediction(currentLevel, sessions)
        val minutesToFull = prediction.remainingMinutes

        if (minutesToFull < 2) return

        sendTimeToFullNotification(context, currentLevel, minutesToFull)
        timeToFullNotifSent = true
    }

    private fun sendTimeToFullNotification(context: Context, currentLevel: Int, minutesToFull: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_CHARGE_ID,
                "Dự đoán sạc pin",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Thông báo thời gian sạc đầy pin" }
            nm.createNotificationChannel(channel)
        }

        val h = minutesToFull / 60
        val m = minutesToFull % 60
        val timeStr = if (h > 0) "$h giờ $m phút" else "$m phút"

        val notif = NotificationCompat.Builder(context, CHANNEL_CHARGE_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("⚡ NOBG — Dự đoán sạc đầy pin")
            .setContentText("Pin ${currentLevel}% → Dự kiến đầy sau ~$timeStr (Thuật toán phi tuyến)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_CHARGE_ID, notif)
    }

    private fun updateOngoingNotification(batteryPct: Int, isCharging: Boolean) {
        scope.launch {
            val enabledApps = repo.getEnabledApps().size
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification(enabledApps, batteryPct, isCharging))
        }
    }

    private fun buildNotification(enabledApps: Int = 0, batteryPct: Int = -1, isCharging: Boolean = false): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "NOBG Giám sát hệ thống",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, com.nobg.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openStatsIntent = Intent(this, com.nobg.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "battery_stats")
        }
        val openStatsPendingIntent = PendingIntent.getActivity(
            this, 1, openStatsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val chargingStateText = if (isCharging) "⚡ Đang sạc" else "🔋 Đang dùng pin"
        val batteryText = if (batteryPct >= 0) "$batteryPct% ($chargingStateText)" else "Đang theo dõi"
        val appsText = if (enabledApps > 0) "Đang tối ưu $enabledApps ứng dụng" else "Đang bảo vệ thiết bị"

        val bigText = """
            🛡️ Trạng thái: Đang bảo vệ & dọn app ngầm (Shizuku)
            📊 Ứng dụng đã tối ưu: $enabledApps app
            🔋 Trạng thái pin: $batteryText
        """.trimIndent()

        val notifTitle = "🛡️ NOBG — Đang bảo vệ hệ thống"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(notifTitle)
            .setContentText(appsText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_preferences, "⚙️ Quản lý App", openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_sort_by_size, "📊 Thống kê Pin", openStatsPendingIntent)
            .build()
    }

    private var isScreenInteractive: Boolean = true

    private fun loop() {
        scope.launch {
            // make sure ShizukuManager service is bound in this process too
            ShizukuManager.bindUserService()
            while (isActive) {
                try {
                    if (isScreenInteractive) {
                        pollForegroundApp()
                        reconcileTickCounter++
                        if (reconcileTickCounter >= RECONCILE_EVERY_TICKS) {
                            reconcileTickCounter = 0
                            reconcileAll()
                        }
                        delay(POLL_INTERVAL_MS)
                    } else {
                        // Screen is OFF: sleep for 30 seconds to allow CPU Deep Sleep (Doze Mode)
                        delay(30_000L)
                    }
                } catch (_: Exception) {
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    private suspend fun pollForegroundApp() {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastEventTime, now)
        var newForeground: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                newForeground = event.packageName
            }
        }
        lastEventTime = now

        if (newForeground != null && newForeground != lastForegroundPkg) {
            val leftPkg = lastForegroundPkg
            val enteredPkg = newForeground
            lastForegroundPkg = newForeground

            if (leftPkg != null) onAppLeftForeground(leftPkg)
            onAppEnteredForeground(enteredPkg)
        }
    }

    private suspend fun onAppLeftForeground(pkg: String) {
        if (pkg == packageName) return // ignore NOBG itself

        // 1. Check if app is on Freezer Shelf: auto-refreeze on exit
        val shelfApps = repo.getFrozenShelfApps()
        if (shelfApps.any { it.packageName == pkg }) {
            ShizukuManager.forceStop(pkg)
            ShizukuManager.disablePackage(pkg)
            repo.recordBlockedAction(pkg)
            com.nobg.app.widget.FrozenAppsWidgetProvider.updateAllWidgets(this@MonitorService)
            return
        }

        val cfg = repo.getConfig(pkg) ?: return
        if (!cfg.enabled) return

        when (cfg.mode) {
            NobgMode.STANDARD -> {
                // Background restrictions are already persistently applied when toggled on.
            }
            NobgMode.AGGRESSIVE -> {
                pendingKills[pkg]?.cancel()
                pendingKills[pkg] = scope.launch {
                    delay(cfg.delaySeconds * 1000L)
                    ShizukuManager.forceStop(pkg)
                    repo.recordBlockedAction(pkg)
                }
            }
            NobgMode.DISABLE_ENABLE -> {
                ShizukuManager.forceStop(pkg)
                ShizukuManager.disablePackage(pkg)
                repo.recordBlockedAction(pkg)
                com.nobg.app.widget.FrozenAppsWidgetProvider.updateAllWidgets(this@MonitorService)
            }
        }
    }

    private fun onAppEnteredForeground(pkg: String) {
        // If user returned to an app with a pending delayed kill, cancel it.
        pendingKills.remove(pkg)?.cancel()
    }

    /** Safety-net sweep every ~2 minutes: re-enforce state for all enabled apps
     *  that are not currently the foreground app. */
    private suspend fun reconcileAll() {
        val enabledApps = repo.getEnabledApps()
        for (cfg in enabledApps) {
            if (cfg.packageName == lastForegroundPkg) continue
            when (cfg.mode) {
                NobgMode.AGGRESSIVE -> {
                    // if somehow still running, force-stop again
                    ShizukuManager.forceStop(cfg.packageName)
                }
                NobgMode.DISABLE_ENABLE -> {
                    val disabled = ShizukuManager.isPackageDisabled(cfg.packageName)
                    if (!disabled) {
                        ShizukuManager.forceStop(cfg.packageName)
                        ShizukuManager.disablePackage(cfg.packageName)
                    }
                }
                NobgMode.STANDARD -> { /* appops persistent, nothing to re-apply */ }
            }
        }
    }
}
