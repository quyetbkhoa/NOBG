package com.nobg.app.ui

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobg.app.data.AppDetailStats
import com.nobg.app.data.AppDetailStatsHelper
import com.nobg.app.data.BatteryLogEntity
import com.nobg.app.data.NobgRepository
import com.nobg.app.shizuku.BatteryDumpsysParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class UsageItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long,
    val batteryMah: Double = 0.0,
    val batteryPct: Double = 0.0,
    val totalCpuMs: Long = 0L,
    val wakeupCount: Int = 0,
    val totalWakelockMs: Long = 0L
)

enum class StatsInterval(val label: String) {
    DAILY("1 Ngày"),
    WEEKLY("1 Tuần"),
    SINCE_CHARGED("⚡ Sạc đầy gần nhất")
}

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

data class ChargingCurvePoint(val batteryPct: Int, val secondsPerPct: Float)

class BatteryStatsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = NobgRepository(app)
    private val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm = app.packageManager

    private val _usageStats = MutableStateFlow<List<UsageItem>>(emptyList())
    val usageStats: StateFlow<List<UsageItem>> = _usageStats

    private val _currentInterval = MutableStateFlow(StatsInterval.SINCE_CHARGED)
    val currentInterval: StateFlow<StatsInterval> = _currentInterval

    private val _anchorTimeMs = MutableStateFlow(0L)
    val anchorTimeMs: StateFlow<Long> = _anchorTimeMs

    private val _overview = MutableStateFlow(OverviewStats())
    val overview: StateFlow<OverviewStats> = _overview

    private val _chargingCurve = MutableStateFlow<List<ChargingCurvePoint>>(emptyList())
    val chargingCurve: StateFlow<List<ChargingCurvePoint>> = _chargingCurve

    private val _selectedAppDetail = MutableStateFlow<AppDetailStats?>(null)
    val selectedAppDetail: StateFlow<AppDetailStats?> = _selectedAppDetail

    private val _isLoadingDetail = MutableStateFlow(false)
    val isLoadingDetail: StateFlow<Boolean> = _isLoadingDetail

    private val _chargingSessions = MutableStateFlow<List<com.nobg.app.data.ChargingSessionEntity>>(emptyList())
    val chargingSessions: StateFlow<List<com.nobg.app.data.ChargingSessionEntity>> = _chargingSessions

    private val _isFullBatterySoundEnabled = MutableStateFlow(true)
    val isFullBatterySoundEnabled: StateFlow<Boolean> = _isFullBatterySoundEnabled

    private val _predictionResult = MutableStateFlow(com.nobg.app.data.ChargingPredictor.calculateNonLinearPrediction(0, emptyList()))
    val predictionResult: StateFlow<com.nobg.app.data.PredictionResult> = _predictionResult

    private var lastTotalCalculatedMah: Double = 1.0

    init {
        loadUsageStats(StatsInterval.SINCE_CHARGED)
        loadOverview()
        observeChargingSessions()
    }

    private fun observeChargingSessions() {
        _isFullBatterySoundEnabled.value = repo.isFullBatterySoundEnabled()
        viewModelScope.launch(Dispatchers.IO) {
            repo.observeChargingSessions().collect { list ->
                _chargingSessions.value = list
                val currentLevel = _overview.value.currentChargeLevel.let { if (it >= 0) it else 0 }
                _predictionResult.value = com.nobg.app.data.ChargingPredictor.calculateNonLinearPrediction(currentLevel, list)
            }
        }
    }

    fun setFullBatterySoundEnabled(enabled: Boolean) {
        repo.setFullBatterySoundEnabled(enabled)
        _isFullBatterySoundEnabled.value = enabled
    }

    fun clearAllChargingSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.clearAllChargingSessions()
        }
    }

    fun deleteChargingSession(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteChargingSession(id)
        }
    }

    fun selectAppDetail(packageName: String) {
        _isLoadingDetail.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val endTime = System.currentTimeMillis()
            val startTime = _anchorTimeMs.value.let { if (it > 0) it else (endTime - 86400000L) }
            val detail = AppDetailStatsHelper.getAppDetailStats(
                context = getApplication(),
                packageName = packageName,
                startTimeMs = startTime,
                endTimeMs = endTime,
                totalCalculatedMah = lastTotalCalculatedMah
            )
            _selectedAppDetail.value = detail
            _isLoadingDetail.value = false
        }
    }

    fun clearSelectedAppDetail() {
        _selectedAppDetail.value = null
    }

    // ---- App usage tab ----

    fun loadUsageStats(interval: StatsInterval) {
        _currentInterval.value = interval
        viewModelScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            val endTime = cal.timeInMillis

            val startTime: Long = when (interval) {
                StatsInterval.DAILY -> {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    cal.timeInMillis
                }
                StatsInterval.WEEKLY -> {
                    cal.add(Calendar.DAY_OF_YEAR, -7)
                    cal.timeInMillis
                }
                StatsInterval.SINCE_CHARGED -> {
                    val lastFullChargeTs = getLastFullChargeTime()
                    if (lastFullChargeTs > 0) {
                        lastFullChargeTs
                    } else {
                        // Fallback to start of today
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.timeInMillis
                    }
                }
            }

            _anchorTimeMs.value = startTime

            val statsMap = usm.queryAndAggregateUsageStats(startTime, endTime)
            val batteryDetailsMap = BatteryDumpsysParser.getAppBatteryDetails()

            var totalCalculatedMah = 0.0

            val tempItems = statsMap.values
                .filter { it.totalTimeInForeground > 0 }
                .map { stat ->
                    var label = stat.packageName
                    var icon: Drawable? = null
                    var uid = -1
                    var category = ApplicationInfo.CATEGORY_UNDEFINED

                    try {
                        val ai = pm.getApplicationInfo(stat.packageName, 0)
                        label = pm.getApplicationLabel(ai).toString()
                        icon = pm.getApplicationIcon(ai)
                        uid = ai.uid
                        category = ai.category
                    } catch (e: PackageManager.NameNotFoundException) { }

                    val detail = batteryDetailsMap[uid.toString()] ?: batteryDetailsMap[stat.packageName]
                    var usedMah = detail?.mah ?: 0.0

                    // Smart Fallback Estimation if dumpsys omitted mAh
                    if (usedMah <= 0.0 && stat.totalTimeInForeground > 0) {
                        val hours = stat.totalTimeInForeground / 3600000.0
                        val weight = when (category) {
                            ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_AUDIO -> 1.8
                            ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> 1.4
                            ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_MAPS -> 1.3
                            else -> 1.0
                        }
                        usedMah = hours * 180.0 * weight // ~180mA average screen-on drain
                    }

                    totalCalculatedMah += usedMah

                    UsageItem(
                        packageName = stat.packageName,
                        label = label,
                        icon = icon,
                        totalTimeInForeground = stat.totalTimeInForeground,
                        lastTimeUsed = stat.lastTimeUsed,
                        batteryMah = usedMah,
                        batteryPct = 0.0,
                        totalCpuMs = detail?.totalCpuMs ?: 0L,
                        wakeupCount = detail?.wakeupCount ?: 0,
                        totalWakelockMs = detail?.totalWakelockMs ?: 0L
                    )
                }

            val finalTotalMah = totalCalculatedMah.coerceAtLeast(1.0)
            lastTotalCalculatedMah = finalTotalMah
            val items = tempItems
                .map { item ->
                    val pct = (item.batteryMah / finalTotalMah) * 100.0
                    item.copy(batteryPct = pct)
                }
                .sortedByDescending { it.batteryMah }

            _usageStats.value = items
        }
    }

    private suspend fun getLastFullChargeTime(): Long {
        val logs = repo.getBatteryLogsSince(0)
        // Find the last log where battery reached >= 90% while charging
        val fullChargeLog = logs.lastOrNull { it.isCharging && it.batteryLevel >= 90 }
        return fullChargeLog?.timestamp ?: 0L
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

            buildChargingCurve()
        }
    }

    private suspend fun buildChargingCurve() {
        val allLogs = repo.getBatteryLogsSince(0)
        val buckets = mutableMapOf<Int, MutableList<Long>>()

        var prevLog: BatteryLogEntity? = null
        for (log in allLogs) {
            if (prevLog != null && prevLog.isCharging && log.isCharging) {
                val timeDiff = log.timestamp - prevLog.timestamp
                if (timeDiff in 1..300000L) {
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

    fun resetAppUsageStats() {
        viewModelScope.launch {
            repo.setUsageResetTime(System.currentTimeMillis())
            loadUsageStats(_currentInterval.value)
        }
    }

    fun resetOverviewBatteryLogs() {
        viewModelScope.launch {
            repo.clearBatteryLogs()
            loadOverview()
        }
    }

    fun resetData() {
        resetOverviewBatteryLogs()
    }
}
