package com.nobg.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.nobg.app.service.MonitorService
import com.nobg.app.shizuku.ShizukuManager
import com.nobg.app.ui.AppListScreen
import com.nobg.app.ui.BatteryStatsScreen
import com.nobg.app.ui.BatteryStatsViewModel
import com.nobg.app.ui.MainViewModel
import com.nobg.app.ui.QuickSettingsScreen
import com.nobg.app.ui.SettingsScreen
import com.nobg.app.ui.theme.NobgTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val batteryStatsViewModel: BatteryStatsViewModel by viewModels()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ShizukuManager.bindUserService()
            lifecycleScope.launch {
                ShizukuManager.grantUsageStatsAccessToSelf(this@MainActivity)
                startMonitorService()
                viewModel.refreshShizukuStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)

        // Always start service on launch
        lifecycleScope.launch {
            if (ShizukuManager.isShizukuRunning() && ShizukuManager.hasPermission()) {
                ShizukuManager.bindUserService()
                ShizukuManager.grantUsageStatsAccessToSelf(this@MainActivity)
            }
            startMonitorService()
        }

        setContent {
            NobgTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf("LIST") }

                    when (currentScreen) {
                        "SETTINGS" -> SettingsScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "LIST" }
                        )
                        "BATTERY_STATS" -> BatteryStatsScreen(
                            viewModel = batteryStatsViewModel,
                            onBack = { currentScreen = "LIST" }
                        )
                        "QUICK_SETTINGS" -> QuickSettingsScreen(
                            onBack = { currentScreen = "LIST" }
                        )
                        else -> AppListScreen(
                            viewModel = viewModel,
                            onOpenSettings = { currentScreen = "SETTINGS" },
                            onOpenBatteryStats = { currentScreen = "BATTERY_STATS" },
                            onOpenQuickSettings = { currentScreen = "QUICK_SETTINGS" }
                        )
                    }
                }
            }
        }
    }

    private fun startMonitorService() {
        val intent = Intent(this, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
