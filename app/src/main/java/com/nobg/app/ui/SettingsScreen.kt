package com.nobg.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Refresh
import androidx.core.content.ContextCompat
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nobg.app.shell.PrivilegedShell
import com.nobg.app.shell.AdbDaemonInstaller

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenAlgorithmScreen: () -> Unit,
    onOpenNotificationRead: () -> Unit = {},
    onOpenSmartTimer: () -> Unit = {}
) {
    var showConfirm by remember { mutableStateOf(false) }
    val shizukuReady by viewModel.shizukuReady.collectAsState()
    val activeBackend by viewModel.activeBackend.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { com.nobg.app.data.NobgRepository(context) }

    var isUsageStatsOk by remember { mutableStateOf(false) }
    var isNotificationOk by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var isBatteryOptOk by remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        )
    }

    LaunchedEffect(Unit) {
        scope.launch {
            isUsageStatsOk = ShizukuManager.hasUsageStatsAccess(context)
        }
    }

    fun refreshAllPermissionStatus() {
        if (Build.VERSION.SDK_INT >= 33) {
            isNotificationOk = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        isBatteryOptOk = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        scope.launch {
            isUsageStatsOk = ShizukuManager.hasUsageStatsAccess(context)
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshAllPermissionStatus()
                viewModel.refreshShizukuStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(onBack = onBack)

    var selfStats by remember { mutableStateOf<com.nobg.app.data.NobgSelfStats?>(null) }
    var isSelfStatsLoading by remember { mutableStateOf(false) }

    fun refreshSelfStats() {
        isSelfStatsLoading = true
        scope.launch(Dispatchers.IO) {
            val stats = com.nobg.app.data.NobgSelfStatsHelper.getNobgSelfStats(context)
            withContext(Dispatchers.Main) {
                selfStats = stats
                isSelfStatsLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshSelfStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // NOBG SELF RESOURCE CONSUMPTION CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📊 TÀI NGUYÊN NOBG ĐANG SỬ DỤNG",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { refreshSelfStats() }) {
                            if (isSelfStatsLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Làm mới", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Text(
                        "Thống kê trực tiếp tài nguyên RAM, CPU và Pin ứng dụng NOBG đang tiêu thụ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (selfStats != null) {
                        val stats = selfStats!!
                        val uptimeMs = android.os.SystemClock.elapsedRealtime()
                        val uptimeH = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(uptimeMs)
                        val uptimeM = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60
                        val uptimeS = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(uptimeMs) % 60
                        val sessionTimeText = String.format(java.util.Locale.getDefault(), "%02dh %02dm %02ds", uptimeH, uptimeM, uptimeS)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("⏱️ Thời gian đếm (Phiên):", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    sessionTimeText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("💾 Bộ nhớ RAM đang dùng:", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${String.format("%.1f", stats.ramMb)} MB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("⚡ Tải CPU trung bình:", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${String.format("%.2f", stats.cpuPct)}% (${String.format("%.1f", stats.cpuTimeMs / 1000.0)}s CPU)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🔋 Điện năng đã dùng:", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${String.format("%.1f", stats.batteryMah)} mAh (${String.format("%.2f", stats.batteryPct)}% pin)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }

            val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) {
                    scope.launch {
                        try {
                            val jsonStr = repo.exportConfigJson()
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(jsonStr.toByteArray(Charsets.UTF_8))
                            }
                            Toast.makeText(context, "📤 Đã xuất file cấu hình JSON thành công!", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Lỗi xuất file: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    scope.launch {
                        try {
                            val jsonStr = context.contentResolver.openInputStream(uri)?.use { input ->
                                input.bufferedReader().readText()
                            }
                            if (!jsonStr.isNullOrBlank()) {
                                val (restored, total) = repo.importConfigJson(jsonStr)
                                Toast.makeText(context, "📥 Đã đồng bộ cấu hình $restored/$total ứng dụng!", Toast.LENGTH_LONG).show()
                                viewModel.reloadAllData()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Lỗi nhập cấu hình: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            // ☁️ SAO LƯU & ĐỒNG BỘ CẤU HÌNH ĐA THIẾT BỊ (JSON)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "☁️ SAO LƯU & ĐỒNG BỘ CẤU HÌNH ĐA THIẾT BỊ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Xuất danh sách cài đặt ứng dụng thành file JSON linh hoạt để lưu trữ hoặc gửi sang điện thoại khác khôi phục lập tức.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { exportLauncher.launch("nobg_config_backup.json") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📤 Xuất file (JSON)")
                        }
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📥 Nhập cấu hình")
                        }
                    }
                }
            }

            // 🎨 TÙY CHỈNH GIAO DIỆN WIDGET KỆ ĐÓNG BĂNG
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🎨 GIAO DIỆN WIDGET KỆ ĐÓNG BĂNG",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tùy chỉnh chủ đề (đen/trắng), độ mờ nền, số cột, kích thước icon và bo góc avatar ứng dụng trên Widget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(context, com.nobg.app.widget.WidgetConfigActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🎨 Mở menu Tùy chỉnh Giao diện Widget", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 🔊 ĐỌC THÔNG BÁO (NOTIFICATION READER)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🔊 ĐỌC THÔNG BÁO (BUDS STYLE)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tự động đọc tên ứng dụng hoặc nội dung thông báo khi kết nối tai nghe Bluetooth. Hỗ trợ chọn thiết bị BT cụ thể.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onOpenNotificationRead,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("🔊 Mở cài đặt Đọc thông báo")
                    }
                }
            }

            // ⏱️ ĐẾM GIỜ THÔNG MINH (SMART TIMER)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "⏱️ ĐẾM GIỜ THÔNG MINH & HẸN GIỜ TẮT MÁY",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Đọc giờ/thời gian định kỳ ngầm khi tắt màn hình bằng TTS tiếng Việt. Hỗ trợ hẹn giờ tự động tắt máy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onOpenSmartTimer,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("⏱️ Mở Đếm giờ thông minh")
                    }
                }
            }

            // TRUNG TÂM QUẢN LÝ QUYỀN HỆ THỐNG
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📋 TRUNG TÂM QUẢN LÝ QUYỀN HỆ THỐNG",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kiểm tra trạng thái thời gian thực và đòi các quyền cần thiết cho NOBG.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))

                    // 1. Shizuku
                    PermissionStatusCard(
                        title = "1. Quyền đặc quyền Shizuku",
                        description = "Cần thiết để Ép dừng, Vô hiệu hóa & Đổi chế độ pin ngầm.",
                        isGranted = shizukuReady,
                        buttonLabel = "Cấp quyền Shizuku",
                        onAction = {
                            if (ShizukuManager.isShizukuRunning()) {
                                ShizukuManager.requestPermission(1001)
                                Toast.makeText(context, "Đã gửi yêu cầu quyền Shizuku", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Shizuku chưa chạy trên thiết bị!", Toast.LENGTH_SHORT).show()
                            }
                            refreshAllPermissionStatus()
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    // 2. Usage Stats
                    PermissionStatusCard(
                        title = "2. Giám sát sử dụng App (Usage Stats)",
                        description = "Theo dõi thời gian app mở/thoát để tính toán pin.",
                        isGranted = isUsageStatsOk,
                        buttonLabel = "Cấp quyền",
                        onAction = {
                            scope.launch {
                                var granted = false
                                if (shizukuReady) {
                                    granted = ShizukuManager.grantUsageStatsAccessToSelf(context)
                                }
                                if (!granted) {
                                    try {
                                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                } else {
                                    Toast.makeText(context, "Đã cấp quyền Usage Stats thành công!", Toast.LENGTH_SHORT).show()
                                }
                                refreshAllPermissionStatus()
                            }
                        }
                    )

                    if (Build.VERSION.SDK_INT >= 33) {
                        Spacer(Modifier.height(8.dp))
                        // 3. Notifications
                        PermissionStatusCard(
                            title = "3. Quyền Thông báo (Notifications)",
                            description = "Hiển thị thông báo sạc pin & dịch vụ chạy ngầm.",
                            isGranted = isNotificationOk,
                            buttonLabel = "Cấp quyền",
                            onAction = {
                                try {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                                refreshAllPermissionStatus()
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // 4. Ignore Battery Saver
                    PermissionStatusCard(
                        title = "4. Tắt Hạn chế Pin cho NOBG",
                        description = "Giữ cho dịch vụ NOBG không bị Android tự động tắt.",
                        isGranted = isBatteryOptOk,
                        buttonLabel = "Tắt tối ưu pin",
                        onAction = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(fallbackIntent)
                                } catch (_: Exception) {}
                            }
                            refreshAllPermissionStatus()
                        }
                    )
                }
            }

            // Chế độ hoạt động
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Chế độ hoạt động", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))

                    val isNone = activeBackend == PrivilegedShell.Backend.NONE
                    val isShizuku = activeBackend == PrivilegedShell.Backend.SHIZUKU
                    val isAdb = activeBackend == PrivilegedShell.Backend.ADB

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isNone, onClick = { /* Read only */ })
                        Text("Chế độ Thường (Chỉ theo dõi pin)")
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isShizuku, onClick = {
                            if (!isShizuku) {
                                if (ShizukuManager.isShizukuRunning()) {
                                    ShizukuManager.requestPermission(1001)
                                    Toast.makeText(context, "Đã gửi yêu cầu quyền Shizuku", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Thất bại: Shizuku chưa chạy trên thiết bị!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                        Text("Chế độ Shizuku")
                    }
                    
                    if (isShizuku) {
                        Text("Shizuku đang hoạt động tốt!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 48.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isAdb, onClick = { /* Read only */ })
                        Text("Chế độ ADB trực tiếp")
                    }

                    if (isAdb) {
                        Text("ADB Daemon đang hoạt động tốt!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 48.dp))
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Cấu hình ADB Daemon (Thay thế Shizuku)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    
                    var daemonExtracted by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            try {
                                AdbDaemonInstaller.extractDaemonScript(context)
                                daemonExtracted = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isAdb) {
                                    Text("🟢 Daemon đang chạy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("🔴 Daemon chưa khởi động", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            
                            val wiredCmd = remember { AdbDaemonInstaller.getWiredAdbCommand(context) }
                            Text("📋 Lệnh ADB (kết nối USB):", style = MaterialTheme.typography.labelSmall)
                            Text(wiredCmd, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(vertical = 4.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("NOBG ADB Command", wiredCmd))
                                        Toast.makeText(context, "Đã sao chép lệnh!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Sao chép", style = MaterialTheme.typography.labelSmall)
                                }
                                
                                Button(
                                    onClick = { viewModel.connectAdbDaemon() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Kiểm tra kết nối", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            var showWireless by remember { mutableStateOf(false) }
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { showWireless = !showWireless }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (showWireless) "Ẩn Hướng dẫn Wireless ADB (Android 11+)" else "Hiện Hướng dẫn Wireless ADB (Android 11+)", style = MaterialTheme.typography.labelSmall)
                            }
                            
                            if (showWireless) {
                                val wirelessInst = remember { AdbDaemonInstaller.getWirelessAdbPairInstructions() }
                                Text(wirelessInst, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }

            // Cập nhật ứng dụng từ GitHub
            var isCheckingUpdate by remember { mutableStateOf(false) }
            var isFetchingChangelog by remember { mutableStateOf(false) }
            var changelogInfo by remember { mutableStateOf<com.nobg.app.update.UpdateInfo?>(null) }
            var updateResultState by remember { mutableStateOf<com.nobg.app.update.UpdateResult?>(null) }
            var isDownloading by remember { mutableStateOf(false) }
            var downloadProgressPct by remember { mutableStateOf(0) }
            var downloadStatusText by remember { mutableStateOf("") }

            if (changelogInfo != null) {
                com.nobg.app.update.AutoUpdateDialog(
                    updateInfo = changelogInfo!!,
                    context = context,
                    onDismiss = { changelogInfo = null }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🚀 Cập nhật ứng dụng từ GitHub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("Kiểm tra và tải trực tiếp bản Release APK mới nhất được tự động build từ GitHub Actions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isCheckingUpdate = true
                                scope.launch {
                                    val res = com.nobg.app.update.GitHubUpdater.checkForUpdates(context)
                                    updateResultState = res
                                    isCheckingUpdate = false
                                }
                            },
                            enabled = !isCheckingUpdate && !isDownloading && !isFetchingChangelog,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("🔄 Kiểm tra bản mới")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                isFetchingChangelog = true
                                scope.launch {
                                    val info = com.nobg.app.update.GitHubUpdater.fetchLatestReleaseInfo(context)
                                    changelogInfo = info
                                    isFetchingChangelog = false
                                }
                            },
                            enabled = !isCheckingUpdate && !isDownloading && !isFetchingChangelog,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isFetchingChangelog) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("📝 Xem Changelog")
                            }
                        }
                    }

                    if (updateResultState is com.nobg.app.update.UpdateResult.UpdateAvailable) {
                        val info = (updateResultState as com.nobg.app.update.UpdateResult.UpdateAvailable).info
                        Spacer(Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("✨ Đã tìm thấy bản Release APK mới!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.height(4.dp))
                                Text("Phiên bản mới: ${info.tagName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        isDownloading = true
                                        scope.launch {
                                            val downloadedFile = com.nobg.app.update.GitHubUpdater.downloadApk(context, info.apkUrl) { bytes, totalBytes, pct ->
                                                downloadProgressPct = pct
                                                val downloadedMB = String.format("%.1f", bytes / (1024.0 * 1024.0))
                                                val totalMB = if (totalBytes > 0) String.format("%.1f MB", totalBytes / (1024.0 * 1024.0)) else "KB"
                                                downloadStatusText = "Đang tải... $pct% ($downloadedMB / $totalMB)"
                                            }
                                            if (downloadedFile != null && downloadedFile.exists()) {
                                                downloadStatusText = "⚡ Đang tiến hành cài đặt..."
                                                com.nobg.app.update.GitHubUpdater.installApk(context, downloadedFile)
                                            } else {
                                                downloadStatusText = "❌ Tải file APK thất bại."
                                            }
                                            isDownloading = false
                                        }
                                    },
                                    enabled = !isDownloading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (downloadStatusText.isNotBlank()) downloadStatusText else "Đang tải APK...")
                                    } else {
                                        Text("🚀 Tải & Cài đặt trực tiếp")
                                    }
                                }

                                if (isDownloading && downloadProgressPct > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = downloadProgressPct / 100f,
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        com.nobg.app.update.GitHubUpdater.openDownloadLink(context, info.apkUrl)
                                    },
                                    enabled = !isDownloading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🌐 Mở link trên trình duyệt")
                                }
                            }
                        }
                    } else if (updateResultState is com.nobg.app.update.UpdateResult.AlreadyLatest) {
                        val ver = (updateResultState as com.nobg.app.update.UpdateResult.AlreadyLatest).currentVersion
                        Spacer(Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✅ Bạn đang sử dụng phiên bản mới nhất (v$ver)!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (updateResultState is com.nobg.app.update.UpdateResult.Error) {
                        val msg = (updateResultState as com.nobg.app.update.UpdateResult.Error).message
                        Spacer(Modifier.height(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                com.nobg.app.update.GitHubUpdater.openDownloadLink(context, "https://github.com/quyetbkhoa/NOBG/releases")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🌐 Mở trang Release trên trình duyệt Web")
                        }
                    }
                }
            }

            val currentVersionName = remember(context) {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "3.0.0"
                } catch (e: Exception) {
                    "3.0.0"
                }
            }
            // 📘 THUẬT TOÁN & LỆNH HỆ THỐNG NOBG
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📘 THUẬT TOÁN & LỆNH HỆ THỐNG", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.height(4.dp))
                    Text("Xem chi tiết sơ đồ cây nguyên lý thuật toán, công thức toán học và toàn bộ lệnh Shell ADB/Shizuku cho mọi tính năng.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onOpenAlgorithmScreen,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("📖 Mở bảng tra cứu Thuật toán & Lệnh (Cây Sơ Đồ)")
                    }
                }
            }

            // Thông tin ứng dụng
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Thông tin ứng dụng", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Phiên bản: v$currentVersionName", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Tác giả: quyetbkhoa", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Mã nguồn mở (Github):", style = MaterialTheme.typography.bodySmall)
                    Text("https://github.com/quyetbkhoa/NOBG", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                changelogInfo = com.nobg.app.update.GitHubUpdater.fetchLatestReleaseInfo(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📝 Xem Nhật ký thay đổi (Changelog)")
                    }
                }
            }

            // Khôi phục tất cả
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Khôi phục tất cả", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Đưa toàn bộ app đã bị NOBG can thiệp về đúng trạng thái ban đầu trước khi bật NOBG lần đầu, và tắt NOBG cho tất cả.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset All")
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Xác nhận Reset All") },
            text = { Text("Toàn bộ app sẽ được khôi phục về trạng thái gốc và tắt NOBG. Tiếp tục?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAll()
                    Toast.makeText(context, "Đã khôi phục tất cả!", Toast.LENGTH_SHORT).show()
                    showConfirm = false
                }) { Text("Đồng ý") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Hủy") }
            }
        )
    }
}
