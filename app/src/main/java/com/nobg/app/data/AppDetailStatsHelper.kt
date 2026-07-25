package com.nobg.app.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Build
import com.nobg.app.shizuku.BatteryDumpsysParser

data class TimeInterval(
    val startMs: Long,
    val endMs: Long
)

data class AppDetailStats(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val uid: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val foregroundIntervals: List<TimeInterval>,
    val fgServiceIntervals: List<TimeInterval>,
    val fgSessionCount: Int,
    val totalFgTimeMs: Long,
    val longestFgSessionMs: Long,
    val fgServiceRunCount: Int,
    val totalFgServiceTimeMs: Long,
    val userInteractionCount: Int,
    val openCount: Int,
    val batteryMah: Double,
    val batteryPct: Double,
    val pctPerHour: Double,
    val standbyBucket: String,
    val wifiBytes: Long,
    val mobileBytes: Long
)

object AppDetailStatsHelper {

    fun getAppDetailStats(
        context: Context,
        packageName: String,
        startTimeMs: Long,
        endTimeMs: Long,
        totalCalculatedMah: Double = 1.0
    ): AppDetailStats {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        var label = packageName
        var icon: Drawable? = null
        var uid = -1
        var category = ApplicationInfo.CATEGORY_UNDEFINED

        try {
            val ai = pm.getApplicationInfo(packageName, 0)
            label = pm.getApplicationLabel(ai).toString()
            icon = pm.getApplicationIcon(ai)
            uid = ai.uid
            category = ai.category
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        // Query UsageEvents for timeline intervals and counters
        val events = usm.queryEvents(startTimeMs, endTimeMs)
        val event = UsageEvents.Event()

        val fgIntervals = mutableListOf<TimeInterval>()
        val fgServiceIntervals = mutableListOf<TimeInterval>()

        var fgStartMs: Long? = null
        var fgServiceStartMs: Long? = null

        var userInteractions = 0
        var openCount = 0

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue

            val eventTime = event.timeStamp.coerceIn(startTimeMs, endTimeMs)

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    openCount++
                    if (fgStartMs == null) {
                        fgStartMs = eventTime
                    }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (fgStartMs != null) {
                        if (eventTime > fgStartMs) {
                            fgIntervals.add(TimeInterval(fgStartMs, eventTime))
                        }
                        fgStartMs = null
                    }
                }
                19 -> { // FOREGROUND_SERVICE_START (Android Q / API 29+)
                    if (fgServiceStartMs == null) {
                        fgServiceStartMs = eventTime
                    }
                }
                20 -> { // FOREGROUND_SERVICE_STOP
                    if (fgServiceStartMs != null) {
                        if (eventTime > fgServiceStartMs) {
                            fgServiceIntervals.add(TimeInterval(fgServiceStartMs, eventTime))
                        }
                        fgServiceStartMs = null
                    }
                }
                UsageEvents.Event.USER_INTERACTION -> {
                    userInteractions++
                }
            }
        }

        // Close pending intervals if app is currently in foreground or service is running
        if (fgStartMs != null && endTimeMs > fgStartMs) {
            fgIntervals.add(TimeInterval(fgStartMs, endTimeMs))
        }
        if (fgServiceStartMs != null && endTimeMs > fgServiceStartMs) {
            fgServiceIntervals.add(TimeInterval(fgServiceStartMs, endTimeMs))
        }

        val totalFgTimeMs = fgIntervals.sumOf { it.endMs - it.startMs }
        val longestFgSessionMs = fgIntervals.maxOfOrNull { it.endMs - it.startMs } ?: 0L
        val totalFgServiceTimeMs = fgServiceIntervals.sumOf { it.endMs - it.startMs }

        // Fallback calculations if queryEvents yielded 0 sessions but foreground stats exist
        val statsMap = usm.queryAndAggregateUsageStats(startTimeMs, endTimeMs)
        val usageStat = statsMap[packageName]
        val screenTimeMs = if (totalFgTimeMs > 0) totalFgTimeMs else (usageStat?.totalTimeInForeground ?: 0L)
        val finalOpenCount = if (openCount > 0) openCount else (if (screenTimeMs > 0) 1 else 0)

        // Battery calculation
        val batteryDetailsMap = kotlinx.coroutines.runBlocking { BatteryDumpsysParser.getAppBatteryDetails() }
        val detail = batteryDetailsMap[uid.toString()] ?: batteryDetailsMap[packageName]
        var mah = detail?.mah ?: 0.0

        if (mah <= 0.0 && screenTimeMs > 0) {
            val hours = screenTimeMs / 3600000.0
            val weight = when (category) {
                ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_AUDIO -> 1.8
                ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> 1.4
                ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_MAPS -> 1.3
                else -> 1.0
            }
            mah = hours * 180.0 * weight
        }

        val batteryPct = if (totalCalculatedMah > 0) (mah / totalCalculatedMah) * 100.0 else 0.0

        val totalDurationHours = (endTimeMs - startTimeMs).toDouble() / 3600000.0
        val pctPerHour = if (totalDurationHours > 0) batteryPct / totalDurationHours else 0.0

        // Standby bucket
        val standbyBucketLabel = getStandbyBucketLabel(context, packageName)

        // Network usage
        val (wifiBytes, mobileBytes) = getNetworkStatsForUid(context, uid, startTimeMs, endTimeMs)

        return AppDetailStats(
            packageName = packageName,
            label = label,
            icon = icon,
            uid = uid,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            foregroundIntervals = fgIntervals,
            fgServiceIntervals = fgServiceIntervals,
            fgSessionCount = fgIntervals.size,
            totalFgTimeMs = screenTimeMs,
            longestFgSessionMs = longestFgSessionMs,
            fgServiceRunCount = fgServiceIntervals.size,
            totalFgServiceTimeMs = totalFgServiceTimeMs,
            userInteractionCount = userInteractions,
            openCount = finalOpenCount,
            batteryMah = mah,
            batteryPct = batteryPct,
            pctPerHour = pctPerHour,
            standbyBucket = standbyBucketLabel,
            wifiBytes = wifiBytes,
            mobileBytes = mobileBytes
        )
    }

    private fun getStandbyBucketLabel(context: Context, packageName: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            try {
                return when (usm.getAppStandbyBucket()) {
                    5 -> "Ngoại lệ"
                    UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "Hoạt động"
                    UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "Tập làm việc"
                    UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "Thường xuyên"
                    UsageStatsManager.STANDBY_BUCKET_RARE -> "Hiếm khi"
                    UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "Hạn chế"
                    else -> "--"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return "--"
    }

    private fun getNetworkStatsForUid(context: Context, uid: Int, startTimeMs: Long, endTimeMs: Long): Pair<Long, Long> {
        if (uid < 0) return Pair(0L, 0L)
        var wifiBytes = 0L
        var mobileBytes = 0L

        try {
            val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

            // WiFi stats
            val wifiStats = nsm.queryDetailsForUid(
                ConnectivityManager.TYPE_WIFI,
                null,
                startTimeMs,
                endTimeMs,
                uid
            )
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                wifiBytes += bucket.rxBytes + bucket.txBytes
            }
            wifiStats.close()

            // Mobile stats
            val mobileStats = nsm.queryDetailsForUid(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTimeMs,
                endTimeMs,
                uid
            )
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                mobileBytes += bucket.rxBytes + bucket.txBytes
            }
            mobileStats.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(wifiBytes, mobileBytes)
    }
}
