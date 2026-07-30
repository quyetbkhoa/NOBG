package com.nobg.app.ui

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobg.app.data.NobgRepository
import com.nobg.app.data.NotificationReadConfigEntity
import com.nobg.app.data.NotificationReadMode
import com.nobg.app.data.SelectedBluetoothDeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class NotifReadAppUiModel(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isEnabled: Boolean,
    val readMode: NotificationReadMode
)

data class BluetoothDeviceUiModel(
    val address: String,
    val name: String,
    val isSelected: Boolean,
    val isConnected: Boolean
)

class NotificationReadViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NobgRepository(app)

    // --- App list ---
    private val _apps = MutableStateFlow<List<NotifReadAppUiModel>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredApps: StateFlow<List<NotifReadAppUiModel>> = combine(_apps, _searchQuery) { apps, query ->
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Bluetooth devices ---
    private val _btDevices = MutableStateFlow<List<BluetoothDeviceUiModel>>(emptyList())
    val btDevices: StateFlow<List<BluetoothDeviceUiModel>> = _btDevices.asStateFlow()

    // --- Global settings ---
    private val _isGlobalEnabled = MutableStateFlow(false)
    val isGlobalEnabled: StateFlow<Boolean> = _isGlobalEnabled.asStateFlow()

    private val _isOnlySelectedBt = MutableStateFlow(false)
    val isOnlySelectedBt: StateFlow<Boolean> = _isOnlySelectedBt.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _isNotifListenerEnabled = MutableStateFlow(false)
    val isNotifListenerEnabled: StateFlow<Boolean> = _isNotifListenerEnabled.asStateFlow()

    init {
        _isGlobalEnabled.value = repo.isNotifReadGlobalEnabled()
        _isOnlySelectedBt.value = repo.isNotifReadOnlySelectedBt()
        _speechRate.value = repo.getTtsSpeechRate()
        checkNotifListenerPermission()
        loadUserApps()
        loadBluetoothDevices()
    }

    fun checkNotifListenerPermission() {
        val ctx = getApplication<Application>()
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(ctx)
        _isNotifListenerEnabled.value = enabledPackages.contains(ctx.packageName)
    }

    private fun loadUserApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val pm = ctx.packageManager
            val installedApps = pm.getInstalledApplications(0)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .filter { it.packageName != ctx.packageName }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

            val configs = repo.observeNotifReadConfigs().first()
            val configMap = configs.associateBy { it.packageName }

            val models = installedApps.map { appInfo ->
                val cfg = configMap[appInfo.packageName]
                NotifReadAppUiModel(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null },
                    isEnabled = cfg?.isEnabled ?: false,
                    readMode = cfg?.readMode ?: NotificationReadMode.FULL_CONTENT
                )
            }
            _apps.value = models
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

                // Get currently connected devices
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
            repo.setNotifReadConfig(pkg, enabled, mode)
            reloadAppConfig(pkg)
        }
    }

    fun setAppReadMode(pkg: String, mode: NotificationReadMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repo.getNotifReadConfig(pkg)
            val enabled = existing?.isEnabled ?: true
            repo.setNotifReadConfig(pkg, enabled, mode)
            reloadAppConfig(pkg)
        }
    }

    private suspend fun reloadAppConfig(pkg: String) {
        val cfg = repo.getNotifReadConfig(pkg)
        _apps.value = _apps.value.map {
            if (it.packageName == pkg) {
                it.copy(
                    isEnabled = cfg?.isEnabled ?: false,
                    readMode = cfg?.readMode ?: NotificationReadMode.FULL_CONTENT
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

    fun toggleBtDeviceSelected(addr: String, name: String, selected: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.upsertBtDevice(addr, name, selected)
            // Update UI list directly
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
                repo.setNotifReadConfig(app.packageName, true, mode)
            }
            _apps.value = _apps.value.map { it.copy(isEnabled = true) }
        }
    }

    fun disableAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            for (app in _apps.value) {
                val existing = repo.getNotifReadConfig(app.packageName)
                val mode = existing?.readMode ?: NotificationReadMode.FULL_CONTENT
                repo.setNotifReadConfig(app.packageName, false, mode)
            }
            _apps.value = _apps.value.map { it.copy(isEnabled = false) }
        }
    }

    fun setAllReadMode(mode: NotificationReadMode) {
        viewModelScope.launch(Dispatchers.IO) {
            for (app in _apps.value) {
                val existing = repo.getNotifReadConfig(app.packageName)
                val enabled = existing?.isEnabled ?: app.isEnabled
                repo.setNotifReadConfig(app.packageName, enabled, mode)
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
                testTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "test_tts")
            }
        }
    }
}
