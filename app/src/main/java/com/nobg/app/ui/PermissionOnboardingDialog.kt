package com.nobg.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.launch
import com.nobg.app.shell.PrivilegedShell

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingDialog(
    onRequestNotificationPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isShizukuOk by remember {
        mutableStateOf(PrivilegedShell.isReady())
    }
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

    fun refreshAllStatus() {
        PrivilegedShell.tryConnectAdb()
        isShizukuOk = PrivilegedShell.isReady()
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
                refreshAllStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tiếp tục", fontWeight = FontWeight.Medium)
            }
        },
        title = {
            Column {
                Text(
                    text = "Chào mừng đến NOBG",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Thiết lập quyền hệ thống để app hoạt động tối ưu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Item 1: Shizuku / ADB
                PermissionStatusCard(
                    title = "1. Quyền đặc quyền hệ thống",
                    description = "Cần Shizuku hoặc ADB để Ép dừng, Vô hiệu hóa & Đổi chế độ pin ngầm.",
                    isGranted = isShizukuOk,
                    buttonLabel = "Cấp quyền",
                    onAction = {
                        if (ShizukuManager.isShizukuRunning()) {
                            ShizukuManager.requestPermission(1001)
                        } else {
                            android.widget.Toast.makeText(context, "Shizuku chưa chạy, vui lòng thiết lập ADB trong cài đặt!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        refreshAllStatus()
                    }
                )

                // Item 2: Usage Stats
                PermissionStatusCard(
                    title = "2. Giám sát sử dụng App (Usage Stats)",
                    description = "Theo dõi thời gian app mở/thoát để tính toán pin và xử lý ngầm.",
                    isGranted = isUsageStatsOk,
                    buttonLabel = "Cấp quyền",
                    onAction = {
                        scope.launch {
                            var granted = false
                            if (isShizukuOk) {
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
                                android.widget.Toast.makeText(context, "Đã cấp quyền Usage Stats thành công!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            refreshAllStatus()
                        }
                    }
                )

                // Item 3: Notifications
                if (Build.VERSION.SDK_INT >= 33) {
                    PermissionStatusCard(
                        title = "3. Quyền Thông báo (Notifications)",
                        description = "Hiển thị thông báo dự đoán sạc pin & duy trì dịch vụ chạy ngầm.",
                        isGranted = isNotificationOk,
                        buttonLabel = "Cấp quyền",
                        onAction = {
                            onRequestNotificationPermission()
                            refreshAllStatus()
                        }
                    )
                }

                // Item 4: Battery Optimization Ignore
                PermissionStatusCard(
                    title = "4. Tắt Hạn chế Pin cho NOBG",
                    description = "Tránh bị hệ thống Android tự đóng dịch vụ giám sát pin ngầm của NOBG.",
                    isGranted = isBatteryOptOk,
                    buttonLabel = "Tắt tối ưu pin",
                    showDivider = false,
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
                        refreshAllStatus()
                    }
                )
            }
        }
    )
}

@Composable
fun PermissionStatusCard(
    title: String,
    description: String,
    isGranted: Boolean,
    buttonLabel: String,
    showDivider: Boolean = true,
    onAction: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PremiumNavigationRow(
            title = title,
            subtitle = description,
            icon = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            accent = if (isGranted) PremiumAccent.Green else PremiumAccent.Orange,
            onClick = { if (!isGranted) onAction() },
            trailing = if (isGranted) "Đã cấp" else buttonLabel,
            showChevron = !isGranted
        )
        if (showDivider) PremiumInsetDivider()
    }
}
