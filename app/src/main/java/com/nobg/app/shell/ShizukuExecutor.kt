package com.nobg.app.shell

import com.nobg.app.shizuku.IUserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ShizukuExecutor : ShellExecutor {
    @Volatile
    private var service: IUserService? = null

    fun bind(svc: IUserService) {
        service = svc
    }

    fun unbind() {
        service = null
    }

    override suspend fun exec(cmd: String): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val svc = service
            if (svc == null) {
                continuation.resume("ERROR: Shizuku service not bound")
                return@suspendCancellableCoroutine
            }
            // Khi coroutine bị hủy (timeout/stop), không resume continuation nữa
            continuation.invokeOnCancellation {}
            try {
                val result = svc.exec(cmd)
                if (continuation.isActive) {
                    continuation.resume(result ?: "")
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume("ERROR: ${e.message}")
                }
            }
        }
    }

    override fun isConnected(): Boolean {
        val svc = service ?: return false
        return try {
            svc.asBinder().pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    override fun disconnect() {
        unbind()
    }
}
