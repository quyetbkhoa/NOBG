package com.nobg.app.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import com.nobg.app.MainActivity
import com.nobg.app.service.MonitorService
import com.nobg.app.shizuku.ShizukuManager
import com.nobg.app.widget.FrozenAppsWidgetProvider
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UnfreezeAndLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            overridePendingTransition(0, 0)
        } catch (_: Exception) {}

        if (intent.getBooleanExtra("open_widget_settings", false)) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            startActivity(
                Intent(this, com.nobg.app.widget.WidgetConfigActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
            finish()
            return
        }

        val pkg = intent.getStringExtra("pkg_to_launch") ?: intent.data?.schemeSpecificPart

        if (pkg.isNullOrBlank()) {
            // Clicked empty space in widget -> Open NOBG Freezer Shelf directly
            openFreezerShelfAndFinish()
            return
        }

        ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))

        // Unfreeze first, THEN launch target app so Android OS accepts the launch intent on 1st click
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Enable package via a verified Shizuku/ADB backend.
                val (enabled, error) = ShizukuManager.enablePackageResult(pkg)
                if (!enabled) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Không thể rã đông ứng dụng: $error", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    return@launch
                }
                FrozenAppsWidgetProvider.updateAllWidgets(applicationContext)

                // 2. Resolve launch intent
                val launchIntent = withContext(Dispatchers.Main) {
                    getLaunchIntentForPackageEvenIfDisabled(pkg)
                }

                withContext(Dispatchers.Main) {
                    if (launchIntent != null) {
                        try {
                            startActivity(launchIntent)
                        } catch (e: Exception) {
                            Toast.makeText(applicationContext, "Không thể mở ứng dụng: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(applicationContext, "Không tìm thấy launcher cho $pkg", Toast.LENGTH_SHORT).show()
                    }
                    finish()
                    try {
                        overridePendingTransition(0, 0)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Lỗi xả đóng băng: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                    try {
                        overridePendingTransition(0, 0)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun openFreezerShelfAndFinish() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_screen", "FREEZER_SHELF")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(mainIntent)
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
}
