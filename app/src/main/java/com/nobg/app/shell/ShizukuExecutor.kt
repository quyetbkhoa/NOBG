package com.nobg.app.shell

import com.nobg.app.shizuku.IUserService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ShizukuExecutor : ShellExecutor {
    private var service: IUserService? = null

    fun bind(svc: IUserService) {
        service = svc
    }

    fun unbind() {
        service = null
    }

    override suspend fun exec(cmd: String): String = suspendCancellableCoroutine { continuation ->
        val svc = service
        if (svc == null) {
            continuation.resume("ERROR: Shizuku service not bound")
            return@suspendCancellableCoroutine
        }
        try {
            val result = svc.exec(cmd)
            continuation.resume(result ?: "")
        } catch (e: Exception) {
            continuation.resume("ERROR: ${e.message}")
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
