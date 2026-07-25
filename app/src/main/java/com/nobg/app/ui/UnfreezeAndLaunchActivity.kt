package com.nobg.app.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.nobg.app.MainActivity
import com.nobg.app.shell.PrivilegedShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UnfreezeAndLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            overridePendingTransition(0, 0)
        } catch (_: Exception) {}

        val pkg = intent.getStringExtra("pkg_to_launch") ?: intent.data?.schemeSpecificPart

        if (pkg.isNullOrBlank()) {
            // Clicked empty space in widget -> Open NOBG Freezer Shelf directly
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                putExtra("open_screen", "FREEZER_SHELF")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(mainIntent)
            finish()
            try {
                overridePendingTransition(0, 0)
            } catch (_: Exception) {}
            return
        }

        // Clicked an actual app icon -> Find launch intent first (even if disabled)
        val launchIntent = getLaunchIntentForPackageEvenIfDisabled(pkg)

        // Asynchronously unfreeze package via PrivilegedShell
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PrivilegedShell.exec("pm enable $pkg")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (launchIntent != null) {
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                fallbackUnfreezeAndLaunch(pkg)
                return
            }
        } else {
            fallbackUnfreezeAndLaunch(pkg)
            return
        }

        finish()
        try {
            overridePendingTransition(0, 0)
        } catch (_: Exception) {}
    }

    private fun getLaunchIntentForPackageEvenIfDisabled(pkg: String): Intent? {
        val standardIntent = packageManager.getLaunchIntentForPackage(pkg)
        if (standardIntent != null) {
            return standardIntent.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        }

        return try {
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(pkg)
            }
            @Suppress("DEPRECATION")
            val resolveInfos = packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_DISABLED_COMPONENTS)
            val activityInfo = resolveInfos.firstOrNull()?.activityInfo
            if (activityInfo != null) {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(activityInfo.packageName, activityInfo.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fallbackUnfreezeAndLaunch(pkg: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PrivilegedShell.exec("pm enable $pkg")
                kotlinx.coroutines.delay(150)
                val intent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                if (intent != null) {
                    startActivity(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                finish()
            }
        }
    }
}
