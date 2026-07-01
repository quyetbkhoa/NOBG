package com.nobg.app.data

import android.content.Context
import com.nobg.app.shizuku.AppOps
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class NobgRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val appDao = db.appDao()
    private val backupDao = db.backupDao()

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

        // Standard + Aggressive both lock down background behavior persistently.
        if (mode == NobgMode.STANDARD || mode == NobgMode.AGGRESSIVE) {
            for (op in AppOps.ALL) {
                ShizukuManager.setAppOp(pkg, op, allow = false)
            }
        }
        // Disable/Enable mode: app starts disabled immediately (until launched via NOBG).
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

    /** Turn NOBG OFF - restore ops (does NOT auto restore full original, use resetApp for that). */
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
            // re-apply restrictions for the new mode
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
}
