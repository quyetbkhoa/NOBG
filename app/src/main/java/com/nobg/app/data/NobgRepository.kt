package com.nobg.app.data

import android.content.Context
import android.content.SharedPreferences
import com.nobg.app.shizuku.AppOps
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class NobgRepository(private val context: Context) {

    private val db = AppDatabase.get(context)
    private val appDao = db.appDao()
    private val backupDao = db.backupDao()
    private val batteryLogDao = db.batteryLogDao()
    private val chargingSessionDao = db.chargingSessionDao()
    private val cpuLogDao = db.cpuLogDao()
    private val notificationReadDao = db.notificationReadDao()
    private val bluetoothDeviceDao = db.bluetoothDeviceDao()
    private val prefs: SharedPreferences = context.getSharedPreferences("nobg_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BATTERY_RESET_TIME = "battery_reset_time"
        private const val KEY_USAGE_RESET_TIME = "usage_reset_time"
        private const val KEY_FULL_BATTERY_SOUND = "full_battery_sound_enabled"
        private const val KEY_NOTIF_READ_GLOBAL_ENABLED = "notif_read_global_enabled"
        private const val KEY_NOTIF_READ_ONLY_BT = "notif_read_only_selected_bt"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        private const val KEY_TTS_VOLUME = "tts_volume"
        private const val KEY_NOTIF_READ_DUCKING = "notif_read_ducking"
        private const val KEY_TTS_PAN = "tts_pan"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_AI_API_KEY = "ai_api_key"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_SUMMARY_ENABLED = "ai_summary_enabled"
        private const val KEY_AI_FILTER_ENABLED = "ai_filter_enabled"
        private const val KEY_AI_FILTER_STRICTNESS = "ai_filter_strictness"
        private const val KEY_THEME_MODE = "theme_mode"
    }

    /** Chế độ giao diện: "SYSTEM" (theo hệ thống) | "LIGHT" (Trắng - Xanh) | "DARK" (Tối) */
    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
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

    fun observeChargingSessions(): Flow<List<ChargingSessionEntity>> = chargingSessionDao.observeAll().map { list ->
        list.distinctBy { "${it.startLevel}_${it.endLevel}_${it.startTimeMs / 120000L}" }
    }

    suspend fun getAllChargingSessions(): List<ChargingSessionEntity> {
        val list = chargingSessionDao.getAll()
        // Deduplicate in-memory by distinct startLevel, endLevel, and startTimeMs bucket
        return list.distinctBy { "${it.startLevel}_${it.endLevel}_${it.startTimeMs / 120000L}" }
    }

    suspend fun insertChargingSession(session: ChargingSessionEntity): Long = chargingSessionDao.insert(session)

    suspend fun insertChargingSessionDeduplicated(session: ChargingSessionEntity) {
        val existing = chargingSessionDao.getAll()
        val isDuplicate = existing.any {
            Math.abs(it.startTimeMs - session.startTimeMs) < 60_000L ||
            (it.startLevel == session.startLevel && it.endLevel == session.endLevel && Math.abs(it.endTimeMs - session.endTimeMs) < 180_000L)
        }
        if (!isDuplicate) {
            chargingSessionDao.insert(session)
        }
    }

    suspend fun deleteChargingSession(id: Long) = chargingSessionDao.delete(id)

    suspend fun clearAllChargingSessions() = chargingSessionDao.deleteAll()

    fun isFullBatterySoundEnabled(): Boolean = prefs.getBoolean(KEY_FULL_BATTERY_SOUND, true)

    fun setFullBatterySoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FULL_BATTERY_SOUND, enabled).apply()
    }

    suspend fun exportConfigJson(): String {
        val apps = appDao.getAll()
        val root = JSONObject()
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())

        // 1. Global Preferences
        val globalObj = JSONObject().apply {
            put("fullBatterySoundEnabled", isFullBatterySoundEnabled())
            put("lastActiveScreen", getLastActiveScreen())
        }
        root.put("globalSettings", globalObj)

        // 2. Widget Customization Preferences
        val widgetCfg = com.nobg.app.widget.WidgetConfigManager.getConfig(context)
        val widgetObj = JSONObject().apply {
            put("theme", widgetCfg.theme)
            put("textColor", widgetCfg.textColor)
            put("opacityPct", widgetCfg.opacityPct)
            put("numColumns", widgetCfg.numColumns)
            put("iconSizeDp", widgetCfg.iconSizeDp)
            put("cornerRadiusDp", widgetCfg.cornerRadiusDp)
        }
        root.put("widgetSettings", widgetObj)

        // 3. Apps Complete State List
        val pm = context.packageManager
        val array = org.json.JSONArray()
        for (app in apps) {
            val item = JSONObject().apply {
                put("packageName", app.packageName)
                put("mode", app.mode.name)
                put("enabled", app.enabled)
                put("delaySeconds", app.delaySeconds)
                put("addedAt", app.addedAt)
                put("blockedCount", app.blockedCount)
                put("lastActionAt", app.lastActionAt)
                put("isFrozenShelf", app.isFrozenShelf)

                // Package disabled / frozen state
                val isFrozen = try {
                    val appInfo = pm.getApplicationInfo(app.packageName, 0)
                    !appInfo.enabled
                } catch (_: Exception) {
                    false
                }
                put("isFrozen", isFrozen)

                // Backup Entity (Original AppOps, Power Save Whitelist, etc.)
                val backup = backupDao.get(app.packageName)
                if (backup != null) {
                    val backupObj = JSONObject().apply {
                        put("originalEnabledState", backup.originalEnabledState)
                        put("appOpsJson", backup.appOpsJson)
                        put("isPowerWhitelisted", backup.isPowerWhitelisted)
                        put("hasBackup", backup.hasBackup)
                        put("backupTimestamp", backup.backupTimestamp)
                    }
                    put("backup", backupObj)
                }
            }
            array.put(item)
        }
        root.put("apps", array)
        return root.toString(2)
    }

    suspend fun importConfigJson(jsonStr: String): Pair<Int, Int> {
        val root = JSONObject(jsonStr)

        // 1. Restore Global Settings if present
        root.optJSONObject("globalSettings")?.let { global ->
            if (global.has("fullBatterySoundEnabled")) {
                setFullBatterySoundEnabled(global.optBoolean("fullBatterySoundEnabled", true))
            }
            if (global.has("lastActiveScreen")) {
                setLastActiveScreen(global.optString("lastActiveScreen", "LIST"))
            }
        }

        // 2. Restore Widget Config if present
        root.optJSONObject("widgetSettings")?.let { widgetObj ->
            val wCfg = com.nobg.app.widget.WidgetConfig(
                theme = widgetObj.optString("theme", "DARK"),
                textColor = widgetObj.optString("textColor", "SYSTEM"),
                opacityPct = widgetObj.optInt("opacityPct", 85),
                numColumns = widgetObj.optInt("numColumns", 2),
                iconSizeDp = widgetObj.optInt("iconSizeDp", 48),
                cornerRadiusDp = widgetObj.optInt("cornerRadiusDp", 18)
            )
            com.nobg.app.widget.WidgetConfigManager.saveConfig(context, wCfg)
        }

        // 3. Restore Apps
        val array = root.optJSONArray("apps") ?: return 0 to 0
        var restoredCount = 0
        val totalCount = array.length()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val pkg = item.optString("packageName", "")
            if (pkg.isBlank()) continue

            val modeStr = item.optString("mode", "STANDARD")
            val mode = try { NobgMode.valueOf(modeStr) } catch (_: Exception) { NobgMode.STANDARD }
            val enabled = item.optBoolean("enabled", true)
            val delaySeconds = item.optInt("delaySeconds", 30)
            val addedAt = item.optLong("addedAt", System.currentTimeMillis())
            val blockedCount = item.optInt("blockedCount", 0)
            val lastActionAt = item.optLong("lastActionAt", 0L)
            val isFrozenShelf = item.optBoolean("isFrozenShelf", false)
            val isFrozen = item.optBoolean("isFrozen", false)

            // Save Backup Entity if present
            item.optJSONObject("backup")?.let { backupObj ->
                backupDao.upsert(
                    BackupEntity(
                        packageName = pkg,
                        originalEnabledState = backupObj.optInt("originalEnabledState", 0),
                        appOpsJson = backupObj.optString("appOpsJson", "{}"),
                        isPowerWhitelisted = backupObj.optBoolean("isPowerWhitelisted", false),
                        hasBackup = backupObj.optBoolean("hasBackup", true),
                        backupTimestamp = backupObj.optLong("backupTimestamp", System.currentTimeMillis())
                    )
                )
                // Restore Power save whitelist if applicable
                val isPowerWhite = backupObj.optBoolean("isPowerWhitelisted", false)
                if (isPowerWhite) {
                    ShizukuManager.exec("dumpsys deviceidle whitelist +$pkg")
                } else {
                    ShizukuManager.exec("dumpsys deviceidle whitelist -$pkg")
                }
            }

            // Save App Entity
            appDao.upsert(
                AppEntity(
                    packageName = pkg,
                    mode = mode,
                    enabled = enabled,
                    delaySeconds = delaySeconds,
                    addedAt = addedAt,
                    blockedCount = blockedCount,
                    lastActionAt = lastActionAt,
                    isFrozenShelf = isFrozenShelf
                )
            )

            // Restore NOBG Service config
            if (enabled) {
                enableNobg(pkg, mode, delaySeconds)
            }

            // Restore Freeze / Disable state
            if (isFrozen) {
                freezePackage(pkg)
            } else {
                unfreezePackage(pkg)
            }

            restoredCount++
        }

        com.nobg.app.widget.FrozenAppsWidgetProvider.updateAllWidgets(context)
        return restoredCount to totalCount
    }

    // --- APP SCREEN STATE PERSISTENCE ---
    fun getLastActiveScreen(): String = prefs.getString("last_active_screen", "LIST") ?: "LIST"
    fun setLastActiveScreen(screen: String) {
        prefs.edit().putString("last_active_screen", screen).apply()
    }



    // --- APP FREEZER SHELF (KỆ ĐÓNG BẰNG ỨNG DỤNG) ---
    val frozenShelfApps: kotlinx.coroutines.flow.Flow<List<AppEntity>> = appDao.observeFrozenShelf()
    suspend fun getFrozenShelfApps(): List<AppEntity> = appDao.getFrozenShelfApps()

    suspend fun freezePackage(pkg: String) {
        ShizukuManager.exec("pm disable-user --user 0 $pkg")
    }

    suspend fun unfreezePackage(pkg: String) {
        ShizukuManager.exec("pm enable $pkg")
    }

    suspend fun unfreezeAndLaunch(context: android.content.Context, pkg: String): Boolean {
        unfreezePackage(pkg)
        kotlinx.coroutines.delay(200)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        return if (launchIntent != null) {
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } else {
            false
        }
    }

    suspend fun toggleAppFrozenShelf(pkg: String, addToShelf: Boolean) {
        val existing = appDao.get(pkg)
        if (existing != null) {
            appDao.upsert(existing.copy(isFrozenShelf = addToShelf))
        } else {
            appDao.upsert(AppEntity(packageName = pkg, enabled = false, isFrozenShelf = addToShelf))
        }
        if (addToShelf) {
            freezePackage(pkg)
        } else {
            unfreezePackage(pkg)
        }
        com.nobg.app.widget.FrozenAppsWidgetProvider.updateAllWidgets(context)
    }

    suspend fun freezeAllShelfApps() {
        val apps = appDao.getFrozenShelfApps()
        for (app in apps) {
            freezePackage(app.packageName)
        }
        com.nobg.app.widget.FrozenAppsWidgetProvider.updateAllWidgets(context)
    }

    suspend fun unfreezeAllShelfApps() {
        val apps = appDao.getFrozenShelfApps()
        for (app in apps) {
            unfreezePackage(app.packageName)
        }
        com.nobg.app.widget.FrozenAppsWidgetProvider.updateAllWidgets(context)
    }

    // --- NOTIFICATION READ CONFIG ---
    fun observeNotifReadConfigs(): kotlinx.coroutines.flow.Flow<List<NotificationReadConfigEntity>> = notificationReadDao.observeAll()

    suspend fun getNotifReadConfig(pkg: String, userId: Int = 0): NotificationReadConfigEntity? =
        notificationReadDao.get(pkg, userId)

    suspend fun getNotifReadConfigById(id: String): NotificationReadConfigEntity? =
        notificationReadDao.getById(id)

    suspend fun getAllEnabledNotifRead(): List<NotificationReadConfigEntity> = notificationReadDao.getAllEnabled()

    suspend fun setNotifReadConfig(
        pkg: String,
        isEnabled: Boolean,
        readMode: NotificationReadMode,
        keywordFilter: String = "",
        userId: Int = 0
    ) {
        notificationReadDao.upsert(
            NotificationReadConfigEntity(
                id = NotificationReadConfigEntity.makeId(pkg, userId),
                packageName = pkg,
                userId = userId,
                isEnabled = isEnabled,
                readMode = readMode,
                keywordFilter = keywordFilter
            )
        )
    }

    suspend fun deleteNotifReadConfig(pkg: String, userId: Int = 0) =
        notificationReadDao.delete(NotificationReadConfigEntity.makeId(pkg, userId))

    suspend fun setAllNotifRead(enabled: Boolean) {
        val all = notificationReadDao.getAll()
        for (cfg in all) {
            notificationReadDao.upsert(cfg.copy(isEnabled = enabled))
        }
    }

    suspend fun setAllNotifReadMode(mode: NotificationReadMode) {
        val all = notificationReadDao.getAll()
        for (cfg in all) {
            notificationReadDao.upsert(cfg.copy(readMode = mode))
        }
    }

    // --- SELECTED BLUETOOTH DEVICES ---
    fun observeSelectedBtDevices(): kotlinx.coroutines.flow.Flow<List<SelectedBluetoothDeviceEntity>> = bluetoothDeviceDao.observeAll()

    suspend fun getSelectedBtDevices(): List<SelectedBluetoothDeviceEntity> = bluetoothDeviceDao.getSelected()

    suspend fun getAllBtDevices(): List<SelectedBluetoothDeviceEntity> = bluetoothDeviceDao.getAll()

    suspend fun upsertBtDevice(address: String, name: String, isSelected: Boolean) {
        bluetoothDeviceDao.upsert(SelectedBluetoothDeviceEntity(address = address, name = name, isSelected = isSelected))
    }

    suspend fun deleteBtDevice(address: String) = bluetoothDeviceDao.delete(address)

    suspend fun clearAllBtDevices() = bluetoothDeviceDao.deleteAll()

    // --- NOTIFICATION READ PREFS ---
    fun isNotifReadGlobalEnabled(): Boolean = prefs.getBoolean(KEY_NOTIF_READ_GLOBAL_ENABLED, false)

    fun setNotifReadGlobalEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_READ_GLOBAL_ENABLED, enabled).apply()
    }

    fun isNotifReadOnlySelectedBt(): Boolean = prefs.getBoolean(KEY_NOTIF_READ_ONLY_BT, false)

    fun setNotifReadOnlySelectedBt(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_READ_ONLY_BT, enabled).apply()
    }

    fun getTtsSpeechRate(): Float = prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f)

    fun setTtsSpeechRate(rate: Float) {
        prefs.edit().putFloat(KEY_TTS_SPEECH_RATE, rate.coerceIn(0.5f, 2.0f)).apply()
    }

    fun getTtsVolume(): Float = prefs.getFloat(KEY_TTS_VOLUME, 1.0f)

    fun setTtsVolume(volume: Float) {
        prefs.edit().putFloat(KEY_TTS_VOLUME, volume.coerceIn(0.0f, 1.0f)).apply()
    }

    fun isNotifReadDuckingEnabled(): Boolean = prefs.getBoolean(KEY_NOTIF_READ_DUCKING, true)

    fun setNotifReadDuckingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIF_READ_DUCKING, enabled).apply()
    }

    fun getTtsPan(): Float = prefs.getFloat(KEY_TTS_PAN, 0.0f)

    fun setTtsPan(pan: Float) {
        prefs.edit().putFloat(KEY_TTS_PAN, pan.coerceIn(-1.0f, 1.0f)).apply()
    }

    fun getTtsPitch(): Float = prefs.getFloat(KEY_TTS_PITCH, 1.0f)

    fun setTtsPitch(pitch: Float) {
        prefs.edit().putFloat(KEY_TTS_PITCH, pitch.coerceIn(0.5f, 2.0f)).apply()
    }

    fun getSmartTimerConfig(): SmartTimerConfig {
        val modeStr = prefs.getString("smart_timer_mode", SmartTimerMode.CLOCK_TIME.name)
        val mode = try { SmartTimerMode.valueOf(modeStr ?: SmartTimerMode.CLOCK_TIME.name) } catch (_: Exception) { SmartTimerMode.CLOCK_TIME }
        return SmartTimerConfig(
            isRunning = prefs.getBoolean("smart_timer_running", false),
            mode = mode,
            intervalMinutes = prefs.getInt("smart_timer_interval", 2),
            durationMinutes = prefs.getInt("smart_timer_duration", 60),
            autoShutdown = prefs.getBoolean("smart_timer_auto_shutdown", false),
            volume = prefs.getFloat("smart_timer_volume", 1.0f),
            audioDucking = prefs.getBoolean("smart_timer_ducking", true),
            speechRate = prefs.getFloat("smart_timer_speech_rate", 1.1f),
            startTimeMillis = prefs.getLong("smart_timer_start_time", 0L),
            endTimeMillis = prefs.getLong("smart_timer_end_time", 0L)
        )
    }

    fun saveSmartTimerConfig(config: SmartTimerConfig) {
        prefs.edit()
            .putBoolean("smart_timer_running", config.isRunning)
            .putString("smart_timer_mode", config.mode.name)
            .putInt("smart_timer_interval", config.intervalMinutes)
            .putInt("smart_timer_duration", config.durationMinutes)
            .putBoolean("smart_timer_auto_shutdown", config.autoShutdown)
            .putFloat("smart_timer_volume", config.volume)
            .putBoolean("smart_timer_ducking", config.audioDucking)
            .putFloat("smart_timer_speech_rate", config.speechRate)
            .putLong("smart_timer_start_time", config.startTimeMillis)
            .putLong("smart_timer_end_time", config.endTimeMillis)
            .apply()
    }

    // --- AI (GEMINI) PREFS ---
    fun isAiEnabled(): Boolean = prefs.getBoolean(KEY_AI_ENABLED, false)

    fun setAiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
    }

    fun getAiApiKey(): String = prefs.getString(KEY_AI_API_KEY, "") ?: ""

    fun setAiApiKey(key: String) {
        prefs.edit().putString(KEY_AI_API_KEY, key.trim()).apply()
    }

    fun getAiModel(): String = prefs.getString(KEY_AI_MODEL, GeminiApiClient.DEFAULT_MODEL)
        ?: GeminiApiClient.DEFAULT_MODEL

    fun setAiModel(model: String) {
        prefs.edit().putString(KEY_AI_MODEL, model.trim()).apply()
    }

    fun isAiSummaryEnabled(): Boolean = prefs.getBoolean(KEY_AI_SUMMARY_ENABLED, false)

    fun setAiSummaryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_SUMMARY_ENABLED, enabled).apply()
    }

    fun isAiFilterEnabled(): Boolean = prefs.getBoolean(KEY_AI_FILTER_ENABLED, false)

    fun setAiFilterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_FILTER_ENABLED, enabled).apply()
    }

    fun getAiFilterStrictness(): Float = prefs.getFloat(KEY_AI_FILTER_STRICTNESS, 0.5f)

    fun setAiFilterStrictness(strictness: Float) {
        prefs.edit().putFloat(KEY_AI_FILTER_STRICTNESS, strictness.coerceIn(0.0f, 1.0f)).apply()
    }

    fun isAiFullyConfigured(): Boolean = isAiEnabled() && getAiApiKey().isNotBlank()
}
