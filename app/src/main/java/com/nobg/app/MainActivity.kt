package com.nobg.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nobg.app.service.MonitorService
import com.nobg.app.shizuku.ShizukuManager
import com.nobg.app.ui.AppListScreen
import com.nobg.app.ui.BatteryStatsScreen
import com.nobg.app.ui.BatteryStatsViewModel
import com.nobg.app.ui.MainViewModel
import com.nobg.app.ui.PermissionOnboardingDialog
import com.nobg.app.ui.SettingsScreen
import com.nobg.app.ui.theme.NobgTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val batteryStatsViewModel: BatteryStatsViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
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

        lifecycleScope.launch {
            viewModel.toastEvent.collectLatest { msg ->
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            }
        }

        // Always start service on launch
        lifecycleScope.launch {
            if (ShizukuManager.isShizukuRunning() && ShizukuManager.hasPermission()) {
                ShizukuManager.bindUserService()
                ShizukuManager.grantUsageStatsAccessToSelf(this@MainActivity)

                // Wait for the remote service to bind so we can update the UI correctly
                for (i in 1..10) {
                    kotlinx.coroutines.delay(100)
                    if (ShizukuManager.isServiceBound()) {
                        viewModel.refreshShizukuStatus()
                        break
                    }
                }
            }
            startMonitorService()
        }

        val missingShizuku = !ShizukuManager.isShizukuRunning() || !ShizukuManager.hasPermission()
        val missingNotification = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val missingBatteryOpt = !(getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

        val shouldShowOnboardingInitially = missingShizuku || missingNotification || missingBatteryOpt

        val repo = com.nobg.app.data.NobgRepository(applicationContext)
        val initialScreen = intent?.getStringExtra("open_screen") ?: repo.getLastActiveScreen()

        setContent {
            NobgTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf(initialScreen) }
                    var showOnboardingDialog by remember { mutableStateOf(shouldShowOnboardingInitially) }

                    LaunchedEffect(currentScreen) {
                        repo.setLastActiveScreen(currentScreen)
                    }

                    LaunchedEffect(intent) {
                        val screenExtra = intent?.getStringExtra("open_screen")
                        if (!screenExtra.isNullOrBlank()) {
                            currentScreen = screenExtra
                        }
                    }

                    if (showOnboardingDialog) {
                        PermissionOnboardingDialog(
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onDismiss = { showOnboardingDialog = false }
                        )
                    }

                    when (currentScreen) {
                        "SETTINGS" -> SettingsScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "LIST" },
                            onOpenAlgorithmScreen = { currentScreen = "ALGORITHM" }
                        )
                        "BATTERY_STATS" -> BatteryStatsScreen(
                            viewModel = batteryStatsViewModel,
                            onBack = { currentScreen = "LIST" }
                        )
                        "ADVANCED_TWEAKS" -> com.nobg.app.ui.AdvancedTweaksScreen(
                            repo = com.nobg.app.data.NobgRepository(applicationContext),
                            onBack = { currentScreen = "LIST" }
                        )
                        "FREEZER_SHELF" -> com.nobg.app.ui.FreezerShelfScreen(
                            repo = com.nobg.app.data.NobgRepository(applicationContext),
                            onBack = { currentScreen = "LIST" }
                        )
                        "ALGORITHM" -> com.nobg.app.ui.AlgorithmScreen(
                            onBack = { currentScreen = "SETTINGS" }
                        )
                        else -> AppListScreen(
                            viewModel = viewModel,
                            onOpenSettings = { currentScreen = "SETTINGS" },
                            onOpenBatteryStats = { currentScreen = "BATTERY_STATS" },
                            onOpenAdvancedTweaks = { currentScreen = "ADVANCED_TWEAKS" },
                            onOpenFreezerShelf = { currentScreen = "FREEZER_SHELF" }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadAllData()
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
