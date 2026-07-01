package com.nobg.app.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobg.app.qs.*
import com.nobg.app.shizuku.ShizukuManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val shizukuReady = ShizukuManager.isShizukuRunning() && ShizukuManager.hasPermission() && ShizukuManager.isServiceBound()

    val hasWriteSecure = remember {
        context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val hasWriteSettings = remember {
        Settings.System.canWrite(context)
    }

    var showAdbDialog by remember { mutableStateOf<String?>(null) }
    var showShortcutConfig by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chỉnh sửa Quick Setting") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // Permission Status Card
                val statusColor = if (hasWriteSecure) MaterialTheme.colorScheme.primaryContainer
                                  else MaterialTheme.colorScheme.errorContainer
                Card(
                    colors = CardDefaults.cardColors(containerColor = statusColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (hasWriteSecure) "✅ Đã có quyền WRITE_SECURE_SETTINGS"
                            else "⚠️ Chưa có quyền WRITE_SECURE_SETTINGS",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!hasWriteSecure) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Cần quyền này để bật/tắt USB Debug, ADB Không dây, Tuỳ chọn Nhà phát triển.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            if (shizukuReady) {
                                Button(onClick = {
                                    // Grant via Shizuku
                                    // (launched as coroutine in effect)
                                }) { Text("Cấp quyền qua Shizuku") }
                            } else {
                                OutlinedButton(onClick = {
                                    showAdbDialog = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
                                }) { Text("Hiện lệnh ADB để cấp quyền") }
                            }
                        }
                    }
                }
            }

            item {
                Text("Các nút Quick Setting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                QsTileCard(
                    title = "🔌 USB Debug",
                    description = "Nút bật/tắt USB Debugging trong vùng thông báo nhanh.",
                    requiresSecure = true,
                    hasSecure = hasWriteSecure,
                    shizukuReady = shizukuReady,
                    onAdd = { addTileToQs(context, UsbDebugTileService::class.java) },
                    onShowAdb = { showAdbDialog = buildAdbGrantSecure(context.packageName) }
                )
            }

            item {
                QsTileCard(
                    title = "📡 ADB Không dây",
                    description = "Nút bật/tắt gỡ lỗi không dây (Wireless ADB) trong QS.",
                    requiresSecure = true,
                    hasSecure = hasWriteSecure,
                    shizukuReady = shizukuReady,
                    onAdd = { addTileToQs(context, WirelessDebugTileService::class.java) },
                    onShowAdb = { showAdbDialog = buildAdbGrantSecure(context.packageName) }
                )
            }

            item {
                QsTileCard(
                    title = "🛠️ Tuỳ chọn nhà phát triển",
                    description = "Nút bật/tắt Developer Options trong QS.",
                    requiresSecure = true,
                    hasSecure = hasWriteSecure,
                    shizukuReady = shizukuReady,
                    onAdd = { addTileToQs(context, DevOptionsTileService::class.java) },
                    onShowAdb = { showAdbDialog = buildAdbGrantSecure(context.packageName) }
                )
            }

            item {
                QsTileCard(
                    title = "⏱️ Thời gian sáng màn hình",
                    description = "Bấm để chuyển vòng qua các mức: 15s → 30s → 1m → 2m → 5m → 10m. Bấm lại để vào Cài đặt màn hình.",
                    requiresSecure = false,
                    hasSecure = hasWriteSettings,
                    shizukuReady = shizukuReady,
                    onAdd = { addTileToQs(context, ScreenTimeoutTileService::class.java) },
                    onShowAdb = { showAdbDialog = "adb shell appops set ${context.packageName} android:write_settings allow" }
                )
            }

            item {
                // Shortcut tile with extra config
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🚀 Shortcut mở App", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Nút QS dùng để mở nhanh một ứng dụng hoặc màn hình bất kỳ.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showShortcutConfig = true },
                                modifier = Modifier.weight(1f)
                            ) { Text("Cấu hình") }
                            Button(
                                onClick = { addTileToQs(context, AppShortcutTileService::class.java) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Thêm vào QS") }
                        }
                    }
                }
            }
        }
    }

    // Dialog: show ADB command
    showAdbDialog?.let { cmd ->
        AlertDialog(
            onDismissRequest = { showAdbDialog = null },
            title = { Text("Lệnh ADB cần chạy") },
            text = {
                Column {
                    Text("Kết nối máy tính với ADB rồi chạy lệnh sau:", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            cmd,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdbDialog = null }) { Text("Đã hiểu") }
            }
        )
    }

    // Dialog: shortcut config
    if (showShortcutConfig) {
        ShortcutConfigDialog(
            context = context,
            onDismiss = { showShortcutConfig = false }
        )
    }
}

@Composable
private fun QsTileCard(
    title: String,
    description: String,
    requiresSecure: Boolean,
    hasSecure: Boolean,
    shizukuReady: Boolean,
    onAdd: () -> Unit,
    onShowAdb: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))

            if (requiresSecure && !hasSecure) {
                Text(
                    "⚠️ Cần quyền WRITE_SECURE_SETTINGS để nút này hoạt động.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (requiresSecure && !hasSecure) {
                    OutlinedButton(onClick = onShowAdb, modifier = Modifier.weight(1f)) {
                        Text("Xem lệnh ADB", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Text("Thêm vào QS", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ShortcutConfigDialog(context: Context, onDismiss: () -> Unit) {
    val prefs = context.getSharedPreferences("nobg_prefs", Context.MODE_PRIVATE)
    var packageName by remember { mutableStateOf(prefs.getString("qs_shortcut_package", "") ?: "") }
    var activityName by remember { mutableStateOf(prefs.getString("qs_shortcut_activity", "") ?: "") }
    var label by remember { mutableStateOf(prefs.getString("qs_shortcut_label", "") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cấu hình Shortcut QS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Điền package name và activity (tuỳ chọn) của ứng dụng muốn mở.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package (vd: com.android.settings)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = activityName,
                    onValueChange = { activityName = it },
                    label = { Text("Activity (tuỳ chọn, để trống = màn hình chính)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Tên hiển thị trên QS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.edit()
                    .putString("qs_shortcut_package", packageName.trim())
                    .putString("qs_shortcut_activity", activityName.trim().ifEmpty { null })
                    .putString("qs_shortcut_label", label.trim().ifEmpty { "Shortcut" })
                    .apply()
                onDismiss()
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

private fun addTileToQs(context: Context, serviceClass: Class<*>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        statusBarManager.requestAddTileService(
            ComponentName(context, serviceClass),
            "NOBG Tile",
            android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_info_details),
            { cmd -> cmd.run() },
            {}
        )
    } else {
        // Fallback: open QS settings or show message
        try {
            context.startActivity(Intent("android.settings.ACTION_QS_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}

private fun buildAdbGrantSecure(pkg: String) =
    "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
