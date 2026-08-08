package com.nobg.app.shizuku

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * This class is instantiated by Shizuku in a separate process that runs
 * with "shell" UID privileges (same as `adb shell`). Because of that,
 * plain ProcessBuilder / Runtime.exec calls made from HERE already have
 * shell-level permissions (am force-stop, pm disable-user, pm enable,
 * appops set, dumpsys, etc.) without needing root or hidden reflection APIs.
 *
 * IMPORTANT: Must have a public no-argument constructor - Shizuku
 * instantiates this via reflection.
 */
class UserService() : IUserService.Stub() {

    companion object {
        private const val TIMEOUT_SECONDS = 10L
    }

    override fun exec(cmd: String): String {
        val process = try {
            ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            return "ERROR: ${e.message}"
        }

        // Đọc output song song với chờ kết thúc để tránh deadlock khi output nhiều
        val outputFuture = java.util.concurrent.CompletableFuture.supplyAsync {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            } catch (e: Exception) {
                ""
            }
        }

        return try {
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                try {
                    process.waitFor(2, TimeUnit.SECONDS)
                } catch (_: Exception) {}
                if (process.isAlive) process.destroyForcibly()
                val partial = try { outputFuture.get(1, TimeUnit.SECONDS) } catch (_: Exception) { "" }
                "ERROR: timeout after $TIMEOUT_SECONDS seconds".let { err ->
                    if (partial.isBlank()) err else "$err\n$partial"
                }
            } else {
                try { outputFuture.get(2, TimeUnit.SECONDS) } catch (_: Exception) { "" }
            }.trim()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}
