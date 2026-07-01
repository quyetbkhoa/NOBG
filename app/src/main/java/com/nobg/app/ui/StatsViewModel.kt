package com.nobg.app.ui

import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobg.app.shizuku.BatteryDumpsysParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class UsageItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long,
    val batteryMah: Double = 0.0
)

enum class StatsInterval { DAILY, WEEKLY }

class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm = app.packageManager

    private val _usageStats = MutableStateFlow<List<UsageItem>>(emptyList())
    val usageStats: StateFlow<List<UsageItem>> = _usageStats

    private val _currentInterval = MutableStateFlow(StatsInterval.DAILY)
    val currentInterval: StateFlow<StatsInterval> = _currentInterval

    init {
        loadStats(StatsInterval.DAILY)
    }

    fun loadStats(interval: StatsInterval) {
        _currentInterval.value = interval
        viewModelScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            val endTime = cal.timeInMillis
            
            when (interval) {
                StatsInterval.DAILY -> cal.add(Calendar.DAY_OF_YEAR, -1)
                StatsInterval.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, -7)
            }
            val startTime = cal.timeInMillis

            // queryAndAggregateUsageStats merges all data for a package within the timeframe
            val statsMap = usm.queryAndAggregateUsageStats(startTime, endTime)

            // Fetch actual battery usage (mAh) from dumpsys via Shizuku
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
                    } catch (e: PackageManager.NameNotFoundException) {
                        // Keep package name if not found
                    }
                    
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
}
