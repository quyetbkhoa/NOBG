package com.nobg.app.update

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AutoUpdateDialog(
    updateInfo: UpdateInfo,
    context: Context,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgressPct by remember { mutableStateOf(0) }
    var downloadStatusText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚀 BẢN CẬP NHẬT MỚI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Phiên bản mới nhất: ${updateInfo.tagName}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "📝 NHẬT KÝ THAY ĐỔI (CHANGELOG):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(6.dp))

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (updateInfo.body.isNotBlank()) updateInfo.body else "Cải tiến hiệu năng và sửa lỗi hệ thống.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (isDownloading) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (downloadStatusText.isNotBlank()) downloadStatusText else "Đang tải gói cài đặt APK...",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgressPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isDownloading = true
                    scope.launch {
                        val downloadedFile = GitHubUpdater.downloadApk(context, updateInfo.apkUrl) { bytes, totalBytes, pct ->
                            downloadProgressPct = pct
                            val downloadedMB = String.format("%.1f", bytes / (1024.0 * 1024.0))
                            val totalMB = if (totalBytes > 0) String.format("%.1f MB", totalBytes / (1024.0 * 1024.0)) else "KB"
                            downloadStatusText = "Đang tải APK... $pct% ($downloadedMB / $totalMB)"
                        }
                        if (downloadedFile != null && downloadedFile.exists()) {
                            downloadStatusText = "⚡ Đang tự động cài đặt..."
                            GitHubUpdater.installApk(context, downloadedFile)
                            onDismiss()
                        } else {
                            downloadStatusText = "❌ Tải file APK thất bại."
                            Toast.makeText(context, "Lỗi tải APK từ GitHub Releases.", Toast.LENGTH_SHORT).show()
                        }
                        isDownloading = false
                    }
                },
                enabled = !isDownloading
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("Đang tải...")
                } else {
                    Text("🚀 Tải & Cài đặt")
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Để sau")
                }
            }
        }
    )
}
