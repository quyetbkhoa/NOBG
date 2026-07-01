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

    // package -> pending kill job (Aggressive delay)
    private val pendingKills = mutableMapOf<String, Job>()

    private var reconcileTickCounter = 0

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF -> {
                    logBatteryState(context)
                }
            }
        }
    }

    private fun logBatteryState(context: Context) {
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
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "nobg_monitor"
        const val NOTIF_ID = 1001
        const val POLL_INTERVAL_MS = 10000L
        const val RECONCILE_EVERY_TICKS = (120_000L / POLL_INTERVAL_MS).toInt() // ~2 minutes
    }

    override fun onCreate() {
        super.onCreate()
        repo = NobgRepository(applicationContext)
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
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

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "NOBG Giam sat",
                NotificationManager.IMPORTANCE_MIN
            )
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NOBG dang giam sat")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun loop() {
        scope.launch {
            // make sure ShizukuManager service is bound in this process too
            ShizukuManager.bindUserService()
            while (isActive) {
                try {
                    pollForegroundApp()
                    reconcileTickCounter++
                    if (reconcileTickCounter >= RECONCILE_EVERY_TICKS) {
                        reconcileTickCounter = 0
                        reconcileAll()
                    }
                } catch (_: Exception) {
                }
                delay(POLL_INTERVAL_MS)
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
        val cfg = repo.getConfig(pkg) ?: return
        if (!cfg.enabled) return

        when (cfg.mode) {
            NobgMode.STANDARD -> {
                // Background restrictions are already persistently applied when toggled on.
                // Nothing to do on leave - app stays alive in recents, just can't do anything.
            }
            NobgMode.AGGRESSIVE -> {
                // Schedule delayed kill; cancel if user returns before it fires.
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
