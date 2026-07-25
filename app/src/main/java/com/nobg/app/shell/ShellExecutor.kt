package com.nobg.app.shell

/**
 * Abstraction for executing privileged shell commands.
 * Implementations: ShizukuExecutor (via Shizuku IPC) and AdbShellExecutor (via local socket daemon).
 */
interface ShellExecutor {
    suspend fun exec(cmd: String): String
    fun isConnected(): Boolean
    fun disconnect()
}
