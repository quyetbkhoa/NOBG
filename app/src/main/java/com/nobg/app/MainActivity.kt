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
import com.nobg.app.ui.SettingsScreen
import com.nobg.app.ui.MainViewModel
import com.nobg.app.ui.StatsScreen
import com.nobg.app.ui.StatsViewModel
import com.nobg.app.ui.GlobalStatsScreen
import com.nobg.app.ui.GlobalStatsViewModel
import com.nobg.app.ui.theme.NobgTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val statsViewModel: StatsViewModel by viewModels()
    private val globalStatsViewModel: GlobalStatsViewModel by viewModels()

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

        // Always try to start service on launch
        lifecycleScope.launch {
            if (ShizukuManager.isShizukuRunning() && ShizukuManager.hasPermission()) {
                ShizukuManager.grantUsageStatsAccessToSelf(this@MainActivity)
            }
            startMonitorService()
        }

        setContent {
            NobgTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf("LIST") }

                    if (currentScreen == "SETTINGS") {
                        SettingsScreen(viewModel = viewModel, onBack = { currentScreen = "LIST" })
                    } else if (currentScreen == "STATS") {
                        StatsScreen(viewModel = statsViewModel, onBack = { currentScreen = "LIST" })
                    } else if (currentScreen == "GLOBAL_STATS") {
                        GlobalStatsScreen(viewModel = globalStatsViewModel, onBack = { currentScreen = "LIST" })
                    } else {
                        AppListScreen(
                            viewModel = viewModel,
                            onOpenSettings = { currentScreen = "SETTINGS" },
                            onOpenStats = { currentScreen = "STATS" },
                            onOpenGlobalStats = { currentScreen = "GLOBAL_STATS" }
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
