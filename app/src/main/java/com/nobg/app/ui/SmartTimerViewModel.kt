package com.nobg.app.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobg.app.data.NobgRepository
import com.nobg.app.data.SmartTimerConfig
import com.nobg.app.data.SmartTimerMode
import com.nobg.app.service.SmartTimerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SmartTimerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = NobgRepository(application)

    private val _configState = MutableStateFlow(repo.getSmartTimerConfig())
    val configState: StateFlow<SmartTimerConfig> = _configState.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    init {
        refreshState()
        viewModelScope.launch {
            while (true) {
                val current = repo.getSmartTimerConfig()
                _configState.value = current.copy(isRunning = SmartTimerService.isServiceRunning)

                if (SmartTimerService.isServiceRunning && current.startTimeMillis > 0) {
                    val elapsed = (System.currentTimeMillis() - current.startTimeMillis) / 1000L
                    _elapsedSeconds.value = elapsed.coerceAtLeast(0L)
                } else {
                    _elapsedSeconds.value = 0L
                }

                delay(1000L)
            }
        }
    }

    fun refreshState() {
        val current = repo.getSmartTimerConfig()
        _configState.value = current.copy(isRunning = SmartTimerService.isServiceRunning)
    }

    fun setMode(mode: SmartTimerMode) {
        val newCfg = _configState.value.copy(mode = mode)
        saveAndEmit(newCfg)
    }

    fun setInterval(intervalMinutes: Int) {
        val newCfg = _configState.value.copy(intervalMinutes = intervalMinutes.coerceAtLeast(1))
        saveAndEmit(newCfg)
    }

    fun setDuration(durationMinutes: Int) {
        val newCfg = _configState.value.copy(durationMinutes = durationMinutes.coerceAtLeast(0))
        saveAndEmit(newCfg)
    }

    fun setAutoShutdown(autoShutdown: Boolean) {
        val newCfg = _configState.value.copy(autoShutdown = autoShutdown)
        saveAndEmit(newCfg)
    }

    fun setVolume(volume: Float) {
        val newCfg = _configState.value.copy(volume = volume.coerceIn(0.0f, 1.0f))
        saveAndEmit(newCfg)
    }

    fun setAudioDucking(ducking: Boolean) {
        val newCfg = _configState.value.copy(audioDucking = ducking)
        saveAndEmit(newCfg)
    }

    fun setSpeechRate(rate: Float) {
        val newCfg = _configState.value.copy(speechRate = rate.coerceIn(0.5f, 2.0f))
        saveAndEmit(newCfg)
    }

    fun startTimer() {
        val app = getApplication<Application>()
        val updated = _configState.value.copy(
            isRunning = true,
            startTimeMillis = System.currentTimeMillis()
        )
        saveAndEmit(updated)

        val intent = Intent(app, SmartTimerService::class.java).apply {
            action = SmartTimerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= 26) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    fun stopTimer() {
        val app = getApplication<Application>()
        val updated = _configState.value.copy(isRunning = false)
        saveAndEmit(updated)

        val intent = Intent(app, SmartTimerService::class.java).apply {
            action = SmartTimerService.ACTION_STOP
        }
        app.startService(intent)
    }

    fun applyPreset(mode: SmartTimerMode, durationMins: Int, intervalMins: Int, autoShutdown: Boolean) {
        val updated = _configState.value.copy(
            mode = mode,
            durationMinutes = durationMins,
            intervalMinutes = intervalMins,
            autoShutdown = autoShutdown,
            isRunning = true,
            startTimeMillis = System.currentTimeMillis()
        )
        saveAndEmit(updated)
        startTimer()
    }

    private fun saveAndEmit(config: SmartTimerConfig) {
        _configState.value = config
        repo.saveSmartTimerConfig(config)
    }
}
