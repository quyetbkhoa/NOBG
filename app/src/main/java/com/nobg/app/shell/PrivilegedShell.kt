package com.nobg.app.shell

import com.nobg.app.shizuku.IUserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PrivilegedShell {
    enum class Backend { NONE, SHIZUKU, ADB }
    
    private val _activeBackend = MutableStateFlow(Backend.NONE)
    val activeBackend: StateFlow<Backend> = _activeBackend.asStateFlow()
    
    val shizukuExecutor = ShizukuExecutor()
    val adbExecutor = AdbShellExecutor()
    
    private fun activeExecutor(): ShellExecutor? = when (_activeBackend.value) {
        Backend.SHIZUKU -> if (shizukuExecutor.isConnected()) shizukuExecutor else null
        Backend.ADB -> if (adbExecutor.isConnected()) adbExecutor else null
        Backend.NONE -> null
    }
    
    suspend fun exec(cmd: String): String {
        return activeExecutor()?.exec(cmd) ?: "ERROR: no backend connected"
    }
    
    fun isReady(): Boolean = activeExecutor() != null
    
    fun setShizukuBackend(service: IUserService) {
        shizukuExecutor.bind(service)
        if (_activeBackend.value == Backend.NONE) {
            _activeBackend.value = Backend.SHIZUKU
        }
    }
    
    fun clearShizukuBackend() {
        shizukuExecutor.unbind()
        if (_activeBackend.value == Backend.SHIZUKU) {
            if (adbExecutor.isConnected()) {
                _activeBackend.value = Backend.ADB
            } else {
                _activeBackend.value = Backend.NONE
            }
        }
    }
    
    fun tryConnectAdb(): Boolean {
        val connected = adbExecutor.connect()
        if (connected) {
            _activeBackend.value = Backend.ADB
        }
        return connected
    }
    
    fun disconnectAdb() {
        adbExecutor.disconnect()
        if (_activeBackend.value == Backend.ADB) {
            if (shizukuExecutor.isConnected()) {
                _activeBackend.value = Backend.SHIZUKU
            } else {
                _activeBackend.value = Backend.NONE
            }
        }
    }
    
    fun preferBackend(backend: Backend) {
        when (backend) {
            Backend.SHIZUKU -> if (shizukuExecutor.isConnected()) _activeBackend.value = Backend.SHIZUKU
            Backend.ADB -> if (adbExecutor.isConnected()) _activeBackend.value = Backend.ADB
            Backend.NONE -> _activeBackend.value = Backend.NONE
        }
    }
    
    fun getActiveBackendName(): String = when (_activeBackend.value) {
        Backend.SHIZUKU -> "Shizuku"
        Backend.ADB -> "ADB"
        Backend.NONE -> "Không"
    }
}
