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
import androidx.core.content.ContextCompat
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    val shizukuReady by viewModel.shizukuReady.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

            // TRUNG TÂM QUẢN LÝ QUYỀN HỆ THỐNG
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
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
                    Text("Chế độ hoạt động", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !shizukuReady, onClick = { /* Normal Mode is default fallback */ })
                        Text("Chế độ Thường (Chỉ theo dõi pin)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = shizukuReady, onClick = {
                            if (!shizukuReady) {
                                if (ShizukuManager.isShizukuRunning()) {
                                    ShizukuManager.requestPermission(1001)
                                    Toast.makeText(context, "Đã gửi yêu cầu quyền Shizuku", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Thất bại: Shizuku chưa chạy trên thiết bị!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                        Text("Chế độ Nâng cao (Shizuku)")
                    }
                    if (shizukuReady) {
                        Text("Shizuku đang hoạt động tốt!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 48.dp))
                    } else {
                        Text(
                            "Yêu cầu cấp quyền Shizuku để bật tính năng Ép dừng & Vô hiệu hóa ứng dụng ngầm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 48.dp)
                        )
                    }
                }
            }

            // Cập nhật ứng dụng từ GitHub
            var isCheckingUpdate by remember { mutableStateOf(false) }
            var updateResultState by remember { mutableStateOf<com.nobg.app.update.UpdateResult?>(null) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🚀 Cập nhật ứng dụng từ GitHub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("Kiểm tra và tải trực tiếp bản Release APK mới nhất được tự động build từ GitHub Actions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            scope.launch {
                                val res = com.nobg.app.update.GitHubUpdater.checkForUpdates()
                                updateResultState = res
                                isCheckingUpdate = false
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Đang kiểm tra...")
                        } else {
                            Text("🔄 Kiểm tra bản cập nhật mới")
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
                                Text("Tag: ${info.tagName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        com.nobg.app.update.GitHubUpdater.openDownloadLink(context, info.apkUrl)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("⬇️ Tải APK & Cập nhật ngay")
                                }
                            }
                        }
                    } else if (updateResultState is com.nobg.app.update.UpdateResult.Error) {
                        val msg = (updateResultState as com.nobg.app.update.UpdateResult.Error).message
                        Spacer(Modifier.height(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                    Text("Phiên bản: V1.0.4", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Tác giả: quyetbkhoa", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Mã nguồn mở (Github):", style = MaterialTheme.typography.bodySmall)
                    Text("https://github.com/quyetbkhoa/NOBG", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
