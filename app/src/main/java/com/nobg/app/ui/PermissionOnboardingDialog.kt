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
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Vào Ứng Dụng", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Column {
                Text(
                    text = "CHÀO MỪNG ĐẾN VỚI NOBG",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceContainerHigh
                             else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isGranted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Đã cấp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Chưa cấp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isGranted) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(buttonLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
