package com.nobg.app.data

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import com.nobg.app.shizuku.BatteryDumpsysParser

data class NobgSelfStats(
    val ramMb: Double,
    val cpuTimeMs: Long,
    val cpuPct: Double,
    val batteryMah: Double,
    val batteryPct: Double
)

object NobgSelfStatsHelper {

    private var lastCpuTimeMs: Long = 0L
    private var lastUptimeMs: Long = 0L

    suspend fun getNobgSelfStats(context: Context): NobgSelfStats {
        // 1. RAM Measurement (PSS in MB)
        var ramMb = 0.0
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = am.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            if (memInfo.isNotEmpty()) {
                val totalPssKb = memInfo[0].totalPss
                ramMb = totalPssKb / 1024.0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. CPU Measurement
        val currentCpuTimeMs = Process.getElapsedCpuTime()
        val currentUptimeMs = SystemClock.elapsedRealtime()

        var cpuPct = 0.1 // Baseline lightweight estimate
        if (lastUptimeMs > 0 && currentUptimeMs > lastUptimeMs) {
            val cpuDiff = (currentCpuTimeMs - lastCpuTimeMs).toDouble()
            val timeDiff = (currentUptimeMs - lastUptimeMs).toDouble()
            if (timeDiff > 0) {
                cpuPct = ((cpuDiff / timeDiff) * 100.0).coerceIn(0.01, 100.0)
            }
        } else {
            val totalUptime = currentUptimeMs.toDouble()
            if (totalUptime > 0) {
                cpuPct = ((currentCpuTimeMs.toDouble() / totalUptime) * 100.0).coerceIn(0.01, 100.0)
            }
        }

        lastCpuTimeMs = currentCpuTimeMs
        lastUptimeMs = currentUptimeMs

        // 3. Battery Measurement (dumpsys or estimation based on CPU time)
        var batteryMah = 0.0
        try {
            val myUid = context.applicationInfo.uid
            val batteryMap = BatteryDumpsysParser.getAppBatteryDetails()
            val detail = batteryMap[myUid.toString()] ?: batteryMap[context.packageName]
            if (detail != null && detail.mah > 0) {
                batteryMah = detail.mah
            }
        } catch (_: Exception) {}

        if (batteryMah <= 0.0) {
            // Smart estimation: process CPU time (hours) * average ~150mA CPU active current
            val cpuHours = currentCpuTimeMs / 3600000.0
            batteryMah = (cpuHours * 150.0).coerceAtLeast(0.1)
        }

        // Estimate battery % relative to typical ~4000mAh capacity
        val batteryPct = (batteryMah / 4000.0) * 100.0

        return NobgSelfStats(
            ramMb = ramMb,
            cpuTimeMs = currentCpuTimeMs,
            cpuPct = cpuPct,
            batteryMah = batteryMah,
            batteryPct = batteryPct
        )
    }
}
