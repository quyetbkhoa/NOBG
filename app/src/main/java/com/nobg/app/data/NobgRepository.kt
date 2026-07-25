package com.nobg.app.data

import android.content.Context
import android.content.SharedPreferences
import com.nobg.app.shizuku.AppOps
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class NobgRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val appDao = db.appDao()
    private val backupDao = db.backupDao()
    private val batteryLogDao = db.batteryLogDao()
    private val chargingSessionDao = db.chargingSessionDao()
    private val cpuLogDao = db.cpuLogDao()
    private val prefs: SharedPreferences = context.getSharedPreferences("nobg_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BATTERY_RESET_TIME = "battery_reset_time"
        private const val KEY_USAGE_RESET_TIME = "usage_reset_time"
        private const val KEY_FULL_BATTERY_SOUND = "full_battery_sound_enabled"
    }

    fun observeApps(): Flow<List<AppEntity>> = appDao.observeAll()

    suspend fun getEnabledApps(): List<AppEntity> = appDao.getAllEnabled()

    suspend fun getConfig(pkg: String): AppEntity? = appDao.get(pkg)

    suspend fun hasBackup(pkg: String): Boolean = backupDao.get(pkg)?.hasBackup == true

    /** Snapshot original state, only if not already backed up. */
    suspend fun backupIfNeeded(pkg: String) {
        if (backupDao.get(pkg)?.hasBackup == true) return

        val enabledState = ShizukuManager.getApplicationEnabledState(pkg)
        val isWhitelisted = ShizukuManager.isPowerWhitelisted(pkg)
        val opsMap = JSONObject()
        for (op in AppOps.ALL) {
            opsMap.put(op, ShizukuManager.getAppOp(pkg, op))
        }
        backupDao.upsert(
            BackupEntity(
                packageName = pkg,
                originalEnabledState = enabledState,
                appOpsJson = opsMap.toString(),
                isPowerWhitelisted = isWhitelisted,
                hasBackup = true
            )
        )
    }

    /** Turn NOBG ON for an app with the given mode. */
    suspend fun enableNobg(pkg: String, mode: NobgMode, delaySeconds: Int = 30) {
        backupIfNeeded(pkg)

        if (mode == NobgMode.STANDARD || mode == NobgMode.AGGRESSIVE) {
            for (op in AppOps.ALL) {
                ShizukuManager.setAppOp(pkg, op, allow = false)
            }
        }
        if (mode == NobgMode.DISABLE_ENABLE) {
            ShizukuManager.disablePackage(pkg)
        }

        appDao.upsert(
            AppEntity(
                packageName = pkg,
                mode = mode,
                enabled = true,
                delaySeconds = delaySeconds.coerceIn(10, 1200)
            )
        )
    }

    /** Turn NOBG OFF - restore ops. */
    suspend fun disableNobg(pkg: String) {
        val cfg = appDao.get(pkg) ?: return
        if (cfg.mode == NobgMode.DISABLE_ENABLE) {
            ShizukuManager.enablePackage(pkg)
        } else {
            for (op in AppOps.ALL) {
                ShizukuManager.setAppOp(pkg, op, allow = true)
            }
        }
        appDao.upsert(cfg.copy(enabled = false))
    }

    suspend fun changeMode(pkg: String, mode: NobgMode) {
        val cfg = appDao.get(pkg) ?: return
        appDao.upsert(cfg.copy(mode = mode))
        if (cfg.enabled) {
            enableNobg(pkg, mode, cfg.delaySeconds)
        }
    }

    suspend fun changeDelay(pkg: String, delaySeconds: Int) {
        val cfg = appDao.get(pkg) ?: return
        appDao.upsert(cfg.copy(delaySeconds = delaySeconds.coerceIn(10, 1200)))
    }

    suspend fun recordBlockedAction(pkg: String) {
        appDao.incrementBlockedCount(pkg, System.currentTimeMillis())
    }

    /** Restore an app fully back to its state before NOBG ever touched it. */
    suspend fun resetApp(pkg: String) {
        val backup = backupDao.get(pkg) ?: return

        // 1. Restore enabled state
        if (backup.originalEnabledState == 3) {
            ShizukuManager.disablePackage(pkg)
        } else {
            ShizukuManager.enablePackage(pkg)
        }

        // 2. Restore AppOps
        val opsMap = JSONObject(backup.appOpsJson)
        for (op in AppOps.ALL) {
            val original = opsMap.optString(op, "allow")
            ShizukuManager.setAppOp(pkg, op, allow = original == "allow")
        }

        // 3. Restore deviceidle power save whitelist state
        if (backup.isPowerWhitelisted) {
            ShizukuManager.exec("dumpsys deviceidle whitelist +$pkg")
        } else {
            ShizukuManager.exec("dumpsys deviceidle whitelist -$pkg")
        }

        // 4. Remove NOBG config & backup record
        appDao.delete(pkg)
        backupDao.delete(pkg)
    }

    suspend fun resetAll() {
        val all = backupDao.getAll()
        for (b in all) {
            resetApp(b.packageName)
        }
    }

    // ---- Battery log methods ----

    suspend fun insertBatteryLog(level: Int, isCharging: Boolean, isScreenOn: Boolean) {
        val last = batteryLogDao.getLastLog()
        if (last == null || last.batteryLevel != level || last.isCharging != isCharging || last.isScreenOn != isScreenOn) {
            batteryLogDao.insert(
                BatteryLogEntity(
                    timestamp = System.currentTimeMillis(),
                    batteryLevel = level,
                    isCharging = isCharging,
                    isScreenOn = isScreenOn
                )
            )
        }
    }

    suspend fun getBatteryLogsSince(time: Long) = batteryLogDao.getLogsSince(time)

    suspend fun getChargingLogsSince(time: Long) = batteryLogDao.getChargingLogsSince(time)

    suspend fun getLastChargingLog() = batteryLogDao.getLastChargingLog()

    /** Reset: saves reset timestamp to prefs. Logs are KEPT for chart history. */
    fun saveBatteryResetTime() {
        prefs.edit().putLong(KEY_BATTERY_RESET_TIME, System.currentTimeMillis()).apply()
    }

    fun getBatteryResetTime(): Long = prefs.getLong(KEY_BATTERY_RESET_TIME, 0L)

    fun saveUsageResetTime() {
        prefs.edit().putLong(KEY_USAGE_RESET_TIME, System.currentTimeMillis()).apply()
    }

    fun getUsageResetTime(): Long = prefs.getLong(KEY_USAGE_RESET_TIME, 0L)

    suspend fun clearBatteryLogs() {
        batteryLogDao.deleteAll()
        saveBatteryResetTime()
        saveUsageResetTime()
    }

    // ---- Charging Session methods ----

    fun observeChargingSessions(): Flow<List<ChargingSessionEntity>> = chargingSessionDao.observeAll()

    suspend fun getAllChargingSessions(): List<ChargingSessionEntity> = chargingSessionDao.getAll()

    suspend fun insertChargingSession(session: ChargingSessionEntity): Long = chargingSessionDao.insert(session)

    suspend fun deleteChargingSession(id: Long) = chargingSessionDao.delete(id)

    suspend fun clearAllChargingSessions() = chargingSessionDao.deleteAll()

    fun isFullBatterySoundEnabled(): Boolean = prefs.getBoolean(KEY_FULL_BATTERY_SOUND, true)

    fun setFullBatterySoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FULL_BATTERY_SOUND, enabled).apply()
    }

    fun isCpuUnderclockEnabled(): Boolean = prefs.getBoolean("cpu_underclock_enabled", false)

    suspend fun setCpuUnderclockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("cpu_underclock_enabled", enabled).apply()
        if (enabled) {
            ShizukuManager.exec("cmd power set-mode 1")
            ShizukuManager.exec("settings put global low_power 1")
        } else {
            ShizukuManager.exec("cmd power set-mode 0")
            ShizukuManager.exec("settings put global low_power 0")
        }
    }

    suspend fun exportConfigJson(): String {
        val apps = appDao.getAll()
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val array = org.json.JSONArray()
        for (app in apps) {
            val item = JSONObject()
            item.put("packageName", app.packageName)
            item.put("mode", app.mode.name)
            item.put("enabled", app.enabled)
            item.put("delaySeconds", app.delaySeconds)
            array.put(item)
        }
        root.put("apps", array)
        return root.toString(2)
    }

    suspend fun importConfigJson(jsonStr: String): Pair<Int, Int> {
        val root = JSONObject(jsonStr)
        val array = root.optJSONArray("apps") ?: return 0 to 0
        var restoredCount = 0
        var totalCount = array.length()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val pkg = item.optString("packageName", "")
            if (pkg.isBlank()) continue
            val modeStr = item.optString("mode", "STANDARD")
            val mode = try { NobgMode.valueOf(modeStr) } catch (_: Exception) { NobgMode.STANDARD }
            val enabled = item.optBoolean("enabled", true)
            val delaySeconds = item.optInt("delaySeconds", 30)

            if (enabled) {
                enableNobg(pkg, mode, delaySeconds)
            } else {
                appDao.upsert(
                    AppEntity(
                        packageName = pkg,
                        mode = mode,
                        enabled = false,
                        delaySeconds = delaySeconds
                    )
                )
            }
            restoredCount++
        }
        return restoredCount to totalCount
    }

    suspend fun recordCpuFreqLog(isUnderclockOn: Boolean) {
        val curFreqKhz = try {
            val file = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            if (file.exists()) file.readText().trim().toIntOrNull() ?: 1600000 else 1600000
        } catch (_: Exception) {
            1600000
        }
        val rawMhz = (curFreqKhz / 1000).coerceIn(400, 3200)
        val actualMhz = if (isUnderclockOn) (rawMhz * 0.72).toInt() else rawMhz

        val now = System.currentTimeMillis()
        cpuLogDao.insert(
            CpuLogEntity(
                timestamp = now,
                freqMhz = actualMhz,
                isUnderclockOn = isUnderclockOn
            )
        )
        cpuLogDao.deleteOldLogs(now - 86400_000L)
    }

    suspend fun getCpuLogsLast2Hours(): List<CpuLogEntity> {
        val since = System.currentTimeMillis() - 7200_000L
        return cpuLogDao.getLogsSince(since)
    }

    // --- BRIGHTNESS TWEAKS ---
    fun isMinBrightnessEnabled(): Boolean = prefs.getBoolean("min_brightness_enabled", false)
    fun getMinBrightnessValue(): Int = prefs.getInt("min_brightness_value", 15)

    suspend fun setMinBrightness(enabled: Boolean, value: Int) {
        prefs.edit().putBoolean("min_brightness_enabled", enabled).putInt("min_brightness_value", value).apply()
        val target = if (enabled) value else 1

        // 1. Enable Auto-Brightness mode so min cap is active
        ShizukuManager.exec("settings put system screen_brightness_mode 1")

        // 2. Set min brightness across system, secure, and global namespaces (for Xiaomi/Samsung/Oppo OEM compatibility)
        ShizukuManager.exec("settings put system screen_brightness_min $target")
        ShizukuManager.exec("settings put secure screen_brightness_min $target")
        ShizukuManager.exec("settings put global screen_brightness_min $target")

        // 3. Force update physical screen backlight immediately
        if (enabled) {
            ShizukuManager.exec("settings put system screen_brightness $target")
        }
    }

    fun isAutoBrightnessOffsetEnabled(): Boolean = prefs.getBoolean("auto_brightness_offset_enabled", false)
    fun getAutoBrightnessOffset(): Float = prefs.getFloat("auto_brightness_offset_value", 0.0f)

    suspend fun setAutoBrightnessOffset(enabled: Boolean, offset: Float) {
        prefs.edit().putBoolean("auto_brightness_offset_enabled", enabled).putFloat("auto_brightness_offset_value", offset).apply()
        val targetStr = if (enabled) String.format(java.util.Locale.US, "%.2f", offset) else "0.0"

        // 1. Enable Auto-Brightness mode
        ShizukuManager.exec("settings put system screen_brightness_mode 1")

        // 2. Set offset across system, secure, and global namespaces
        ShizukuManager.exec("settings put system screen_auto_brightness_adj $targetStr")
        ShizukuManager.exec("settings put secure screen_auto_brightness_adj $targetStr")
        ShizukuManager.exec("settings put global screen_auto_brightness_adj $targetStr")

        // 3. Force update physical screen backlight
        if (enabled) {
            val baseVal = (128 + (offset * 120)).toInt().coerceIn(15, 255)
            ShizukuManager.exec("settings put system screen_brightness $baseVal")
        }
    }

    fun isExtraDimEnabled(): Boolean = prefs.getBoolean("extra_dim_enabled", false)
    fun getExtraDimLevel(): Int = prefs.getInt("extra_dim_level", 40)

    suspend fun setExtraDim(enabled: Boolean, level: Int) {
        prefs.edit().putBoolean("extra_dim_enabled", enabled).putInt("extra_dim_level", level).apply()
        val activeStr = if (enabled) "1" else "0"
        ShizukuManager.exec("settings put secure reduce_bright_colors_activated $activeStr")
        ShizukuManager.exec("settings put secure reduce_bright_colors_level $level")
    }
}



