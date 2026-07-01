package com.nobg.app.shizuku

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

object BatteryDumpsysParser {

    /**
     * Parse dumpsys batterystats for current session app battery usage (mAh).
     * Returns a map of Uid (String) to mAh (Double)
     */
    fun getAppBatteryUsage(): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        if (!ShizukuManager.isShizukuRunning() || !ShizukuManager.hasPermission()) {
            return result
        }

        try {
            val process = Shizuku.newProcess(arrayOf("dumpsys", "batterystats"), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            
            // Regex to match lines like "  UID u0a197: 204 fg: 164 bg: 13.9"
            // or "  Uid 10197: 204"
            val uidPattern = Pattern.compile("^\\s*(?:UID|Uid) (u0a\\d+|\\d+):\\s+([0-9.]+)")

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val m = uidPattern.matcher(line!!)
                if (m.find()) {
                    var uidStr = m.group(1)
                    val mahStr = m.group(2)
                    
                    if (uidStr.startsWith("u0a")) {
                        // u0a197 -> 10197
                        val appIndex = uidStr.substring(3).toIntOrNull() ?: continue
                        uidStr = (10000 + appIndex).toString()
                    }
                    
                    val mah = mahStr.toDoubleOrNull() ?: continue
                    result[uidStr] = mah
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
