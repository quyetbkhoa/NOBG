package com.nobg.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.nobg.app.shizuku.ShizukuManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    val shizukuReady by viewModel.shizukuReady.collectAsState()
    val context = LocalContext.current

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
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            
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
                                    android.widget.Toast.makeText(context, "Đã gửi yêu cầu quyền Shizuku", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Thất bại: Shizuku chưa chạy trên thiết bị!", android.widget.Toast.LENGTH_SHORT).show()
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

            // Tối ưu Pin / Autostart
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tối ưu hệ thống (Rất quan trọng)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Để tránh hệ thống tự động tắt bộ đếm pin của NOBG, hãy cấp quyền Tự Khởi Động (Auto Start) và Tắt Hạn Chế Pin (No Restrictions) cho app NOBG.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }) {
                        Text("Mở Cài Đặt App")
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
                    showConfirm = false
                }) { Text("Đồng ý") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Hủy") }
            }
        )
    }
}
