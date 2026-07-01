package com.nobg.app.shizuku

import java.io.BufferedReader
import java.io.InputStreamReader

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

    override fun exec(cmd: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream))
                .readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}
