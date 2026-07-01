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
import com.nobg.app.ui.ConnectScreen
import com.nobg.app.ui.MainViewModel
import com.nobg.app.ui.SettingsScreen
import com.nobg.app.ui.StatsScreen
import com.nobg.app.ui.StatsViewModel
import com.nobg.app.ui.theme.NobgTheme
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val statsViewModel: StatsViewModel by viewModels()

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

        setContent {
            NobgTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var connected by remember { mutableStateOf(false) }
                    var currentScreen by remember { mutableStateOf("LIST") }

                    if (!connected) {
                        ConnectScreen(
                            onConnected = {
                                connected = true
                                lifecycleScope.launch {
                                    ShizukuManager.grantUsageStatsAccessToSelf(this@MainActivity)
                                    startMonitorService()
                                }
                            },
                            requestPermission = { ShizukuManager.requestPermission(1001) }
                        )
                    } else if (currentScreen == "SETTINGS") {
                        SettingsScreen(viewModel = viewModel, onBack = { currentScreen = "LIST" })
                    } else if (currentScreen == "STATS") {
                        StatsScreen(viewModel = statsViewModel, onBack = { currentScreen = "LIST" })
                    } else {
                        AppListScreen(
                            viewModel = viewModel,
                            onOpenSettings = { currentScreen = "SETTINGS" },
                            onOpenStats = { currentScreen = "STATS" }
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
