package com.nobg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobg.app.data.BatteryLogEntity
import com.nobg.app.data.NobgRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GlobalStatsData(
    val totalTimeMs: Long = 0,
    val screenOnTimeMs: Long = 0,
    val chargingTimeMs: Long = 0,
    val batteryDischargedPct: Int = 0,
    val batteryChargedPct: Int = 0,
    val hasData: Boolean = false
)

class GlobalStatsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = NobgRepository(app)

    private val _stats = MutableStateFlow(GlobalStatsData())
    val stats: StateFlow<GlobalStatsData> = _stats

    fun loadStats() {
        viewModelScope.launch {
            val logs = repo.getBatteryLogsSince(0)
            if (logs.isEmpty()) {
                _stats.value = GlobalStatsData()
                return@launch
            }

            var screenOnMs = 0L
            var chargingMs = 0L
            var dischargedPct = 0
            var chargedPct = 0

            var prevLog: BatteryLogEntity? = null

            for (log in logs) {
                if (prevLog != null) {
                    val timeDiff = log.timestamp - prevLog.timestamp
                    
                    // Cap at 12 hours for a single gap to avoid massive outliers if app killed
                    val validDiff = if (timeDiff in 0..43200000) timeDiff else 0L

                    if (prevLog.isScreenOn) screenOnMs += validDiff
                    if (prevLog.isCharging) chargingMs += validDiff

                    val levelDiff = log.batteryLevel - prevLog.batteryLevel
                    if (levelDiff > 0) chargedPct += levelDiff
                    if (levelDiff < 0) dischargedPct += (-levelDiff)
                }
                prevLog = log
            }

            val firstLog = logs.first()
            val lastLog = logs.last()
            val totalMs = lastLog.timestamp - firstLog.timestamp

            _stats.value = GlobalStatsData(
                totalTimeMs = totalMs,
                screenOnTimeMs = screenOnMs,
                chargingTimeMs = chargingMs,
                batteryDischargedPct = dischargedPct,
                batteryChargedPct = chargedPct,
                hasData = true
            )
        }
    }

    fun resetData() {
        viewModelScope.launch {
            repo.clearBatteryLogs()
            loadStats()
        }
    }
}
