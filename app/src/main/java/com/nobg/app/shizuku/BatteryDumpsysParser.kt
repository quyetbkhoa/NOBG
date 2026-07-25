package com.nobg.app.shizuku

import java.util.regex.Pattern

data class AppBatteryDetail(
    val mah: Double = 0.0,
    val userCpuMs: Long = 0L,
    val systemCpuMs: Long = 0L,
    val wakeupCount: Int = 0,
    val totalWakelockMs: Long = 0L
) {
    val totalCpuMs: Long get() = userCpuMs + systemCpuMs
}

object BatteryDumpsysParser {

    fun parseDumpsysDurationMs(timeStr: String): Long {
        var totalMs = 0L
        val parts = timeStr.trim().split("\\s+".toRegex())
        for (part in parts) {
            val p = part.trim()
            when {
                p.endsWith("ms") -> totalMs += p.removeSuffix("ms").toLongOrNull() ?: 0L
                p.endsWith("h") -> totalMs += (p.removeSuffix("h").toLongOrNull() ?: 0L) * 3600000L
                p.endsWith("m") && !p.endsWith("ms") -> totalMs += (p.removeSuffix("m").toLongOrNull() ?: 0L) * 60000L
                p.endsWith("s") && !p.endsWith("ms") -> totalMs += (p.removeSuffix("s").toLongOrNull() ?: 0L) * 1000L
            }
        }
        return totalMs
    }

    /**
     * Parse dumpsys batterystats for mAh, CPU time, Wakeup counts, and Wakelock duration.
     * Returns a map keyed by Uid (String e.g. "10197") or Package Name.
     */
    suspend fun getAppBatteryDetails(): Map<String, AppBatteryDetail> {
        val resultMap = mutableMapOf<String, AppBatteryDetail>()
        if (!ShizukuManager.isShizukuRunning() || !ShizukuManager.hasPermission() || !ShizukuManager.isServiceBound()) {
            return resultMap
        }

        try {
            var output = ShizukuManager.exec("dumpsys batterystats --charged")
            if (output.isBlank() || output.startsWith("ERROR")) {
                output = ShizukuManager.exec("dumpsys batterystats")
            }

            val uidBlocks = output.split(Regex("\n(?=\\s*(?:UID|Uid)\\s+)"))
            for (block in uidBlocks) {
                val firstLine = block.lines().firstOrNull() ?: continue
                if (!firstLine.trim().startsWith("Uid") && !firstLine.trim().startsWith("UID")) continue

                val uidPattern = Pattern.compile("(?:UID|Uid)\\s+(u0a\\d+|\\d+):?\\s*([0-9.]+)?")
                val matcher = uidPattern.matcher(firstLine)
                if (!matcher.find()) continue

                var idStr = matcher.group(1) ?: continue
                if (idStr.startsWith("u0a")) {
                    val appIndex = idStr.substring(3).toIntOrNull() ?: continue
                    idStr = (10000 + appIndex).toString()
                }

                val mah = matcher.group(2)?.toDoubleOrNull() ?: 0.0

                var userCpuMs = 0L
                var systemCpuMs = 0L
                var wakeups = 0
                var wakelockMs = 0L

                for (line in block.lines()) {
                    val trimmed = line.trim()
                    when {
                        trimmed.contains("User cpu time:") -> {
                            val userPart = trimmed.substringAfter("User cpu time:").substringBefore(",")
                            userCpuMs += parseDumpsysDurationMs(userPart)
                            if (trimmed.contains("System cpu time:")) {
                                val sysPart = trimmed.substringAfter("System cpu time:")
                                systemCpuMs += parseDumpsysDurationMs(sysPart)
                            }
                        }
                        trimmed.contains("Wakeups:") -> {
                            val countStr = trimmed.substringAfter("Wakeups:").trim().substringBefore(" ").substringBefore("(")
                            wakeups += (countStr.toIntOrNull() ?: 0)
                        }
                        trimmed.contains("Wake lock") -> {
                            val lockTimeStr = trimmed.substringAfter(":").trim().substringBefore("(")
                            wakelockMs += parseDumpsysDurationMs(lockTimeStr)
                        }
                    }
                }

                val currentDetail = resultMap[idStr]
                if (currentDetail == null) {
                    resultMap[idStr] = AppBatteryDetail(mah, userCpuMs, systemCpuMs, wakeups, wakelockMs)
                } else {
                    resultMap[idStr] = AppBatteryDetail(
                        maxOf(currentDetail.mah, mah),
                        currentDetail.userCpuMs + userCpuMs,
                        currentDetail.systemCpuMs + systemCpuMs,
                        currentDetail.wakeupCount + wakeups,
                        currentDetail.totalWakelockMs + wakelockMs
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultMap
    }

    suspend fun getAppBatteryUsage(): Map<String, Double> {
        val details = getAppBatteryDetails()
        return details.mapValues { it.value.mah }
    }
}
