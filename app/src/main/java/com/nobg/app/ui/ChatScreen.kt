package com.nobg.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    chatViewModel: ChatViewModel = viewModel()
) {
    val messages by chatViewModel.messages.collectAsState()
    val isSending by chatViewModel.isSending.collectAsState()
    val inputText by chatViewModel.inputText.collectAsState()
    val configError by chatViewModel.configError.collectAsState()
    val pendingApproval by chatViewModel.pendingApproval.collectAsState()

    val listState = rememberLazyListState()

    // Dialog xét duyệt khi AI muốn thay đổi cài đặt NOBG
    pendingApproval?.let { approval ->
        AlertDialog(
            onDismissRequest = { chatViewModel.respondApproval(false) },
            title = { Text("⚠️ AI yêu cầu thay đổi cài đặt", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("AI Trợ lý muốn thực hiện:")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        approval.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Chỉ khi bạn đồng ý, thay đổi mới được áp dụng.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { chatViewModel.respondApproval(true) }) {
                    Text("Chấp thuận")
                }
            },
            dismissButton = {
                TextButton(onClick = { chatViewModel.respondApproval(false) }) {
                    Text("Từ chối")
                }
            }
        )
    }

    // Tự cuộn xuống tin mới nhất
    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Trợ lý", fontWeight = FontWeight.SemiBold)
                        Text(
                            chatViewModel.providerDisplayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { chatViewModel.clearChat() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Xóa chat")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp, shadowElevation = 0.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = PremiumDimens.ContentMaxWidth)
                        .padding(horizontal = PremiumDimens.ScreenGutter, vertical = 8.dp)
                ) {
                    configError?.let { error ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { chatViewModel.dismissConfigError() }) {
                                    Text("✕", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { chatViewModel.setInputText(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Hỏi bất cứ điều gì...") },
                            maxLines = 4,
                            shape = RoundedCornerShape(28.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = { chatViewModel.sendMessage() },
                            enabled = inputText.isNotBlank() && !isSending,
                            modifier = Modifier.size(48.dp)
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            val suggestions = listOf(
                "Đánh giá tổng quan tình trạng máy của tôi hôm nay",
                "Phân tích xem pin của tôi có đang tụt nhanh không",
                "Phân tích các phiên sạc và thói quen sạc gần đây",
                "Hôm nay tôi dùng ứng dụng nào nhiều nhất?",
                "Kiểm tra NOBG và Kệ Đóng Băng đang hoạt động thế nào",
                "Bật giao diện tối cho NOBG"
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = PremiumDimens.ContentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PremiumDimens.ScreenGutter, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = PremiumAccent.Purple,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Trợ lý hiểu dữ liệu thật trên máy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Chọn một tác vụ để bắt đầu hoặc nhập câu hỏi riêng.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    suggestions.forEachIndexed { index, prompt ->
                        PremiumNavigationRow(
                            title = prompt,
                            icon = Icons.Filled.SmartToy,
                            accent = listOf(PremiumAccent.Blue, PremiumAccent.Green, PremiumAccent.Orange, PremiumAccent.Purple)[index % 4],
                            onClick = { chatViewModel.sendSuggestedPrompt(prompt) }
                        )
                        if (index < suggestions.lastIndex) PremiumInsetDivider()
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = PremiumDimens.ContentMaxWidth),
                state = listState,
                contentPadding = PaddingValues(horizontal = PremiumDimens.ScreenGutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageBubble(msg)
                }
                if (isSending) {
                    item {
                        Text(
                            "Đang suy nghĩ...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: AiChatMessage) {
    val isUser = message.role == AiChatRole.USER
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else if (message.isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (message.isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .background(bubbleColor, RoundedCornerShape(if (isUser) 16.dp else 16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}
