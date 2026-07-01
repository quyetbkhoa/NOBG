package com.nobg.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalStatsScreen(
    viewModel: GlobalStatsViewModel,
    onBack: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tổng quan Pin (Accu)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Reset")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!stats.hasData) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Đang thu thập dữ liệu pin...\nHãy quay lại sau vài giờ.")
                }
            } else {
                StatCard("Thời gian theo dõi", formatDuration(stats.totalTimeMs))
                StatCard("Thời gian On-screen", formatDuration(stats.screenOnTimeMs))
                StatCard("Thời gian sạc", formatDuration(stats.chargingTimeMs))
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                StatCard("Pin đã tiêu thụ", "${stats.batteryDischargedPct}%", isError = true)
                StatCard("Pin đã sạc vào", "${stats.batteryChargedPct}%", isPrimary = true)
                
                if (stats.batteryDischargedPct > 0) {
                    val averageDrainPerHour = (stats.batteryDischargedPct / (stats.totalTimeMs / 3600000.0)).coerceAtLeast(0.0)
                    StatCard("Tốc độ hao pin TB", "${String.format("%.1f", averageDrainPerHour)}% / giờ")
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Xác nhận xóa") },
            text = { Text("Bạn có chắc chắn muốn xóa toàn bộ lịch sử theo dõi pin không? Dữ liệu này không thể khôi phục.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetData()
                    showResetDialog = false
                }) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, isError: Boolean = false, isPrimary: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isError) MaterialTheme.colorScheme.error else if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
