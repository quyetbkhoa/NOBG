package com.nobg.app.shizuku

import java.util.regex.Pattern

object BatteryDumpsysParser {

    /**
     * Parse dumpsys batterystats --charged for exact kernel app battery usage (mAh).
     * Returns a map of Uid/Package (String) to mAh (Double).
     */
    suspend fun getAppBatteryUsage(): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        if (!ShizukuManager.isShizukuRunning() || !ShizukuManager.hasPermission() || !ShizukuManager.isServiceBound()) {
            return result
        }

        try {
            // First try --charged for exact since-last-full-charge data, fallback to basic dumpsys batterystats
            var output = ShizukuManager.exec("dumpsys batterystats --charged")
            if (output.isBlank() || output.startsWith("ERROR")) {
                output = ShizukuManager.exec("dumpsys batterystats")
            }

            // Pattern 1: Match Uid lines like "  Uid u0a197: 204.5 ( cpu=164.0 ... )" or "  Uid 10197: 204.5"
            val uidPattern = Pattern.compile("^\\s*(?:UID|Uid)\\s+(u0a\\d+|\\d+):\\s+([0-9.]+)", Pattern.MULTILINE)
            val matcher1 = uidPattern.matcher(output)
            while (matcher1.find()) {
                var uidStr = matcher1.group(1) ?: continue
                val mahStr = matcher1.group(2) ?: continue

                if (uidStr.startsWith("u0a")) {
                    val appIndex = uidStr.substring(3).toIntOrNull() ?: continue
                    uidStr = (10000 + appIndex).toString()
                }

                val mah = mahStr.toDoubleOrNull() ?: continue
                if (mah > 0) {
                    result[uidStr] = maxOf(result[uidStr] ?: 0.0, mah)
                }
            }

            // Pattern 2: Match package lines like "  pkg com.example.app: 12.5"
            val pkgPattern = Pattern.compile("^\\s*(?:pkg|package)\\s+([a-zA-Z0-9._]+):\\s+([0-9.]+)", Pattern.MULTILINE)
            val matcher2 = pkgPattern.matcher(output)
            while (matcher2.find()) {
                val pkgName = matcher2.group(1) ?: continue
                val mahStr = matcher2.group(2) ?: continue
                val mah = mahStr.toDoubleOrNull() ?: continue
                if (mah > 0) {
                    result[pkgName] = maxOf(result[pkgName] ?: 0.0, mah)
                }
            }

            // Pattern 3: Match "Estimated power use (mAh):" block
            if (output.contains("Estimated power use (mAh):")) {
                val section = output.substringAfter("Estimated power use (mAh):").substringBefore("Capacity:")
                val sectionMatcher = Pattern.compile("^\\s*(?:Uid|UID|package|pkg|User|App)?\\s*([a-zA-Z0-9._]+):\\s+([0-9.]+)", Pattern.MULTILINE).matcher(section)
                while (sectionMatcher.find()) {
                    var idStr = sectionMatcher.group(1) ?: continue
                    val mahStr = sectionMatcher.group(2) ?: continue

                    if (idStr.startsWith("u0a")) {
                        val appIndex = idStr.substring(3).toIntOrNull() ?: continue
                        idStr = (10000 + appIndex).toString()
                    }

                    val mah = mahStr.toDoubleOrNull() ?: continue
                    if (mah > 0) {
                        result[idStr] = maxOf(result[idStr] ?: 0.0, mah)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
