package com.nobg.app.ui

import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobg.app.data.NobgRepository
import com.nobg.app.data.NotificationReadMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

data class NotifReadAppUiModel(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isEnabled: Boolean,
    val readMode: NotificationReadMode,
    val keywordFilter: String = "",
    val isSecondarySpace: Boolean = false
)

data class BluetoothDeviceUiModel(
    val address: String,
    val name: String,
    val isSelected: Boolean,
    val isConnected: Boolean
)

class NotificationReadViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NobgRepository(app)

    private val _apps = MutableStateFlow<List<NotifReadAppUiModel>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _includeSystemApps = MutableStateFlow(false)
    val includeSystemApps: StateFlow<Boolean> = _includeSystemApps.asStateFlow()

    val filteredApps: StateFlow<List<NotifReadAppUiModel>> = combine(_apps, _searchQuery) { apps, query ->
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true) ||
            it.keywordFilter.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _btDevices = MutableStateFlow<List<BluetoothDeviceUiModel>>(emptyList())
    val btDevices: StateFlow<List<BluetoothDeviceUiModel>> = _btDevices.asStateFlow()

    private val _isGlobalEnabled = MutableStateFlow(false)
    val isGlobalEnabled: StateFlow<Boolean> = _isGlobalEnabled.asStateFlow()

    private val _isOnlySelectedBt = MutableStateFlow(false)
    val isOnlySelectedBt: StateFlow<Boolean> = _isOnlySelectedBt.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _ttsVolume = MutableStateFlow(1.0f)
    val ttsVolume: StateFlow<Float> = _ttsVolume.asStateFlow()

    private val _isNotifListenerEnabled = MutableStateFlow(false)
    val isNotifListenerEnabled: StateFlow<Boolean> = _isNotifListenerEnabled.asStateFlow()

    init {
        _isGlobalEnabled.value = repo.isNotifReadGlobalEnabled()
        _isOnlySelectedBt.value = repo.isNotifReadOnlySelectedBt()
        _speechRate.value = repo.getTtsSpeechRate()
        _ttsVolume.value = repo.getTtsVolume()
        checkNotifListenerPermission()
        loadUserApps()
        loadBluetoothDevices()
    }

    fun checkNotifListenerPermission() {
        val ctx = getApplication<Application>()
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(ctx)
        _isNotifListenerEnabled.value = enabledPackages.contains(ctx.packageName)
    }

    fun toggleIncludeSystemApps(include: Boolean) {
        _includeSystemApps.value = include
        loadUserApps()
    }

    fun loadUserApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val pm = ctx.packageManager
            val showSystem = _includeSystemApps.value

            val appMap = mutableMapOf<String, NotifReadAppUiModel>()
            val configs = repo.observeNotifReadConfigs().first()
            val configMap = configs.associateBy { it.packageName }

            // 1. Quét app mặc định

            val installedApps = pm.getInstalledApplications(0)
                .filter { appInfo ->
                    if (appInfo.packageName == ctx.packageName) return@filter false
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (!showSystem && isSystem) return@filter false
                    true
                }

            installedApps.forEach { appInfo ->
                val cfg = configMap[appInfo.packageName]
                appMap[appInfo.packageName] = NotifReadAppUiModel(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null },
                    isEnabled = cfg?.isEnabled ?: false,
                    readMode = cfg?.readMode ?: NotificationReadMode.FULL_CONTENT,
                    keywordFilter = cfg?.keywordFilter ?: "",
                    isSecondarySpace = false
                )
            }

            // 2. Quét Không gian thứ 2 / Dual Apps / Work Profiles bằng LauncherApps
            try {
                val userManager = ctx.getSystemService(Context.USER_SERVICE) as? UserManager
                val launcherApps = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

                if (userManager != null && launcherApps != null) {
                    val profiles = userManager.userProfiles
                    val myUserHandle = android.os.Process.myUserHandle()

                    for (profile in profiles) {
                        val isSecondary = profile != myUserHandle
                        val activityList = launcherApps.getActivityList(null, profile)
                        for (item in activityList) {
                            val pkg = item.applicationInfo.packageName
                            if (pkg == ctx.packageName) continue

                            val existing = appMap[pkg]
                            val cfg = configMap[pkg]
                            val label = item.label?.toString() ?: pkg

                            appMap[pkg] = NotifReadAppUiModel(
                                packageName = pkg,
                                label = if (isSecondary) "$label (Không gian 2)" else label,
                                icon = existing?.icon ?: try { item.getBadgedIcon(0) } catch (_: Exception) { null },
                                isEnabled = cfg?.isEnabled ?: existing?.isEnabled ?: false,
                                readMode = cfg?.readMode ?: existing?.readMode ?: NotificationReadMode.FULL_CONTENT,
                                keywordFilter = cfg?.keywordFilter ?: existing?.keywordFilter ?: "",
                                isSecondarySpace = isSecondary || existing?.isSecondarySpace == true
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotifReadVM", "Error scanning secondary space apps", e)
            }

            _apps.value = appMap.values.sortedBy { it.label.lowercase() }
        }
    }

    @Suppress("MissingPermission")
    fun loadBluetoothDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val btManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = btManager?.adapter

                if (adapter == null || !adapter.isEnabled) {
                    _btDevices.value = emptyList()
                    return@launch
                }

                val bondedDevices = adapter.bondedDevices ?: emptySet()
                val savedDevices = repo.getAllBtDevices()
                val savedMap = savedDevices.associateBy { it.address }

                val connectedAddresses = mutableSetOf<String>()
                try {
                    val a2dp = btManager.getConnectedDevices(BluetoothProfile.A2DP)
                    connectedAddresses.addAll(a2dp.map { it.address })
                } catch (_: Exception) {}
                try {
                    val headset = btManager.getConnectedDevices(BluetoothProfile.HEADSET)
                    connectedAddresses.addAll(headset.map { it.address })
                } catch (_: Exception) {}

                val models = bondedDevices.map { device ->
                    val saved = savedMap[device.address]
                    BluetoothDeviceUiModel(
                        address = device.address,
                        name = device.name ?: device.address,
                        isSelected = saved?.isSelected ?: false,
                        isConnected = device.address in connectedAddresses
                    )
                }.sortedWith(compareByDescending<BluetoothDeviceUiModel> { it.isConnected }.thenBy { it.name })

                _btDevices.value = models
            } catch (e: Exception) {
                Log.e("NotifReadVM", "Error loading BT devices", e)
                _btDevices.value = emptyList()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleAppEnabled(pkg: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repo.getNotifReadConfig(pkg)
            val mode = existing?.readMode ?: NotificationReadMode.FULL_CONTENT
            val filter = existing?.keywordFilter ?: ""
            repo.setNotifReadConfig(pkg, enabled, mode, filter)
            reloadAppConfig(pkg)
        }
    }

    fun setAppReadMode(pkg: String, mode: NotificationReadMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repo.getNotifReadConfig(pkg)
            val enabled = existing?.isEnabled ?: true
            val filter = existing?.keywordFilter ?: ""
            repo.setNotifReadConfig(pkg, enabled, mode, filter)
            reloadAppConfig(pkg)
        }
    }

    fun setAppKeywordFilter(pkg: String, filter: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repo.getNotifReadConfig(pkg)
            val enabled = existing?.isEnabled ?: true
            val mode = existing?.readMode ?: NotificationReadMode.FULL_CONTENT
            repo.setNotifReadConfig(pkg, enabled, mode, filter)
            reloadAppConfig(pkg)
        }
    }

    private suspend fun reloadAppConfig(pkg: String) {
        val cfg = repo.getNotifReadConfig(pkg)
        _apps.value = _apps.value.map {
            if (it.packageName == pkg) {
                it.copy(
                    isEnabled = cfg?.isEnabled ?: false,
                    readMode = cfg?.readMode ?: NotificationReadMode.FULL_CONTENT,
                    keywordFilter = cfg?.keywordFilter ?: ""
                )
            } else it
        }
    }

    fun toggleGlobalEnabled(enabled: Boolean) {
        repo.setNotifReadGlobalEnabled(enabled)
        _isGlobalEnabled.value = enabled
    }

    fun toggleOnlySelectedBt(enabled: Boolean) {
        repo.setNotifReadOnlySelectedBt(enabled)
        _isOnlySelectedBt.value = enabled
    }

    fun setSpeechRate(rate: Float) {
        repo.setTtsSpeechRate(rate)
        _speechRate.value = rate
    }

    fun setTtsVolume(volume: Float) {
        repo.setTtsVolume(volume)
        _ttsVolume.value = volume
    }

    fun toggleBtDeviceSelected(addr: String, name: String, selected: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.upsertBtDevice(addr, name, selected)
            _btDevices.value = _btDevices.value.map {
                if (it.address == addr) it.copy(isSelected = selected) else it
            }
        }
    }

    fun enableAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            for (app in _apps.value) {
                val existing = repo.getNotifReadConfig(app.packageName)
                val mode = existing?.readMode ?: NotificationReadMode.FULL_CONTENT
                val filter = existing?.keywordFilter ?: ""
                repo.setNotifReadConfig(app.packageName, true, mode, filter)
            }
            _apps.value = _apps.value.map { it.copy(isEnabled = true) }
        }
    }

    fun disableAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            for (app in _apps.value) {
                val existing = repo.getNotifReadConfig(app.packageName)
                val mode = existing?.readMode ?: NotificationReadMode.FULL_CONTENT
                val filter = existing?.keywordFilter ?: ""
                repo.setNotifReadConfig(app.packageName, false, mode, filter)
            }
            _apps.value = _apps.value.map { it.copy(isEnabled = false) }
        }
    }

    fun setAllReadMode(mode: NotificationReadMode) {
        viewModelScope.launch(Dispatchers.IO) {
            for (app in _apps.value) {
                val existing = repo.getNotifReadConfig(app.packageName)
                val enabled = existing?.isEnabled ?: app.isEnabled
                val filter = existing?.keywordFilter ?: ""
                repo.setNotifReadConfig(app.packageName, enabled, mode, filter)
            }
            _apps.value = _apps.value.map { it.copy(readMode = mode) }
        }
    }

    fun testTts(text: String) {
        val ctx = getApplication<Application>()
        var testTts: TextToSpeech? = null
        testTts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val viLocale = Locale("vi", "VN")
                val result = testTts?.setLanguage(viLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    testTts?.setLanguage(Locale.getDefault())
                }
                testTts?.setSpeechRate(_speechRate.value)
                val params = android.os.Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, _ttsVolume.value)
                }
                testTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "test_tts")
            }
        }
    }
}
