package com.nobg.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Khôi phục tất cả", style = MaterialTheme.typography.titleMedium)
            Text(
                "Đưa toàn bộ app đã bị NOBG can thiệp về đúng trạng thái ban đầu trước khi bật NOBG lần đầu, và tắt NOBG cho tất cả.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { showConfirm = true }) {
                Text("Reset All")
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
