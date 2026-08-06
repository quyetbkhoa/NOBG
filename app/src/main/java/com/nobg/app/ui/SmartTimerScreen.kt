package com.nobg.app.ui

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
                onPresetSelect = { mode, duration, interval, autoShutdown ->
                    viewModel.applyPreset(mode, duration, interval, autoShutdown)
                }
            )

            ReaderConfigCard(
                mode = config.mode,
                interval = config.intervalMinutes,
                duration = config.durationMinutes,
                onModeSelected = { viewModel.setMode(it) },
                onIntervalSelected = { viewModel.setInterval(it) },
                onDurationSelected = { viewModel.setDuration(it) }
            )

            AutoShutdownCard(
                autoShutdown = config.autoShutdown,
                onToggle = { viewModel.setAutoShutdown(it) }
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
    onPresetSelect: (mode: SmartTimerMode, duration: Int, interval: Int, autoShutdown: Boolean) -> Unit
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onPresetSelect(SmartTimerMode.CLOCK_TIME, 60, 2, false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Mặc định", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("1h · 2p/lần", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                OutlinedButton(
                    onClick = { onPresetSelect(SmartTimerMode.ELAPSED_TIME, 15, 1, true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("15p Tắt máy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("1p/lần · tự tắt", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                OutlinedButton(
                    onClick = { onPresetSelect(SmartTimerMode.ELAPSED_TIME, 30, 2, false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("30 phút", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("2p/lần", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mode == SmartTimerMode.CLOCK_TIME,
                        onClick = { onModeSelected(SmartTimerMode.CLOCK_TIME) },
                        label = { Text("Giờ thực tế", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == SmartTimerMode.ELAPSED_TIME,
                        onClick = { onModeSelected(SmartTimerMode.ELAPSED_TIME) },
                        label = { Text("Thời gian trôi qua", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chu kỳ báo (1 lần mỗi)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                val intervals = listOf(1, 2, 3, 5, 10, 15, 30)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    intervals.take(4).forEach { i ->
                        FilterChip(
                            selected = interval == i,
                            onClick = { onIntervalSelected(i) },
                            label = { Text("${i}p") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    intervals.drop(4).forEach { i ->
                        FilterChip(
                            selected = interval == i,
                            onClick = { onIntervalSelected(i) },
                            label = { Text("${i}p") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Thời lượng tổng", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                val durations = listOf(
                    15 to "15p",
                    30 to "30p",
                    60 to "1h",
                    120 to "2h",
                    0 to "∞"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    durations.take(3).forEach { (d, label) ->
                        FilterChip(
                            selected = duration == d,
                            onClick = { onDurationSelected(d) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    durations.drop(3).forEach { (d, label) ->
                        FilterChip(
                            selected = duration == d,
                            onClick = { onDurationSelected(d) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
