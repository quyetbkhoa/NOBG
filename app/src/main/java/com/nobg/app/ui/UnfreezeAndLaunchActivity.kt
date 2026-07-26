package com.nobg.app.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import com.nobg.app.MainActivity
import com.nobg.app.shell.PrivilegedShell
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

        val pkg = intent.getStringExtra("pkg_to_launch") ?: intent.data?.schemeSpecificPart
        val isDeleteMode = intent.getBooleanExtra("is_delete_mode", false)

        if (pkg.isNullOrBlank()) {
            // Clicked empty space in widget -> Open NOBG Freezer Shelf directly
            openFreezerShelfAndFinish()
            return
        }

        if (isDeleteMode) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = com.nobg.app.data.NobgRepository(applicationContext)
                    val appLabel = try {
                        val appInfo = packageManager.getApplicationInfo(pkg, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        pkg
                    }
                    repo.toggleAppFrozenShelf(pkg, false)
                    com.nobg.app.widget.FrozenAppsWidgetProvider.updateAllWidgets(applicationContext)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "❌ Đã xóa $appLabel khỏi Kệ đóng bằng", Toast.LENGTH_SHORT).show()
                        finish()
                        try {
                            overridePendingTransition(0, 0)
                        } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Lỗi xóa app: ${e.message}", Toast.LENGTH_SHORT).show()
                        finish()
                        try {
                            overridePendingTransition(0, 0)
                        } catch (_: Exception) {}
                    }
                }
            }
            return
        }

        // Unfreeze first, THEN launch target app so Android OS accepts the launch intent on 1st click
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Enable package via PrivilegedShell (Shizuku/ADB)
                PrivilegedShell.exec("pm enable $pkg")

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
