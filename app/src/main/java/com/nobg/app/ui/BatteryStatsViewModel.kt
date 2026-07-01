package com.nobg.app.ui

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobg.app.data.BatteryLogEntity
import com.nobg.app.data.NobgRepository
import com.nobg.app.shizuku.BatteryDumpsysParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

// ---- App Usage Tab data ----
data class UsageItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long,
    val batteryMah: Double = 0.0
)

enum class StatsInterval { DAILY, WEEKLY }

// ---- Global Battery Overview Tab data ----
data class OverviewStats(
    val sinceMs: Long = 0L,         // timestamp of reset anchor
    val totalDays: Double = 0.0,    // days since reset
    val avgDischargePctPerDay: Double = 0.0,
    val avgChargePctPerDay: Double = 0.0,
    val drainRateOnscreen: Double = 0.0,   // %/h onscreen
    val drainRateOffscreen: Double = 0.0,  // %/h offscreen
    val chargeRate: Double = 0.0,          // %/h while charging
    val timeToFullMinutes: Int = -1,       // -1 = not charging / unknown
    val currentChargeLevel: Int = -1,
    val hasData: Boolean = false
)

// Charging curve: maps pct (0..100) -> average seconds per % to charge
data class ChargingCurvePoint(val batteryPct: Int, val secondsPerPct: Float)

class BatteryStatsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = NobgRepository(app)
    private val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm = app.packageManager

    private val _usageStats = MutableStateFlow<List<UsageItem>>(emptyList())
    val usageStats: StateFlow<List<UsageItem>> = _usageStats

    private val _currentInterval = MutableStateFlow(StatsInterval.DAILY)
    val currentInterval: StateFlow<StatsInterval> = _currentInterval

    private val _overview = MutableStateFlow(OverviewStats())
    val overview: StateFlow<OverviewStats> = _overview

    private val _chargingCurve = MutableStateFlow<List<ChargingCurvePoint>>(emptyList())
    val chargingCurve: StateFlow<List<ChargingCurvePoint>> = _chargingCurve

    init {
        loadUsageStats(StatsInterval.DAILY)
        loadOverview()
    }

    // ---- App usage tab ----

    fun loadUsageStats(interval: StatsInterval) {
        _currentInterval.value = interval
        viewModelScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            val endTime = cal.timeInMillis
            when (interval) {
                StatsInterval.DAILY -> cal.add(Calendar.DAY_OF_YEAR, -1)
                StatsInterval.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, -7)
            }
            // Use reset anchor as the lower bound if it's more recent than the interval
            val intervalStart = cal.timeInMillis
            val resetTime = repo.getUsageResetTime()
            val startTime = maxOf(intervalStart, resetTime)

            val statsMap = usm.queryAndAggregateUsageStats(startTime, endTime)
            val batteryUsageMap = BatteryDumpsysParser.getAppBatteryUsage()

            val items = statsMap.values
                .filter { it.totalTimeInForeground > 0 }
                .map { stat ->
                    var label = stat.packageName
                    var icon: Drawable? = null
                    var uid = -1
                    try {
                        val ai = pm.getApplicationInfo(stat.packageName, 0)
                        label = pm.getApplicationLabel(ai).toString()
                        icon = pm.getApplicationIcon(ai)
                        uid = ai.uid
                    } catch (e: PackageManager.NameNotFoundException) { /* keep pkg name */ }
                    val usedMah = batteryUsageMap[uid.toString()] ?: 0.0
                    UsageItem(
                        packageName = stat.packageName,
                        label = label,
                        icon = icon,
                        totalTimeInForeground = stat.totalTimeInForeground,
                        lastTimeUsed = stat.lastTimeUsed,
                        batteryMah = usedMah
                    )
                }
                .sortedByDescending { it.batteryMah.takeIf { it > 0 } ?: it.totalTimeInForeground.toDouble() }

            _usageStats.value = items
        }
    }

    // ---- Overview tab ----

    fun loadOverview() {
        viewModelScope.launch(Dispatchers.IO) {
            val resetTime = repo.getBatteryResetTime()
            val logs = repo.getBatteryLogsSince(resetTime)

            if (logs.size < 2) {
                _overview.value = OverviewStats(sinceMs = resetTime)
                _chargingCurve.value = emptyList()
                return@launch
            }

            // Calculate stats
            var screenOnDischargeMs = 0L
            var screenOnDischargePct = 0
            var screenOffDischargeMs = 0L
            var screenOffDischargePct = 0
            var chargingMs = 0L
            var chargePct = 0
            var dischargePct = 0

            var prevLog: BatteryLogEntity? = null

            for (log in logs) {
                if (prevLog != null) {
                    val timeDiff = log.timestamp - prevLog.timestamp
                    val validDiff = if (timeDiff in 1..43200000L) timeDiff else 0L
                    val levelDiff = log.batteryLevel - prevLog.batteryLevel

                    if (!prevLog.isCharging) {
                        if (prevLog.isScreenOn) {
                            screenOnDischargeMs += validDiff
                            if (levelDiff < 0) screenOnDischargePct += (-levelDiff)
                        } else {
                            screenOffDischargeMs += validDiff
                            if (levelDiff < 0) screenOffDischargePct += (-levelDiff)
                        }
                    } else {
                        chargingMs += validDiff
                        if (levelDiff > 0) chargePct += levelDiff
                    }

                    if (levelDiff > 0) chargePct += 0 // already counted
                    if (levelDiff < 0 && !prevLog.isCharging) dischargePct += (-levelDiff)
                }
                prevLog = log
            }

            val firstTs = logs.first().timestamp
            val lastTs = logs.last().timestamp
            val totalDays = (lastTs - firstTs) / 86400000.0

            val drainOnscreen = if (screenOnDischargeMs > 0)
                screenOnDischargePct / (screenOnDischargeMs / 3600000.0) else 0.0
            val drainOffscreen = if (screenOffDischargeMs > 0)
                screenOffDischargePct / (screenOffDischargeMs / 3600000.0) else 0.0
            val chargeRatePerHour = if (chargingMs > 0)
                chargePct / (chargingMs / 3600000.0) else 0.0

            val avgDischargePctPerDay = if (totalDays > 0) dischargePct / totalDays else 0.0
            val avgChargePctPerDay = if (totalDays > 0) chargePct / totalDays else 0.0

            // Time to full: check if currently charging
            val lastLog = logs.last()
            var timeToFullMinutes = -1
            var currentLevel = -1
            if (lastLog.isCharging) {
                currentLevel = lastLog.batteryLevel
                val pctNeeded = 100 - currentLevel
                if (chargeRatePerHour > 0 && pctNeeded > 0) {
                    timeToFullMinutes = ((pctNeeded / chargeRatePerHour) * 60).toInt()
                }
            }

            _overview.value = OverviewStats(
                sinceMs = if (resetTime > 0) resetTime else firstTs,
                totalDays = totalDays,
                avgDischargePctPerDay = avgDischargePctPerDay,
                avgChargePctPerDay = avgChargePctPerDay,
                drainRateOnscreen = drainOnscreen,
                drainRateOffscreen = drainOffscreen,
                chargeRate = chargeRatePerHour,
                timeToFullMinutes = timeToFullMinutes,
                currentChargeLevel = currentLevel,
                hasData = true
            )

            // Build charging curve: avg seconds per % for each % bucket
            buildChargingCurve()
        }
    }

    private suspend fun buildChargingCurve() {
        val allLogs = repo.getBatteryLogsSince(0)
        // Map pct -> list of (seconds it took to charge that %)
        val buckets = mutableMapOf<Int, MutableList<Long>>()

        var prevLog: BatteryLogEntity? = null
        for (log in allLogs) {
            if (prevLog != null && prevLog.isCharging && log.isCharging) {
                val timeDiff = log.timestamp - prevLog.timestamp
                if (timeDiff in 1..300000L) { // cap at 5min per % to avoid gaps
                    val pct = prevLog.batteryLevel
                    val levelDiff = log.batteryLevel - prevLog.batteryLevel
                    if (levelDiff == 1) {
                        buckets.getOrPut(pct) { mutableListOf() }.add(timeDiff / 1000)
                    }
                }
            }
            prevLog = log
        }

        val curve = buckets
            .filter { it.value.isNotEmpty() }
            .map { (pct, times) ->
                ChargingCurvePoint(pct, times.average().toFloat())
            }
            .sortedBy { it.batteryPct }

        _chargingCurve.value = curve
    }

    fun resetData() {
        viewModelScope.launch {
            repo.clearBatteryLogs()
            loadOverview()
        }
    }
}
