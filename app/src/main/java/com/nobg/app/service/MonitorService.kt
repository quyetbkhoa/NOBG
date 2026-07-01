package com.nobg.app.service

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
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

    companion object {
        const val CHANNEL_ID = "nobg_monitor"
        const val NOTIF_ID = 1001
        const val POLL_INTERVAL_MS = 1500L
        const val RECONCILE_EVERY_TICKS = (120_000L / POLL_INTERVAL_MS).toInt() // ~2 minutes
    }

    override fun onCreate() {
        super.onCreate()
        repo = NobgRepository(applicationContext)
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        startForeground(NOTIF_ID, buildNotification())
        loop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
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
