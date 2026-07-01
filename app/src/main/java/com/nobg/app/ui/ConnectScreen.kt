package com.nobg.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    requestPermission: () -> Unit
) {
    var installed by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var bound by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        installed = ShizukuManager.isShizukuInstalled()
        running = ShizukuManager.isShizukuRunning()
        hasPermission = ShizukuManager.hasPermission()
        bound = ShizukuManager.isServiceBound()
        if (hasPermission && running && !bound) {
            ShizukuManager.bindUserService()
        }
        if (bound) onConnected()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Kết nối Shizuku", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "NOBG cần Shizuku để cấp quyền hệ thống (force-stop, disable app, chặn background) mà không cần root.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        StatusRow("Đã cài Shizuku", installed)
        StatusRow("Shizuku đang chạy", running)
        StatusRow("Đã cấp quyền cho NOBG", hasPermission)
        StatusRow("Đã kết nối dịch vụ", bound)

        Spacer(Modifier.height(24.dp))

        if (!installed) {
            Text("Bước 1: Cài app Shizuku từ Play Store hoặc GitHub (rikkaapps/Shizuku).")
        } else if (!running) {
            Column {
                Text("Bước 2: Khởi động Shizuku bằng MỘT trong hai cách:")
                Spacer(Modifier.height(8.dp))
                Text("• Cắm cáp USB, bật USB debugging, trên máy tính chạy:")
                Card(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("• Hoặc mở app Shizuku > \"Bắt đầu qua Wireless debugging\" (Android 11+, không cần cáp).")
            }
        } else if (!hasPermission) {
            Button(onClick = { requestPermission(); scope.launch { refresh() } }) {
                Text("Cấp quyền cho NOBG")
            }
        } else {
            Button(onClick = { scope.launch { refresh() } }) {
                Text("Làm mới trạng thái")
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { refresh() }) { Text("Kiểm tra lại") }

        Spacer(Modifier.height(24.dp))
        
        val context = LocalContext.current
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Quan trọng: Tránh bị ngắt kết nối", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hãy bật Tự khởi chạy (Auto Start) và Không hạn chế pin (No Restrictions) để NOBG luôn chạy ngầm và không bị hệ thống kill.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Mở Cài đặt Ứng dụng")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
