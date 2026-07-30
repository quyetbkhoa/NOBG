package com.nobg.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nobg.app.data.NotificationReadMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationReadScreen(
    viewModel: NotificationReadViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val apps by viewModel.filteredApps.collectAsState()
    val btDevices by viewModel.btDevices.collectAsState()
    val isGlobalEnabled by viewModel.isGlobalEnabled.collectAsState()
    val isOnlySelectedBt by viewModel.isOnlySelectedBt.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val isNotifListenerEnabled by viewModel.isNotifListenerEnabled.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Refresh permission state when resuming
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkNotifListenerPermission()
                viewModel.loadBluetoothDevices()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Bluetooth permission launcher (Android 12+)
    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.loadBluetoothDevices()
        } else {
            Toast.makeText(context, "Cần cấp quyền Bluetooth để quét thiết bị", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔊 Đọc thông báo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ===== CARD 1: Banner cấp quyền =====
            if (!isNotifListenerEnabled) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "⚠️ CẦN CẤP QUYỀN ĐỌC THÔNG BÁO",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "NOBG cần quyền \"Đọc thông báo\" (Notification Listener) để có thể lắng nghe và đọc thông báo từ các ứng dụng khác.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Không thể mở cài đặt", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("🔓 Mở cài đặt cấp quyền Đọc thông báo")
                            }
                        }
                    }
                }
            }

            // ===== CARD 2: Cấu hình chung =====
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "⚙️ CẤU HÌNH CHUNG",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))

                        // Công tắc tổng
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bật tính năng Đọc thông báo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Tự động đọc thông báo bằng giọng nói TTS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isGlobalEnabled,
                                onCheckedChange = { viewModel.toggleGlobalEnabled(it) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Chỉ đọc khi BT chọn kết nối
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Chỉ đọc khi kết nối thiết bị BT được chọn", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Chỉ phát giọng đọc khi đúng tai nghe/loa Bluetooth đã chọn đang kết nối", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isOnlySelectedBt,
                                onCheckedChange = { viewModel.toggleOnlySelectedBt(it) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Tốc độ đọc
                        Text("Tốc độ đọc: ${"%.1f".format(speechRate)}x", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Slider(
                            value = speechRate,
                            onValueChange = { viewModel.setSpeechRate(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 14, // 0.1 increments
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        // Nút test TTS
                        OutlinedButton(
                            onClick = {
                                viewModel.testTts("Đây là giọng đọc thử nghiệm từ NOBG. Thông báo từ Zalo. Nguyễn Văn A: Hồi nữa gặp nhé!")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔊 Thử giọng đọc (Test TTS)")
                        }
                    }
                }
            }

            // ===== CARD 3: Danh sách Thiết bị Bluetooth (khi bật lọc BT) =====
            if (isOnlySelectedBt) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "🎧 THIẾT BỊ BLUETOOTH ĐƯỢC PHÉP ĐỌC",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                IconButton(onClick = {
                                    if (Build.VERSION.SDK_INT >= 31) {
                                        btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    } else {
                                        viewModel.loadBluetoothDevices()
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Làm mới", modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tích chọn thiết bị Bluetooth mà bạn muốn kích hoạt đọc thông báo. Chỉ khi kết nối với thiết bị được chọn, thông báo mới được đọc.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(12.dp))

                            if (btDevices.isEmpty()) {
                                Text(
                                    "Không tìm thấy thiết bị Bluetooth nào đã ghép đôi.\nHãy ghép đôi tai nghe/loa BT trước, sau đó bấm Làm mới.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                btDevices.forEach { device ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🎧", modifier = Modifier.padding(end = 8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(device.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Row {
                                                Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (device.isConnected) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        "● Đang kết nối",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Checkbox(
                                            checked = device.isSelected,
                                            onCheckedChange = {
                                                viewModel.toggleBtDeviceSelected(device.address, device.name, it)
                                            }
                                        )
                                    }
                                    if (device != btDevices.last()) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ===== CARD 4: Thao tác hàng loạt =====
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "⚡ THAO TÁC NHANH",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.enableAllApps() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("✅ Bật tất cả", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { viewModel.disableAllApps() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("❌ Tắt tất cả", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.setAllReadMode(NotificationReadMode.APP_NAME_ONLY) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📌 Tất cả: Tên app", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { viewModel.setAllReadMode(NotificationReadMode.FULL_CONTENT) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📄 Tất cả: Đầy đủ", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // ===== TextField Tìm kiếm =====
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("🔍 Tìm kiếm ứng dụng...") },
                    singleLine = true
                )
            }

            // ===== Header thống kê =====
            item {
                val enabledCount = apps.count { it.isEnabled }
                Text(
                    "Ứng dụng: ${apps.size} tổng · $enabledCount đang bật đọc",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ===== Danh sách App =====
            items(apps, key = { it.packageName }) { app ->
                NotificationReadAppItem(
                    app = app,
                    onToggleEnabled = { viewModel.toggleAppEnabled(app.packageName, it) },
                    onSetReadMode = { viewModel.setAppReadMode(app.packageName, it) }
                )
            }

            // Bottom padding
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun NotificationReadAppItem(
    app: NotifReadAppUiModel,
    onToggleEnabled: (Boolean) -> Unit,
    onSetReadMode: (NotificationReadMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isEnabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App icon
                if (app.icon != null) {
                    Image(
                        bitmap = app.icon.toBitmap(80, 80).asImageBitmap(),
                        contentDescription = app.label,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Box(modifier = Modifier.size(40.dp))
                }

                Spacer(Modifier.width(12.dp))

                // App info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Toggle switch
                Switch(
                    checked = app.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            // Mode selector (visible only when enabled)
            if (app.isEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = app.readMode == NotificationReadMode.APP_NAME_ONLY,
                        onClick = { onSetReadMode(NotificationReadMode.APP_NAME_ONLY) },
                        label = { Text("📌 Chỉ tên app", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = app.readMode == NotificationReadMode.FULL_CONTENT,
                        onClick = { onSetReadMode(NotificationReadMode.FULL_CONTENT) },
                        label = { Text("📄 Đầy đủ nội dung", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
