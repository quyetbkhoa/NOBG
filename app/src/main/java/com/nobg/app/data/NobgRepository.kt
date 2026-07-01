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
    private val prefs: SharedPreferences = context.getSharedPreferences("nobg_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BATTERY_RESET_TIME = "battery_reset_time"
        private const val KEY_USAGE_RESET_TIME = "usage_reset_time"
    }

    fun observeApps(): Flow<List<AppEntity>> = appDao.observeAll()

    suspend fun getEnabledApps(): List<AppEntity> = appDao.getAllEnabled()

    suspend fun getConfig(pkg: String): AppEntity? = appDao.get(pkg)

    suspend fun hasBackup(pkg: String): Boolean = backupDao.get(pkg)?.hasBackup == true

    /** Snapshot original state, only if not already backed up. */
    private suspend fun backupIfNeeded(pkg: String) {
        if (backupDao.get(pkg)?.hasBackup == true) return

        val enabledState = ShizukuManager.getApplicationEnabledState(pkg)
        val opsMap = JSONObject()
        for (op in AppOps.ALL) {
            opsMap.put(op, ShizukuManager.getAppOp(pkg, op))
        }
        backupDao.upsert(
            BackupEntity(
                packageName = pkg,
                originalEnabledState = enabledState,
                appOpsJson = opsMap.toString(),
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
        if (backup.originalEnabledState == 3) {
            ShizukuManager.disablePackage(pkg)
        } else {
            ShizukuManager.enablePackage(pkg)
        }
        val opsMap = JSONObject(backup.appOpsJson)
        for (op in AppOps.ALL) {
            val original = opsMap.optString(op, "allow")
            ShizukuManager.setAppOp(pkg, op, allow = original == "allow")
        }
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
}
