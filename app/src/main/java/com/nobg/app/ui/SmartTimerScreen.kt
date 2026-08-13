package com.nobg.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.nobg.app.data.SmartTimerMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartTimerScreen(
    viewModel: SmartTimerViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val config by viewModel.configState.collectAsState()
    val quickConfig by viewModel.quickConfigState.collectAsState()
    val elapsedSec by viewModel.elapsedSeconds.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đếm giờ thông minh", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TimerStatusCard(
                isRunning = config.isRunning,
                elapsedSeconds = elapsedSec,
                durationMinutes = config.durationMinutes,
                intervalMinutes = config.intervalMinutes,
                mode = config.mode,
                onStart = { viewModel.startTimer() },
                onStop = { viewModel.stopTimer() }
            )

            QuickPresetsCard(
                onPresetSelect = { mode, duration, interval ->
                    viewModel.applyPreset(mode, duration, interval)
                }
            )

            WidgetQuickConfigCard(
                mode = quickConfig.mode,
                interval = quickConfig.intervalMinutes,
                duration = quickConfig.durationMinutes,
                onModeSelected = viewModel::setQuickMode,
                onIntervalSelected = viewModel::setQuickInterval,
                onDurationSelected = viewModel::setQuickDuration
            )

            ReaderConfigCard(
                mode = config.mode,
                interval = config.intervalMinutes,
                duration = config.durationMinutes,
                onModeSelected = { viewModel.setMode(it) },
                onIntervalSelected = { viewModel.setInterval(it) },
                onDurationSelected = { viewModel.setDuration(it) }
            )

            AudioSettingsCard(
                volume = config.volume,
                audioDucking = config.audioDucking,
                onVolumeChange = { viewModel.setVolume(it) },
                onDuckingChange = { viewModel.setAudioDucking(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TimerStatusCard(
    isRunning: Boolean,
    elapsedSeconds: Long,
    durationMinutes: Int,
    intervalMinutes: Int,
    mode: SmartTimerMode,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Timer else Icons.Default.TimerOff,
                        contentDescription = null,
                        tint = if (isRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            val formattedTime = remember(elapsedSeconds) {
                val hours = elapsedSeconds / 3600
                val mins = (elapsedSeconds % 3600) / 60
                val secs = elapsedSeconds % 60
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs)
            }

            Text(
                text = formattedTime,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = when {
                    !isRunning -> "Đã dừng · sẵn sàng đếm"
                    durationMinutes > 0 -> "Báo mỗi ${intervalMinutes}p · giới hạn $durationMinutes phút"
                    else -> "Báo mỗi ${intervalMinutes}p · không giới hạn"
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = if (isRunning) onStop else onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "DỪNG LẠI" else "BẮT ĐẦU ĐẾM",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun QuickPresetsCard(
    onPresetSelect: (mode: SmartTimerMode, duration: Int, interval: Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "⚡ Preset nhanh",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onPresetSelect(SmartTimerMode.CLOCK_TIME, 60, 2) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mặc định", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Giờ thực · 1h · báo mỗi 2p", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                OutlinedButton(
                    onClick = { onPresetSelect(SmartTimerMode.ELAPSED_TIME, 15, 1) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("15 phút", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Đã trôi qua · báo mỗi 1p", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                OutlinedButton(
                    onClick = { onPresetSelect(SmartTimerMode.ELAPSED_TIME, 30, 2) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("30 phút", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Đã trôi qua · báo mỗi 2p", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetQuickConfigCard(
    mode: SmartTimerMode,
    interval: Int,
    duration: Int,
    onModeSelected: (SmartTimerMode) -> Unit,
    onIntervalSelected: (Int) -> Unit,
    onDurationSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("▣ Chế độ nhanh của Widget", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Bấm widget 1x1 để bắt đầu với các thiết lập dưới đây; bấm lại để dừng.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )

            Text("Loại giờ được đọc", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = mode == SmartTimerMode.CLOCK_TIME,
                    onClick = { onModeSelected(SmartTimerMode.CLOCK_TIME) },
                    label = { Text("Giờ thực tế (ví dụ: 8 giờ 20)") },
                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )
                FilterChip(
                    selected = mode == SmartTimerMode.ELAPSED_TIME,
                    onClick = { onModeSelected(SmartTimerMode.ELAPSED_TIME) },
                    label = { Text("Thời gian đã trôi qua") },
                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()
            TimerOptionChips(
                title = "Báo sau mỗi",
                values = listOf(1 to "1p", 2 to "2p", 3 to "3p", 5 to "5p", 10 to "10p", 15 to "15p"),
                selectedValue = interval,
                onSelected = onIntervalSelected
            )

            HorizontalDivider()
            TimerOptionChips(
                title = "Tự dừng sau",
                values = listOf(15 to "15p", 30 to "30p", 60 to "1h", 120 to "2h", 0 to "∞ Không GH"),
                selectedValue = duration,
                onSelected = onDurationSelected
            )
        }
    }
}

@Composable
private fun TimerOptionChips(
    title: String,
    values: List<Pair<Int, String>>,
    selectedValue: Int,
    onSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        values.chunked(3).forEach { rowValues ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowValues.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedValue == value,
                        onClick = { onSelected(value) },
                        label = { Text(label, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowValues.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReaderConfigCard(
    mode: SmartTimerMode,
    interval: Int,
    duration: Int,
    onModeSelected: (SmartTimerMode) -> Unit,
    onIntervalSelected: (Int) -> Unit,
    onDurationSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("⚙️ Cấu hình giọng đọc", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chế độ đọc", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == SmartTimerMode.CLOCK_TIME,
                        onClick = { onModeSelected(SmartTimerMode.CLOCK_TIME) },
                        label = { Text("Giờ thực tế (ví dụ: 8 giờ 20)", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    FilterChip(
                        selected = mode == SmartTimerMode.ELAPSED_TIME,
                        onClick = { onModeSelected(SmartTimerMode.ELAPSED_TIME) },
                        label = { Text("Thời gian trôi qua", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider()
            TimerOptionChips(
                title = "Chu kỳ báo (1 lần mỗi)",
                values = listOf(1, 2, 3, 5, 10, 15, 30).map { it to "${it}p" },
                selectedValue = interval,
                onSelected = onIntervalSelected
            )

            HorizontalDivider()
            TimerOptionChips(
                title = "Thời lượng tổng",
                values = listOf(
                    15 to "15p",
                    30 to "30p",
                    60 to "1h",
                    120 to "2h",
                    0 to "∞"
                ),
                selectedValue = duration,
                onSelected = onDurationSelected
            )
        }
    }
}

@Composable
private fun AudioSettingsCard(
    volume: Float,
    audioDucking: Boolean,
    onVolumeChange: (Float) -> Unit,
    onDuckingChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🔊 Âm thanh & Giảm tiếng nhạc nền", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Âm lượng giọng đọc", fontSize = 14.sp)
                    Text("${(volume * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0.0f..1.0f
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Thu nhỏ audio/nhạc nền khi đọc", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Tự động giảm âm lượng nhạc đang phát khi TTS phát âm", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = audioDucking,
                    onCheckedChange = onDuckingChange
                )
            }
        }
    }
}
