package com.nobg.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Ops we manage per app. These map 1:1 to `appops set <pkg> <op> <mode>`.
 */
object AppOps {
    const val RUN_IN_BACKGROUND = "RUN_IN_BACKGROUND"
    const val RUN_ANY_IN_BACKGROUND = "RUN_ANY_IN_BACKGROUND"
    const val POST_NOTIFICATION = "POST_NOTIFICATION"
    const val START_FOREGROUND = "START_FOREGROUND"
    val ALL = listOf(RUN_IN_BACKGROUND, RUN_ANY_IN_BACKGROUND, POST_NOTIFICATION, START_FOREGROUND)
}

object ShizukuManager {

    private const val USER_SERVICE_TAG = "nobg-user-service"

    private var userService: IUserService? = null
    private var binding = false

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName("com.nobg.app", UserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shizuku")
            .debuggable(false)
            .version(1)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            userService = if (binder != null && binder.pingBinder()) {
                IUserService.Stub.asInterface(binder)
            } else null
            binding = false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
        }
    }

    fun isShizukuInstalled(): Boolean = try {
        Shizuku.pingBinder()
        true
    } catch (e: Throwable) {
        false
    }

    fun isShizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (_: Throwable) {
        }
    }

    /** Binds the shell-privileged remote service. Call after permission granted. */
    fun bindUserService() {
        if (userService != null || binding) return
        binding = true
        Shizuku.bindUserService(serviceArgs, connection)
    }

    fun unbindUserService() {
        try {
            Shizuku.unbindUserService(serviceArgs, connection, true)
        } catch (_: Throwable) {
        }
        userService = null
    }

    fun isServiceBound(): Boolean = userService != null

    /** Low level: run any shell command as shell UID. */
    suspend fun exec(cmd: String): String = suspendCancellableCoroutine { cont ->
        try {
            val svc = userService
            if (svc == null) {
                cont.resume("ERROR: service not bound")
                return@suspendCancellableCoroutine
            }
            val result = svc.exec(cmd)
            cont.resume(result ?: "")
        } catch (e: Exception) {
            cont.resume("ERROR: ${e.message}")
        }
    }

    // ---------- High level operations ----------

    suspend fun forceStop(packageName: String) {
        exec("am force-stop $packageName")
    }

    suspend fun disablePackage(packageName: String) {
        exec("pm disable-user --user 0 $packageName")
    }

    suspend fun enablePackage(packageName: String) {
        exec("pm enable $packageName")
    }

    suspend fun isPackageDisabled(packageName: String): Boolean {
        val out = exec("pm list packages -d")
        return out.contains("package:$packageName")
    }

    suspend fun launchPackage(packageName: String) {
        exec("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    suspend fun setAppOp(packageName: String, op: String, allow: Boolean) {
        val mode = if (allow) "allow" else "deny"
        exec("appops set $packageName $op $mode")
    }

    /** Returns "allow" / "deny" / "default" / "ignore" (best-effort parse). */
    suspend fun getAppOp(packageName: String, op: String): String {
        val out = exec("appops get $packageName $op")
        return when {
            out.contains("allow", ignoreCase = true) -> "allow"
            out.contains("deny", ignoreCase = true) -> "deny"
            out.contains("ignore", ignoreCase = true) -> "ignore"
            else -> "default"
        }
    }

    /** Grants our own app access to usage stats, needed for foreground polling. */
    suspend fun grantUsageStatsAccessToSelf(context: Context) {
        exec("appops set ${context.packageName} android:get_usage_stats allow")
    }

    suspend fun hasUsageStatsAccess(context: Context): Boolean {
        val out = exec("appops get ${context.packageName} android:get_usage_stats")
        return out.contains("allow", ignoreCase = true)
    }

    suspend fun getApplicationEnabledState(packageName: String): Int {
        // 0 = COMPONENT_ENABLED_STATE_DEFAULT, 1 = ENABLED, 2 = DISABLED, 3 = DISABLED_USER
        val out = exec("pm list packages -d")
        return if (out.contains("package:$packageName")) 3 else 0
    }
}
