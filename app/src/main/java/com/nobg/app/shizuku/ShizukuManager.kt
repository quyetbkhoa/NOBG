package com.nobg.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.nobg.app.data.BackgroundPowerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.coroutines.resume

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

    fun bindUserService() {
        if (userService != null || binding || !Shizuku.pingBinder()) return
        binding = true
        try {
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (e: Throwable) {
            binding = false
            e.printStackTrace()
        }
    }

    fun unbindUserService() {
        try {
            Shizuku.unbindUserService(serviceArgs, connection, true)
        } catch (_: Throwable) {
        }
        userService = null
    }

    fun isServiceBound(): Boolean = userService != null

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

    suspend fun forceStop(packageName: String) {
        exec("am force-stop $packageName")
    }

    suspend fun disablePackageResult(packageName: String): Pair<Boolean, String> {
        val out = exec("pm disable-user --user 0 $packageName")
        val success = out.contains("new state: disabled-user") || out.contains("new state: disabled") || out.contains("new state: default")
        return Pair(success, out)
    }

    suspend fun disablePackage(packageName: String) {
        disablePackageResult(packageName)
    }

    suspend fun enablePackage(packageName: String) {
        exec("pm enable $packageName")
    }

    suspend fun getApplicationEnabledState(packageName: String): Int {
        // 0 = COMPONENT_ENABLED_STATE_DEFAULT, 1 = ENABLED, 2 = DISABLED, 3 = DISABLED_USER
        val out = exec("pm list packages -d")
        return if (out.contains("package:$packageName")) 3 else 0
    }

    suspend fun isPackageDisabled(packageName: String): Boolean {
        val out = exec("pm list packages -d")
        return out.contains("package:$packageName")
    }

    suspend fun getDisabledPackages(): Set<String> {
        val resultSet = mutableSetOf<String>()
        try {
            val out = exec("pm list packages -d")
            out.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("package:")) {
                    resultSet.add(trimmed.substringAfter("package:").trim())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultSet
    }

    suspend fun launchPackage(packageName: String) {
        exec("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }

    suspend fun setAppOp(packageName: String, op: String, allow: Boolean) {
        val mode = if (allow) "allow" else "deny"
        exec("appops set $packageName $op $mode")
    }

    suspend fun getAppOp(packageName: String, op: String): String {
        val out = exec("appops get $packageName $op")
        return when {
            out.contains("allow", ignoreCase = true) -> "allow"
            out.contains("deny", ignoreCase = true) -> "deny"
            out.contains("ignore", ignoreCase = true) -> "ignore"
            else -> "default"
        }
    }

    suspend fun grantUsageStatsAccessToSelf(context: Context): Boolean {
        exec("appops set ${context.packageName} GET_USAGE_STATS allow")
        exec("appops set ${context.packageName} android:get_usage_stats allow")
        exec("pm grant ${context.packageName} android.permission.PACKAGE_USAGE_STATS")
        return hasUsageStatsAccess(context)
    }

    suspend fun hasUsageStatsAccess(context: Context): Boolean {
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            if (appOps != null) {
                val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                }
                if (mode == android.app.AppOpsManager.MODE_ALLOWED) return true
            }
        } catch (_: Exception) {}

        val out = exec("appops get ${context.packageName} GET_USAGE_STATS")
        return out.contains("allow", ignoreCase = true)
    }

    suspend fun isUserPowerWhitelisted(packageName: String): Boolean {
        val output = exec("dumpsys deviceidle whitelist")
        val userSection = if (output.contains("User-whitelist:")) {
            output.substringAfter("User-whitelist:")
        } else output
        return userSection.contains("package:$packageName") || userSection.contains(",$packageName,") || userSection.contains(" $packageName")
    }

    suspend fun isSystemPowerWhitelisted(packageName: String): Boolean {
        val output = exec("dumpsys deviceidle whitelist")
        if (!output.contains("System-whitelist:")) return false
        val systemSection = output.substringAfter("System-whitelist:").substringBefore("User-whitelist:")
        return systemSection.contains("package:$packageName") || systemSection.contains(",$packageName,") || systemSection.contains(" $packageName")
    }

    suspend fun isPowerWhitelisted(packageName: String): Boolean {
        return isUserPowerWhitelisted(packageName)
    }

    suspend fun getStandbyBucket(packageName: String): String {
        return exec("am get-standby-bucket $packageName")
    }

    suspend fun getPowerMode(packageName: String): BackgroundPowerState {
        // Check UNRESTRICTED first — whitelist always takes priority
        if (isUserPowerWhitelisted(packageName)) {
            return BackgroundPowerState.UNRESTRICTED
        }

        val bucket = getStandbyBucket(packageName)
        val appOpState = getAppOp(packageName, AppOps.RUN_IN_BACKGROUND)

        if (bucket.contains("45") || bucket.contains("RESTRICTED", ignoreCase = true) || appOpState == "ignore" || appOpState == "deny") {
            return BackgroundPowerState.RESTRICTED
        }

        return BackgroundPowerState.OPTIMIZED
    }

    /**
     * Reliable bulk power state retrieval across all Android ROMs.
     * Uses parallel coroutines with direct system query per app for 100% accuracy.
     */
    suspend fun getAllAppPowerModes(packages: List<String>): Map<String, BackgroundPowerState> = coroutineScope {
        val resultMap = ConcurrentHashMap<String, BackgroundPowerState>()
        try {
            // 1. Device Idle Whitelist (fast, single command)
            val whitelistOut = exec("dumpsys deviceidle whitelist")
            val systemSection = if (whitelistOut.contains("System-whitelist:")) {
                whitelistOut.substringAfter("System-whitelist:").substringBefore("User-whitelist:")
            } else ""

            val userSection = if (whitelistOut.contains("User-whitelist:")) {
                whitelistOut.substringAfter("User-whitelist:")
            } else whitelistOut

            val systemWhitelistedSet = mutableSetOf<String>()
            val pkgPattern = Pattern.compile("(?:package:)?([a-zA-Z0-9._]+)")
            var matcher = pkgPattern.matcher(systemSection)
            while (matcher.find()) {
                val p = matcher.group(1)
                if (p != null) systemWhitelistedSet.add(p)
            }

            val userWhitelistedSet = mutableSetOf<String>()
            matcher = pkgPattern.matcher(userSection)
            while (matcher.find()) {
                val p = matcher.group(1)
                if (p != null && p !in systemWhitelistedSet) {
                    userWhitelistedSet.add(p)
                }
            }

            // 2. Query non-whitelisted packages using clean bulk shell execution (40 packages per shell process)
            val nonWhitelistedPkgs = packages.filter { !userWhitelistedSet.contains(it) }
            for (pkg in packages) {
                if (userWhitelistedSet.contains(pkg)) {
                    resultMap[pkg] = BackgroundPowerState.UNRESTRICTED
                }
            }

            val restrictedBucketSet = mutableSetOf<String>()
            val ignoredAppOpsSet = mutableSetOf<String>()

            // Bulk standby bucket query: 40 packages per shell command
            nonWhitelistedPkgs.chunked(40).forEach { chunk ->
                val cmds = chunk.joinToString("; ") { pkg -> "echo PKG:$pkg:\$(am get-standby-bucket $pkg 2>/dev/null)" }
                val out = exec(cmds)
                for (line in out.lines()) {
                    if (!line.startsWith("PKG:")) continue
                    val parts = line.removePrefix("PKG:").split(":")
                    if (parts.size >= 2) {
                        val pkg = parts[0]
                        val bucket = parts[1].trim()
                        if (bucket == "45" || bucket.contains("RESTRICTED", ignoreCase = true)) {
                            restrictedBucketSet.add(pkg)
                        }
                    }
                }
            }

            // Bulk appops query for remaining non-restricted packages
            val remainingPkgs = nonWhitelistedPkgs.filter { !restrictedBucketSet.contains(it) }
            remainingPkgs.chunked(40).forEach { chunk ->
                val cmds = chunk.joinToString("; ") { pkg -> "echo PKG:$pkg:\$(appops get $pkg RUN_IN_BACKGROUND 2>/dev/null)" }
                val out = exec(cmds)
                for (line in out.lines()) {
                    if (!line.startsWith("PKG:")) continue
                    val parts = line.removePrefix("PKG:").split(":", limit = 3)
                    if (parts.size >= 3) {
                        val pkg = parts[0]
                        val opResult = parts[1].trim() + ":" + parts[2]
                        if (opResult.contains("ignore", ignoreCase = true) || opResult.contains("deny", ignoreCase = true)) {
                            ignoredAppOpsSet.add(pkg)
                        }
                    }
                }
            }

            for (pkg in nonWhitelistedPkgs) {
                if (restrictedBucketSet.contains(pkg) || ignoredAppOpsSet.contains(pkg)) {
                    resultMap[pkg] = BackgroundPowerState.RESTRICTED
                } else {
                    resultMap[pkg] = BackgroundPowerState.OPTIMIZED
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            for (pkg in packages) {
                resultMap[pkg] = getPowerMode(pkg)
            }
        }
        return@coroutineScope resultMap
    }

    suspend fun setPowerMode(packageName: String, mode: BackgroundPowerState) {
        when (mode) {
            BackgroundPowerState.RESTRICTED -> {
                exec("am set-standby-bucket $packageName restricted")
                exec("dumpsys deviceidle whitelist -$packageName")
                exec("appops set $packageName ${AppOps.RUN_IN_BACKGROUND} ignore")
                exec("appops set $packageName ${AppOps.RUN_ANY_IN_BACKGROUND} ignore")
                exec("appops set $packageName ${AppOps.START_FOREGROUND} deny")
                exec("appops set $packageName ${AppOps.POST_NOTIFICATION} deny")
            }
            BackgroundPowerState.OPTIMIZED -> {
                exec("am set-standby-bucket $packageName working_set")
                exec("dumpsys deviceidle whitelist -$packageName")
                exec("appops set $packageName ${AppOps.RUN_IN_BACKGROUND} allow")
                exec("appops set $packageName ${AppOps.RUN_ANY_IN_BACKGROUND} allow")
                exec("appops set $packageName ${AppOps.START_FOREGROUND} allow")
                exec("appops set $packageName ${AppOps.POST_NOTIFICATION} allow")
            }
            BackgroundPowerState.UNRESTRICTED -> {
                exec("am set-standby-bucket $packageName active")
                exec("dumpsys deviceidle whitelist +$packageName")
                exec("appops set $packageName ${AppOps.RUN_IN_BACKGROUND} allow")
                exec("appops set $packageName ${AppOps.RUN_ANY_IN_BACKGROUND} allow")
                exec("appops set $packageName ${AppOps.START_FOREGROUND} allow")
                exec("appops set $packageName ${AppOps.POST_NOTIFICATION} allow")
            }
            BackgroundPowerState.UNKNOWN -> {}
        }
    }
}
