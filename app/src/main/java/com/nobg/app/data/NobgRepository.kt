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

    // --- ADVANCED HIDDEN TWEAKS ---
    fun isForcedRefreshRateEnabled(): Boolean = prefs.getBoolean("forced_refresh_rate_enabled", false)
    fun getForcedRefreshRateValue(): Float = prefs.getFloat("forced_refresh_rate_value", 120.0f)

    suspend fun setForcedRefreshRate(enabled: Boolean, hz: Float) {
        prefs.edit().putBoolean("forced_refresh_rate_enabled", enabled).putFloat("forced_refresh_rate_value", hz).apply()
        if (enabled) {
            val hzStr = String.format(java.util.Locale.US, "%.1f", hz)
            val hzInt = hz.toInt()
            ShizukuManager.exec("settings put global min_refresh_rate $hzStr")
            ShizukuManager.exec("settings put global peak_refresh_rate $hzStr")
            ShizukuManager.exec("settings put global user_refresh_rate $hzInt")
        } else {
            ShizukuManager.exec("settings put global min_refresh_rate 0.0")
            ShizukuManager.exec("settings put global peak_refresh_rate 0.0")
            ShizukuManager.exec("settings put global user_refresh_rate 0")
        }
    }

    fun isForceFreeformEnabled(): Boolean = prefs.getBoolean("force_freeform_enabled", false)

    suspend fun setForceResizableAndFreeform(enabled: Boolean) {
        prefs.edit().putBoolean("force_freeform_enabled", enabled).apply()
        val valStr = if (enabled) "1" else "0"

        // Standard Android & Foldable Freeform Keys
        ShizukuManager.exec("settings put global force_resizable_activities $valStr")
        ShizukuManager.exec("settings put global enable_freeform_support $valStr")
        ShizukuManager.exec("settings put global force_allow_on_external $valStr")

        // Oppo Find N3 / ColorOS / OxygenOS Custom Keys
        ShizukuManager.exec("settings put global oppo_force_resizable $valStr")
        ShizukuManager.exec("settings put secure oppo_force_resizable $valStr")
        ShizukuManager.exec("settings put system oppo_force_resizable $valStr")
        ShizukuManager.exec("settings put global coloros_force_freeform $valStr")
    }

    fun isDisableSafeVolumeEnabled(): Boolean = prefs.getBoolean("disable_safe_volume_enabled", false)

    suspend fun setDisableSafeVolume(enabled: Boolean) {
        prefs.edit().putBoolean("disable_safe_volume_enabled", enabled).apply()
        val valStr = if (enabled) "0" else "1"
        ShizukuManager.exec("settings put global safe_media_volume_option $valStr")
    }

    fun isDisableCellularAlwaysOnEnabled(): Boolean = prefs.getBoolean("disable_cellular_always_on_enabled", false)

    suspend fun setDisableCellularAlwaysOn(enabled: Boolean) {
        prefs.edit().putBoolean("disable_cellular_always_on_enabled", enabled).apply()
        val valStr = if (enabled) "0" else "1"
        ShizukuManager.exec("settings put global mobile_data_always_on $valStr")
    }

    fun isShowRefreshRateOverlayEnabled(): Boolean = prefs.getBoolean("show_refresh_rate_overlay_enabled", false)

    suspend fun setShowRefreshRateOverlay(enabled: Boolean) {
        prefs.edit().putBoolean("show_refresh_rate_overlay_enabled", enabled).apply()
        val valStr = if (enabled) "1" else "0"
        ShizukuManager.exec("settings put surface_flinger show_refresh_rate $valStr")
        ShizukuManager.exec("service call SurfaceFlinger 1034 i32 $valStr")
    }
}



