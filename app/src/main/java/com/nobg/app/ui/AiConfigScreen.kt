package com.nobg.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nobg.app.data.AiClientFactory
import com.nobg.app.data.AiProvider
import com.nobg.app.data.AiResult
import com.nobg.app.data.NobgRepository
import kotlinx.coroutines.launch

/** Màn hình cấu hình AI (Gemini / Groq / OpenRouter) - feature riêng, không nằm trong Cài đặt */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigScreen(
    repo: NobgRepository,
    onBack: () -> Unit,
    onOpenAiChat: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var aiEnabled by remember { mutableStateOf(repo.isAiEnabled()) }
    var provider by remember { mutableStateOf(AiProvider.fromId(repo.getAiProvider())) }
    var apiKey by remember(provider) { mutableStateOf(repo.getAiProviderApiKey(provider)) }
    var model by remember(provider) { mutableStateOf(repo.getAiModel()) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testIsSuccess by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }

    fun saveApiKey(key: String) {
        apiKey = key
        repo.setAiProviderApiKey(provider, key)
        testResult = null
    }

    fun selectProvider(p: AiProvider) {
        if (p == provider) return
        provider = p
        repo.setAiProvider(p.id)
        apiKey = repo.getAiProviderApiKey(p)
        model = repo.getAiModel()
        testResult = null
    }

    fun saveModel(m: String) {
        model = m
        repo.setAiModel(m)
        testResult = null
    }

    fun runTestConnection() {
        if (apiKey.isBlank()) {
            testResult = "Vui lòng nhập API key trước khi kiểm tra."
            testIsSuccess = false
            return
        }
        isTesting = true
        testResult = null
        scope.launch {
            try {
                val result = AiClientFactory.create(repo).testConnection()
                when (result) {
                    is AiResult.Success -> {
                        testResult = "Kết nối thành công! ${provider.displayName} trả lời: \"${result.text.take(40)}\""
                        testIsSuccess = true
                    }
                    is AiResult.Error -> {
                        testResult = result.message
                        testIsSuccess = false
                    }
                }
            } catch (e: Exception) {
                testResult = "Lỗi không xác định: ${e.message ?: e.javaClass.simpleName}"
                testIsSuccess = false
            } finally {
                isTesting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🤖 AI Trợ lý", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🤖 AI (Gemini, Groq, OpenRouter) - Miễn phí",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Tóm tắt thông báo, lọc thông báo rác, chat AI",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = aiEnabled,
                            onCheckedChange = {
                                aiEnabled = it
                                repo.setAiEnabled(it)
                                if (!it) testResult = null
                            }
                        )
                    }

                    if (aiEnabled) {
                        Spacer(Modifier.height(12.dp))

                        // ── 0. NHÀ CUNG CẤP (PROVIDER) ───────────────────
                        Text(
                            "🔄 0. Nhà cung cấp (Provider)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Chọn dịch vụ AI. Mỗi provider dùng API key riêng.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        AiProvider.entries.forEach { p ->
                            val selected = p == provider
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable { selectProvider(p) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                                    }
                                ),
                                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selected, onClick = { selectProvider(p) })
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            p.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            p.shortDesc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── 1. API KEY ────────────────────────────────────
                        Text(
                            "🔑 1. API Key (${provider.displayName})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Lấy miễn phí tại: ${provider.keyUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { saveApiKey(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Dán API Key vào đây (${provider.keyHint})") },
                            placeholder = { Text(provider.keyHint) },
                            singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { showKey = !showKey }) {
                                    Text(if (showKey) "Ẩn" else "Hiện")
                                }
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(provider.keyUrl)
                                        )
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Không mở được trình duyệt", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🌐 Mở trang lấy API Key")
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── 2. MODEL AI ───────────────────────────────────
                        Text(
                            "🧠 2. Model AI",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Chọn model của ${provider.displayName} dùng cho tóm tắt, lọc và chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        var modelExpanded by remember { mutableStateOf(false) }
                        val suggestedModels = AiProvider.suggestedModels(provider)
                        val modelOptions = buildList {
                            addAll(suggestedModels)
                            if (model !in suggestedModels.map { it.first }) {
                                add(model to "✏️ Tùy chỉnh: $model")
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = modelOptions.firstOrNull { it.first == model }?.second ?: model,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                label = { Text("Model") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = modelExpanded,
                                onDismissRequest = { modelExpanded = false }
                            ) {
                                modelOptions.forEach { (modelId, modelLabel) ->
                                    DropdownMenuItem(
                                        text = { Text(modelLabel, maxLines = 1) },
                                        onClick = {
                                            saveModel(modelId)
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        val providerNote = when (provider) {
                            AiProvider.GEMINI ->
                                "Free tier ~10-15 req/phút. Nên tạo key tại Google AI Studio (aistudio.google.com/apikey) — key tạo ở Cloud Console thường bị quota = 0 (429 ngay lần đầu)."
                            AiProvider.GROQ ->
                                "Free không cần thẻ, giới hạn ~30 req/phút theo model. Key bắt đầu bằng gsk_."
                            AiProvider.OPENROUTER ->
                                "Model free (có đuôi :free) giới hạn ~50 req/ngày. Chọn model khác sẽ bị tính phí theo credit."
                        }
                        Text(
                            providerNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        // ── 3. KIỂM TRA & SỬ DỤNG ─────────────────────────
                        Text(
                            "🧪 3. Kiểm tra & Sử dụng",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = { runTestConnection() },
                            enabled = !isTesting && apiKey.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Đang kiểm tra...")
                            } else {
                                Text("🔌 Kiểm tra kết nối")
                            }
                        }

                        testResult?.let { result ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                result,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testIsSuccess) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = onOpenAiChat,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💬 Mở AI Chat")
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Bật \"Tóm tắt bằng AI\" và \"Lọc thông báo rác\" tại màn hình 🔔 Đọc thông báo.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
