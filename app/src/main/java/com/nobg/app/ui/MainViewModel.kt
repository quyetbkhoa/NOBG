package com.nobg.app.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobg.app.data.AppEntity
import com.nobg.app.data.NobgMode
import com.nobg.app.data.NobgRepository
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppUiModel(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val config: AppEntity?,
    val hasBackup: Boolean
)

enum class AppFilter { ALL, USER, SYSTEM, ENABLED_ONLY }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NobgRepository(app)
    private val pm: PackageManager = app.packageManager

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _filter = MutableStateFlow(AppFilter.ALL)
    val filter: StateFlow<AppFilter> = _filter

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps

    private val _installedApps = MutableStateFlow<List<AppUiModel>>(emptyList())

    val shizukuReady = MutableStateFlow(false)

    val appList: StateFlow<List<AppUiModel>> = combine(
        _installedApps, repo.observeApps(), _searchQuery, _filter, _showSystemApps
    ) { installed, configs, query, filter, showSystem ->
        val configMap = configs.associateBy { it.packageName }
        val backupPkgs = configs.map { it.packageName }.toSet()

        installed
            .map { it.copy(config = configMap[it.packageName]) }
            .filter { model ->
                val matchesQuery = query.isBlank() ||
                    model.label.contains(query, ignoreCase = true) ||
                    model.packageName.contains(query, ignoreCase = true)
                val matchesSystemToggle = showSystem || !model.isSystemApp
                val matchesFilter = when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> !model.isSystemApp
                    AppFilter.SYSTEM -> model.isSystemApp
                    AppFilter.ENABLED_ONLY -> model.config?.enabled == true
                }
                matchesQuery && matchesSystemToggle && matchesFilter
            }
            .sortedWith(
                compareByDescending<AppUiModel> { it.config?.enabled == true }
                    .thenBy { it.label.lowercase() }
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val models = apps
                .filter { it.packageName != getApplication<Application>().packageName }
                .map { info: ApplicationInfo ->
                    AppUiModel(
                        packageName = info.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = try { pm.getApplicationIcon(info) } catch (e: Exception) { null },
                        isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        config = null,
                        hasBackup = false
                    )
                }
            _installedApps.value = models
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setFilter(f: AppFilter) { _filter.value = f }
    fun setShowSystemApps(show: Boolean) { _showSystemApps.value = show }

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

    /** Used for launching a disabled app from inside NOBG (Disable/Enable mode). */
    fun launchDisabledApp(pkg: String) {
        viewModelScope.launch {
            ShizukuManager.enablePackage(pkg)
            ShizukuManager.launchPackage(pkg)
        }
    }

    fun refreshShizukuStatus() {
        shizukuReady.value = ShizukuManager.isShizukuRunning() &&
            ShizukuManager.hasPermission() &&
            ShizukuManager.isServiceBound()
    }
}
