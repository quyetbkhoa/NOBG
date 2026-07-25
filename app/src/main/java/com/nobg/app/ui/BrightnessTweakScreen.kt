package com.nobg.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobg.app.data.NobgRepository
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrightnessTweakScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { NobgRepository(context) }

    // Mode 1: Min Cap
    var isMinCapEnabled by remember { mutableStateOf(repo.isMinBrightnessEnabled()) }
    var minCapValue by remember { mutableStateOf(repo.getMinBrightnessValue().toFloat()) }

    // Mode 2: Offset
    var isOffsetEnabled by remember { mutableStateOf(repo.isAutoBrightnessOffsetEnabled()) }
    var offsetValue by remember { mutableStateOf(repo.getAutoBrightnessOffset()) }

    // Mode 3: Extra Dim
    var isExtraDimEnabled by remember { mutableStateOf(repo.isExtraDimEnabled()) }
    var extraDimLevel by remember { mutableStateOf(repo.getExtraDimLevel().toFloat()) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("💡 Độ sáng Tối thiểu & Offset") },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CARD 1: MIN CAP MODE (SÀN ĐỘ SÁNG TỐI THIỂU)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🔆 MODE 1: SÀN ĐỘ SÁNG TỐI THIỂU (MIN CAP)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Khóa không cho độ sáng tự động bị tụt xuống quá tối ban đêm. Giữ màn hình ở mức dịu mắt.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = isMinCapEnabled,
                            onCheckedChange = { enabled ->
                                isMinCapEnabled = enabled
                                scope.launch {
                                    repo.setMinBrightness(enabled, minCapValue.toInt())
                                    val msg = if (enabled) "⚡ Đã BẬT Sàn độ sáng tối thiểu (${minCapValue.toInt()}/255)" else "Đã TẮT Sàn độ sáng tối thiểu"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Mức độ sáng sàn tối thiểu: ${minCapValue.toInt()} / 255 (${String.format(Locale.getDefault(), "%.1f", (minCapValue / 255f) * 100)}%)",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))

                    Slider(
                        value = minCapValue,
                        onValueChange = { newValue ->
                            minCapValue = newValue
                            if (isMinCapEnabled) {
                                scope.launch {
                                    repo.setMinBrightness(true, newValue.toInt())
                                }
                            }
                        },
                        onValueChangeFinished = {
                            if (isMinCapEnabled) {
                                scope.launch {
                                    repo.setMinBrightness(true, minCapValue.toInt())
                                }
                            }
                        },
                        valueRange = 5f..60f,
                        steps = 55,
                        enabled = isMinCapEnabled
                    )
                }
            }

            // CARD 2: AUTO BRIGHTNESS OFFSET MODE (BÙ ĐỘ SÁNG TỰ ĐỘNG)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🌗 MODE 2: BÙ ĐỘ SÁNG TỰ ĐỘNG (OFFSET)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Luôn làm cho màn hình sáng hơn (+) hoặc tối hơn (-) một chút so với độ sáng cảm biến gốc của Android.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = isOffsetEnabled,
                            onCheckedChange = { enabled ->
                                isOffsetEnabled = enabled
                                scope.launch {
                                    repo.setAutoBrightnessOffset(enabled, offsetValue)
                                    val msg = if (enabled) "⚡ Đã BẬT Bù độ sáng (${String.format(Locale.getDefault(), "%+.2f", offsetValue)})" else "Đã TẮT Bù độ sáng"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    val offsetText = when {
                        offsetValue > 0 -> "Sáng hơn ${String.format(Locale.getDefault(), "+%.2f", offsetValue)}"
                        offsetValue < 0 -> "Tối hơn ${String.format(Locale.getDefault(), "%.2f", offsetValue)}"
                        else -> "Mặc định hệ thống (0.00)"
                    }
                    Text(
                        "Mức độ lệch Bù độ sáng: $offsetText",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(4.dp))

                    Slider(
                        value = offsetValue,
                        onValueChange = { newValue ->
                            offsetValue = newValue
                            if (isOffsetEnabled) {
                                scope.launch {
                                    repo.setAutoBrightnessOffset(true, newValue)
                                }
                            }
                        },
                        onValueChangeFinished = {
                            if (isOffsetEnabled) {
                                scope.launch {
                                    repo.setAutoBrightnessOffset(true, offsetValue)
                                }
                            }
                        },
                        valueRange = -0.50f..0.50f,
                        steps = 20,
                        enabled = isOffsetEnabled
                    )
                }
            }

            // CARD 3: EXTRA DIM MODE (SIÊU TỐI BAN ĐÊM - ANDROID 12+)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🌙 CHẾ ĐỘ SIÊU TỐI BAN ĐÊM (EXTRA DIM)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Giảm chói mắt ban đêm bằng phần mềm mà không ảnh hưởng đèn nền phần cứng (Android 12+).",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = isExtraDimEnabled,
                            onCheckedChange = { enabled ->
                                isExtraDimEnabled = enabled
                                scope.launch {
                                    repo.setExtraDim(enabled, extraDimLevel.toInt())
                                    val msg = if (enabled) "🌙 Đã BẬT Siêu tối đêm (${extraDimLevel.toInt()}%)" else "Đã TẮT Siêu tối đêm"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Mức độ giảm chói ánh sáng trắng: ${extraDimLevel.toInt()}%",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(4.dp))

                    Slider(
                        value = extraDimLevel,
                        onValueChange = { newValue ->
                            extraDimLevel = newValue
                            if (isExtraDimEnabled) {
                                scope.launch {
                                    repo.setExtraDim(true, newValue.toInt())
                                }
                            }
                        },
                        onValueChangeFinished = {
                            if (isExtraDimEnabled) {
                                scope.launch {
                                    repo.setExtraDim(true, extraDimLevel.toInt())
                                }
                            }
                        },
                        valueRange = 10f..80f,
                        steps = 14,
                        enabled = isExtraDimEnabled
                    )
                }
            }
        }
    }
}
