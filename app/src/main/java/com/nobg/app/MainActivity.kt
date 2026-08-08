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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.nobg.app.ui.DashboardScreen
import com.nobg.app.ui.MainViewModel
import com.nobg.app.ui.PermissionOnboardingDialog
import com.nobg.app.ui.NotificationReadScreen
import com.nobg.app.ui.NotificationReadViewModel
import com.nobg.app.ui.SettingsScreen
import com.nobg.app.ui.theme.NobgTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val batteryStatsViewModel: BatteryStatsViewModel by viewModels()
    private val notifReadViewModel: NotificationReadViewModel by viewModels()
    private val smartTimerViewModel: com.nobg.app.ui.SmartTimerViewModel by viewModels()

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

    private val screenFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

        setContent {
            val themeMode = remember { mutableStateOf(repo.getThemeMode()) }
            val darkTheme = when (themeMode.value) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            NobgTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showOnboardingDialog by remember { mutableStateOf(shouldShowOnboardingInitially) }

                    // Back stack: root luôn là DASHBOARD, các màn hình khác push lên trên
                    val initialRaw = intent?.getStringExtra("open_screen") ?: repo.getLastActiveScreen()
                    val initialScreen = if (initialRaw.isNullOrBlank() || initialRaw == "DASHBOARD" || initialRaw == "LIST") {
                        "DASHBOARD"
                    } else {
                        initialRaw
                    }
                    val backStack = remember {
                        mutableStateListOf<String>().apply {
                            add("DASHBOARD")
                            if (initialScreen != "DASHBOARD") add(initialScreen)
                        }
                    }
                    val currentScreen by remember { derivedStateOf { backStack.last() } }

                    fun navigate(screen: String) {
                        if (backStack.last() != screen) backStack.add(screen)
                    }

                    fun goBack() {
                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                    }

                    val newScreenRequested by screenFlow.collectAsState()

                    // Back từ bất kỳ màn hình nào: pop về màn hình trước đó, về tới DASHBOARD là dừng
                    BackHandler(
                        enabled = !showOnboardingDialog && backStack.size > 1
                    ) {
                        goBack()
                    }

                    LaunchedEffect(currentScreen) {
                        repo.setLastActiveScreen(currentScreen)
                    }

                    LaunchedEffect(newScreenRequested) {
                        if (!newScreenRequested.isNullOrBlank()) {
                            navigate(newScreenRequested!!)
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
                        "DASHBOARD" -> DashboardScreen(
                            viewModel = viewModel,
                            onOpenAppList = { navigate("APP_LIST") },
                            onOpenSettings = { navigate("SETTINGS") },
                            onOpenBatteryStats = { navigate("BATTERY_STATS") },
                            onOpenFreezerShelf = { navigate("FREEZER_SHELF") },
                            onOpenSmartTimer = { navigate("SMART_TIMER") },
                            onOpenAiChat = { navigate("AI_CONFIG") },
                            onOpenNotificationRead = { navigate("NOTIFICATION_READ") },
                            onOpenAlgorithm = { navigate("ALGORITHM") },
                            onOpenSystemLists = { navigate("SYSTEM_LISTS") }
                        )
                        "SETTINGS" -> SettingsScreen(
                            viewModel = viewModel,
                            onBack = { goBack() },
                            themeMode = themeMode.value,
                            onThemeModeChanged = { mode ->
                                repo.setThemeMode(mode)
                                themeMode.value = mode
                            }
                        )
                        "BATTERY_STATS" -> BatteryStatsScreen(
                            viewModel = batteryStatsViewModel,
                            onBack = { goBack() }
                        )
                        "FREEZER_SHELF" -> com.nobg.app.ui.FreezerShelfScreen(
                            repo = com.nobg.app.data.NobgRepository(applicationContext),
                            onBack = { goBack() }
                        )
                        "ALGORITHM" -> com.nobg.app.ui.AlgorithmScreen(
                            onBack = { goBack() }
                        )
                        "NOTIFICATION_READ" -> NotificationReadScreen(
                            viewModel = notifReadViewModel,
                            onBack = { goBack() }
                        )
                        "SMART_TIMER" -> com.nobg.app.ui.SmartTimerScreen(
                            viewModel = smartTimerViewModel,
                            onBack = { goBack() }
                        )
                        "AI_CHAT" -> com.nobg.app.ui.ChatScreen(
                            onBack = { goBack() }
                        )
                        "AI_CONFIG" -> com.nobg.app.ui.AiConfigScreen(
                            repo = repo,
                            onBack = { goBack() },
                            onOpenAiChat = { navigate("AI_CHAT") }
                        )
                        "SYSTEM_LISTS" -> com.nobg.app.ui.SystemListsScreen(
                            onBack = { goBack() }
                        )
                        else -> AppListScreen(
                            viewModel = viewModel,
                            onBack = { goBack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val screenExtra = intent.getStringExtra("open_screen")
        if (!screenExtra.isNullOrBlank()) {
            screenFlow.value = screenExtra
        }
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
