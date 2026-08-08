package com.nobg.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobg.app.data.AppEntity
import com.nobg.app.data.BackgroundPowerState
import com.nobg.app.data.NobgMode
import com.nobg.app.data.NobgRepository
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.nobg.app.shell.PrivilegedShell
import com.nobg.app.shell.AdbDaemonInstaller

data class AppUiModel(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val config: AppEntity?,
    val hasBackup: Boolean,
    val powerState: BackgroundPowerState = BackgroundPowerState.UNKNOWN,
    val isHidden: Boolean = false,
    val isDisabled: Boolean = false,
    val isFrozenShelf: Boolean = false,
    val installTimeMs: Long = 0L,
    val appSizeBytes: Long = 0L
)

enum class UserSystemFilterOption(val label: String) {
    USER_ONLY("Chỉ App User"),
    SYSTEM_ONLY("Chỉ App Hệ thống")
}

enum class DisabledFilterOption(val label: String) {
    DISABLED_ONLY("Chỉ Đã vô hiệu hóa"),
    ACTIVE_ONLY("Chỉ Chưa vô hiệu hóa")
}

enum class PowerStateFilterOption(val label: String) {
    RESTRICTED_ONLY("🔴 Hạn chế"),
    OPTIMIZED_ONLY("🟡 Tối ưu"),
    UNRESTRICTED_ONLY("🟢 Không hạn chế")
}

enum class NobgStateFilterOption(val label: String) {
    ENABLED_ONLY("Chỉ Đang bật NOBG"),
    DISABLED_ONLY("Chỉ Tắt NOBG")
}

enum class FrozenShelfFilterOption(val label: String) {
    FROZEN_SHELF_ONLY("🧊 Chỉ Kệ Đóng Bằng"),
    NOT_FROZEN_SHELF("Chỉ Chưa thêm Kệ")
}

enum class AppSortOption(val label: String) {
    NAME_ASC("Tên app (A-Z)"),
    INSTALL_TIME_DESC("Mới cài đặt / Cập nhật"),
    INSTALL_TIME_ASC("Cũ nhất"),
    SIZE_DESC("Dung lượng (Lớn nhất)"),
    SIZE_ASC("Dung lượng (Nhỏ nhất)")
}

enum class HiddenFilterOption(val label: String) {
    EXCLUDE_HIDDEN("Chưa ẩn (Mặc định)"),
    SHOW_HIDDEN_ONLY("Chỉ App đã ẩn"),
    ALL("Tất cả (Bao gồm app ẩn)")
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NobgRepository(app)
    private val pm: PackageManager = app.packageManager
    private val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val prefs = app.getSharedPreferences("nobg_prefs", Context.MODE_PRIVATE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Multi-Select Filter Flows
    val userSystemFilters = MutableStateFlow<Set<UserSystemFilterOption>>(emptySet())
    val disabledFilters = MutableStateFlow<Set<DisabledFilterOption>>(emptySet())
    val powerStateFilters = MutableStateFlow<Set<PowerStateFilterOption>>(emptySet())
    val nobgStateFilters = MutableStateFlow<Set<NobgStateFilterOption>>(emptySet())
    val frozenShelfFilters = MutableStateFlow<Set<FrozenShelfFilterOption>>(emptySet())
    val hiddenFilter = MutableStateFlow(HiddenFilterOption.EXCLUDE_HIDDEN)
    val sortOption = MutableStateFlow(AppSortOption.NAME_ASC)

    val isRefreshing = MutableStateFlow(false)

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _hiddenPackages = MutableStateFlow<Set<String>>(loadHiddenPackages())
    val hiddenPackages: StateFlow<Set<String>> = _hiddenPackages

    private val _disabledPackages = MutableStateFlow<Set<String>>(emptySet())
    val disabledPackages: StateFlow<Set<String>> = _disabledPackages

    private val _installedApps = MutableStateFlow<List<AppUiModel>>(emptyList())
    private val _powerStatesMap = MutableStateFlow<Map<String, BackgroundPowerState>>(emptyMap())

    val shizukuReady = MutableStateFlow(false)
    val shellReady = MutableStateFlow(false)
    val activeBackend = MutableStateFlow(PrivilegedShell.Backend.NONE)

    private val _enrichedApps: Flow<List<AppUiModel>> = combine(
        _installedApps, repo.observeApps(), _powerStatesMap, _hiddenPackages, _disabledPackages
    ) { installed, configs, powerStates, hiddenSet, disabledSet ->
        val configMap = configs.associateBy { it.packageName }
        installed.map { model ->
            val pState = powerStates[model.packageName] ?: BackgroundPowerState.OPTIMIZED
            val isHidden = model.packageName in hiddenSet
            val isDisabled = model.packageName in disabledSet
            val isFrozen = configMap[model.packageName]?.isFrozenShelf == true
            model.copy(
                config = configMap[model.packageName],
                powerState = pState,
                isHidden = isHidden,
                isDisabled = isDisabled,
                isFrozenShelf = isFrozen
            )
        }
    }

    private val _filteredTypeAndDisabled = combine(
        _enrichedApps, _searchQuery, userSystemFilters, disabledFilters
    ) { apps, query, typeFilters, disabledF ->
        apps.filter { model ->
            val matchesQuery = query.isBlank() ||
                model.label.contains(query, ignoreCase = true) ||
                model.packageName.contains(query, ignoreCase = true)

            val matchesType = if (typeFilters.isEmpty()) true else {
                (UserSystemFilterOption.USER_ONLY in typeFilters && !model.isSystemApp) ||
                (UserSystemFilterOption.SYSTEM_ONLY in typeFilters && model.isSystemApp)
            }

            val matchesDisabled = if (disabledF.isEmpty()) true else {
                (DisabledFilterOption.DISABLED_ONLY in disabledF && model.isDisabled) ||
                (DisabledFilterOption.ACTIVE_ONLY in disabledF && !model.isDisabled)
            }

            matchesQuery && matchesType && matchesDisabled
        }
    }

    val appList: StateFlow<List<AppUiModel>> = combine(
        _filteredTypeAndDisabled, powerStateFilters, nobgStateFilters, frozenShelfFilters, sortOption
    ) { apps, powerF, nobgF, shelfF, sortOpt ->
        val hiddenF = hiddenFilter.value
        apps
            .filter { model ->
                val matchesHidden = when (hiddenF) {
                    HiddenFilterOption.EXCLUDE_HIDDEN -> !model.isHidden
                    HiddenFilterOption.SHOW_HIDDEN_ONLY -> model.isHidden
                    HiddenFilterOption.ALL -> true
                }

                val matchesPower = if (powerF.isEmpty()) true else {
                    (PowerStateFilterOption.RESTRICTED_ONLY in powerF && model.powerState == BackgroundPowerState.RESTRICTED) ||
                    (PowerStateFilterOption.OPTIMIZED_ONLY in powerF && model.powerState == BackgroundPowerState.OPTIMIZED) ||
                    (PowerStateFilterOption.UNRESTRICTED_ONLY in powerF && model.powerState == BackgroundPowerState.UNRESTRICTED)
                }

                val matchesNobg = if (nobgF.isEmpty()) true else {
                    (NobgStateFilterOption.ENABLED_ONLY in nobgF && model.config?.enabled == true) ||
                    (NobgStateFilterOption.DISABLED_ONLY in nobgF && model.config?.enabled != true)
                }

                val matchesShelf = if (shelfF.isEmpty()) true else {
                    (FrozenShelfFilterOption.FROZEN_SHELF_ONLY in shelfF && model.isFrozenShelf) ||
                    (FrozenShelfFilterOption.NOT_FROZEN_SHELF in shelfF && !model.isFrozenShelf)
                }

                matchesHidden && matchesPower && matchesNobg && matchesShelf
            }
            .sortedWith { a, b ->
                when (sortOpt) {
                    AppSortOption.NAME_ASC -> a.label.lowercase().compareTo(b.label.lowercase())
                    AppSortOption.INSTALL_TIME_DESC -> b.installTimeMs.compareTo(a.installTimeMs)
                    AppSortOption.INSTALL_TIME_ASC -> a.installTimeMs.compareTo(b.installTimeMs)
                    AppSortOption.SIZE_DESC -> b.appSizeBytes.compareTo(a.appSizeBytes)
                    AppSortOption.SIZE_ASC -> a.appSizeBytes.compareTo(b.appSizeBytes)
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeFilterCount: StateFlow<Int> = combine(
        userSystemFilters, disabledFilters, powerStateFilters, nobgStateFilters, frozenShelfFilters
    ) { typeF, disabledF, powerF, nobgF, shelfF ->
        var count = typeF.size + disabledF.size + powerF.size + nobgF.size + shelfF.size
        if (hiddenFilter.value != HiddenFilterOption.EXCLUDE_HIDDEN) count++
        count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        loadInstalledApps()
    }

    private fun loadHiddenPackages(): Set<String> {
        return prefs.getStringSet("hidden_packages", emptySet()) ?: emptySet()
    }

    fun toggleHideApp(packageName: String, hide: Boolean) {
        val current = _hiddenPackages.value.toMutableSet()
        if (hide) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet("hidden_packages", current).apply()
        _hiddenPackages.value = current
    }

    fun reloadAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            loadInstalledApps()
            refreshDisabledPackages()
            refreshPowerStates()
            isRefreshing.value = false
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val models = apps
                .map { info: ApplicationInfo ->
                    val pkgInfo = try { pm.getPackageInfo(info.packageName, 0) } catch (_: Exception) { null }
                    val installTime = pkgInfo?.lastUpdateTime ?: pkgInfo?.firstInstallTime ?: 0L
                    val apkSize = try { java.io.File(info.sourceDir).length() } catch (_: Exception) { 0L }

                    AppUiModel(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = try { pm.getApplicationIcon(info) } catch (e: Exception) { null },
                        isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        config = null,
                        hasBackup = false,
                        powerState = BackgroundPowerState.UNKNOWN,
                        isHidden = false,
                        isDisabled = !info.enabled,
                        installTimeMs = installTime,
                        appSizeBytes = apkSize
                    )
                }
            _installedApps.value = models
            refreshPowerStates()
            refreshDisabledPackages()
        }
    }

    fun refreshDisabledPackages() {
        viewModelScope.launch(Dispatchers.IO) {
            if (PrivilegedShell.isReady()) {
                try {
                    val disabledSet = ShizukuManager.getDisabledPackages()
                    _disabledPackages.value = disabledSet
                } catch (e: Exception) {
                    android.util.Log.e("MainVM", "Error refreshing disabled packages", e)
                }
            }
        }
    }

    fun disableApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (PrivilegedShell.isReady()) {
                    val (success, _) = ShizukuManager.disablePackageResult(packageName)
                    if (!success) {
                        val label = _installedApps.value.find { it.packageName == packageName }?.label ?: packageName
                        _toastEvent.emit("Không thể vô hiệu hóa $label: Ứng dụng này bị hệ thống Android bảo vệ.")
                    }
                    refreshDisabledPackages()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Error disabling $packageName", e)
            }
        }
    }

    fun enableAndLaunchApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (PrivilegedShell.isReady()) {
                    ShizukuManager.enablePackage(packageName)
                    ShizukuManager.launchPackage(packageName)
                    refreshDisabledPackages()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Error enabling/launching $packageName", e)
            }
        }
    }

    fun clearAllFilters() {
        userSystemFilters.value = emptySet()
        disabledFilters.value = emptySet()
        powerStateFilters.value = emptySet()
        nobgStateFilters.value = emptySet()
        frozenShelfFilters.value = emptySet()
        hiddenFilter.value = HiddenFilterOption.EXCLUDE_HIDDEN
        setSearchQuery("")
    }

    fun refreshPowerStates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isShizukuAvailable = PrivilegedShell.isReady()
                val currentApps = _installedApps.value
                val pkgList = currentApps.map { it.packageName }

                if (isShizukuAvailable) {
                    val resultMap = ShizukuManager.getAllAppPowerModes(pkgList)
                    _powerStatesMap.value = resultMap
                } else {
                    val resultMap = mutableMapOf<String, BackgroundPowerState>()
                    for (app in currentApps) {
                        val isIgnoringOpt = powerManager.isIgnoringBatteryOptimizations(app.packageName)
                        resultMap[app.packageName] = if (isIgnoringOpt) BackgroundPowerState.UNRESTRICTED else BackgroundPowerState.OPTIMIZED
                    }
                    _powerStatesMap.value = resultMap
                }
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Error refreshing power states", e)
            }
        }
        refreshDisabledPackages()
    }

    fun loadPowerStateForApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isShizukuAvailable = PrivilegedShell.isReady()
                val state = if (isShizukuAvailable) {
                    ShizukuManager.getPowerMode(packageName)
                } else {
                    if (powerManager.isIgnoringBatteryOptimizations(packageName)) BackgroundPowerState.UNRESTRICTED else BackgroundPowerState.OPTIMIZED
                }
                _powerStatesMap.value = _powerStatesMap.value + (packageName to state)
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Error loading power state for $packageName", e)
            }
        }
    }

    fun changePowerState(packageName: String, newState: BackgroundPowerState) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Immediately update UI state
            _powerStatesMap.value = _powerStatesMap.value + (packageName to newState)
            try {
                if (PrivilegedShell.isReady()) {
                    repo.backupIfNeeded(packageName)
                    ShizukuManager.setPowerMode(packageName, newState)
                    delay(250)
                    loadPowerStateForApp(packageName)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Error changing power state for $packageName", e)
            }
        }
    }

    fun openAppInfoSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun openSystemBatterySettings(context: Context, packageName: String) {
        try {
            val intent = Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
            }
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun toggleNobg(pkg: String, enable: Boolean, mode: NobgMode, delaySeconds: Int) {
        viewModelScope.launch {
            if (enable) repo.enableNobg(pkg, mode, delaySeconds) else repo.disableNobg(pkg)
        }
    }

    fun changeMode(pkg: String, mode: NobgMode) {
        viewModelScope.launch { repo.changeMode(pkg, mode) }
    }

    fun changeDelay(pkg: String, seconds: Int) {
        viewModelScope.launch { repo.changeDelay(pkg, seconds) }
    }

    fun resetApp(pkg: String) {
        viewModelScope.launch { repo.resetApp(pkg) }
    }

    fun resetAll() {
        viewModelScope.launch { repo.resetAll() }
    }

    fun launchDisabledApp(pkg: String) {
        enableAndLaunchApp(pkg)
    }

    fun refreshShizukuStatus() {
        refreshShellStatus()
    }

    fun refreshShellStatus() {
        val shizukuOk = ShizukuManager.isShizukuRunning() &&
            ShizukuManager.hasPermission() &&
            ShizukuManager.isServiceBound()
        shizukuReady.value = shizukuOk
        
        if (!shizukuOk) {
            PrivilegedShell.tryConnectAdb()
        }
        
        shellReady.value = PrivilegedShell.isReady()
        activeBackend.value = PrivilegedShell.activeBackend.value
        refreshPowerStates()
    }

    fun toggleFrozenShelf(packageName: String, addToShelf: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.toggleAppFrozenShelf(packageName, addToShelf)
            refreshDisabledPackages()
        }
    }

    fun freezeAppImmediately(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.toggleAppFrozenShelf(packageName, true)
                ShizukuManager.forceStop(packageName)
                ShizukuManager.disablePackage(packageName)
                refreshDisabledPackages()
            } catch (e: Exception) {
                android.util.Log.e("MainVM", "Error freezing $packageName", e)
                _toastEvent.emit("Không thể đóng băng $packageName: ${e.message ?: "lỗi hệ thống"}")
            }
        }
    }

    fun connectAdbDaemon() {
        viewModelScope.launch(Dispatchers.IO) {
            PrivilegedShell.tryConnectAdb()
            shellReady.value = PrivilegedShell.isReady()
            activeBackend.value = PrivilegedShell.activeBackend.value
        }
    }
}
