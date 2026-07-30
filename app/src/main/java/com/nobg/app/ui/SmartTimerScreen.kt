package com.nobg.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobg.app.data.SmartTimerMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartTimerScreen(
    viewModel: SmartTimerViewModel,
    onBack: () -> Unit
) {
    val config by viewModel.configState.collectAsState()
    val elapsedSec by viewModel.elapsedSeconds.collectAsState()

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
            // Main Timer Display Card
            TimerStatusCard(
                isRunning = config.isRunning,
                elapsedSeconds = elapsedSec,
                durationMinutes = config.durationMinutes,
                onStart = { viewModel.startTimer() },
                onStop = { viewModel.stopTimer() }
            )

            // Quick Preset Bar
            QuickPresetsCard(
                onPresetSelect = { duration, interval, autoShutdown ->
                    viewModel.startQuickPreset(duration, interval, autoShutdown)
                }
            )

            // Mode Selection
            ModeSelectionCard(
                selectedMode = config.mode,
                onModeSelected = { viewModel.setMode(it) }
            )

            // Interval Picker Card
            IntervalPickerCard(
                selectedInterval = config.intervalMinutes,
                onIntervalSelected = { viewModel.setInterval(it) }
            )

            // Duration Picker Card
            DurationPickerCard(
                selectedDuration = config.durationMinutes,
                onDurationSelected = { viewModel.setDuration(it) }
            )

            // Auto Shutdown Card
            AutoShutdownCard(
                autoShutdown = config.autoShutdown,
                onToggle = { viewModel.setAutoShutdown(it) }
            )

            // Audio & Volume Settings Card
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
                text = if (isRunning) {
                    if (durationMinutes > 0) "Đang đếm ngầm (Giới hạn $durationMinutes phút)" else "Đang đếm ngầm (Liên tục)"
                } else "Đã dừng đếm",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onPresetSelect: (duration: Int, interval: Int, autoShutdown: Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "⚡ Kích hoạt nhanh Preset",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onPresetSelect(60, 1, false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("1h (1p/lần)", fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                OutlinedButton(
                    onClick = { onPresetSelect(15, 1, true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("15p Tắt máy", fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                OutlinedButton(
                    onClick = { onPresetSelect(30, 2, false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("30p (2p/lần)", fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ModeSelectionCard(
    selectedMode: SmartTimerMode,
    onModeSelected: (SmartTimerMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🗣️ Chế độ giọng đọc", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedMode == SmartTimerMode.ELAPSED_TIME,
                    onClick = { onModeSelected(SmartTimerMode.ELAPSED_TIME) },
                    label = { Text("Thức đếm (1 phút, 2 phút...)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedMode == SmartTimerMode.CLOCK_TIME,
                    onClick = { onModeSelected(SmartTimerMode.CLOCK_TIME) },
                    label = { Text("Giờ thực (8h01 -> Tám giờ 01)") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun IntervalPickerCard(
    selectedInterval: Int,
    onIntervalSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⏱️ Khoảng cách giữa các lần báo", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            val intervals = listOf(1, 2, 3, 5, 10, 15, 30)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                intervals.take(4).forEach { interval ->
                    FilterChip(
                        selected = selectedInterval == interval,
                        onClick = { onIntervalSelected(interval) },
                        label = { Text("${interval}p") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                intervals.drop(4).forEach { interval ->
                    FilterChip(
                        selected = selectedInterval == interval,
                        onClick = { onIntervalSelected(interval) },
                        label = { Text("${interval}p") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationPickerCard(
    selectedDuration: Int,
    onDurationSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⏳ Tổng thời lượng đếm", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            val durations = listOf(
                15 to "15 phút",
                30 to "30 phút",
                60 to "1 giờ",
                120 to "2 giờ",
                0 to "Không giới hạn"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                durations.take(3).forEach { (dur, label) ->
                    FilterChip(
                        selected = selectedDuration == dur,
                        onClick = { onDurationSelected(dur) },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                durations.drop(3).forEach { (dur, label) ->
                    FilterChip(
                        selected = selectedDuration == dur,
                        onClick = { onDurationSelected(dur) },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoShutdownCard(
    autoShutdown: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("⚡ Hẹn giờ tắt máy khi hết giờ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Tự động tắt máy (qua Shizuku ADB) khi thời lượng đếm kết thúc",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoShutdown,
                onCheckedChange = onToggle
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
