package com.nobg.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nobg.app.data.SmartTimerMode
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SmartTimerScreen(
    viewModel: SmartTimerViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val config by viewModel.configState.collectAsState()
    val quickConfig by viewModel.quickConfigState.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    var pendingStartAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingStartAction
        pendingStartAction = null
        if (granted) action?.invoke()
        else Toast.makeText(context, "Cần quyền thông báo để bộ đếm hoạt động liên tục", Toast.LENGTH_LONG).show()
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) action()
        else {
            pendingStartAction = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Đếm giờ", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = PremiumDimens.ContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PremiumDimens.ScreenGutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(PremiumDimens.GroupGap)
        ) {
            PremiumTimerStatus(
                isRunning = config.isRunning,
                elapsedSeconds = elapsedSeconds,
                durationMinutes = config.durationMinutes,
                intervalMinutes = config.intervalMinutes,
                onAction = {
                    if (config.isRunning) viewModel.stopTimer()
                    else runWithNotificationPermission(viewModel::startTimer)
                }
            )

            TimerPresetGroup { mode, duration, interval ->
                runWithNotificationPermission { viewModel.applyPreset(mode, duration, interval) }
            }

            TimerConfigurationGroup(
                title = "Chế độ nhanh của Widget",
                subtitle = "Thiết lập được dùng khi bạn chạm Widget 1×1.",
                headerIcon = Icons.Filled.Widgets,
                headerAccent = PremiumAccent.Purple,
                mode = quickConfig.mode,
                interval = quickConfig.intervalMinutes,
                duration = quickConfig.durationMinutes,
                intervalLabel = "Báo sau mỗi",
                durationLabel = "Tự dừng sau",
                intervalValues = listOf(1 to "1 phút", 2 to "2 phút", 3 to "3 phút", 5 to "5 phút", 10 to "10 phút", 15 to "15 phút"),
                onModeSelected = viewModel::setQuickMode,
                onIntervalSelected = viewModel::setQuickInterval,
                onDurationSelected = viewModel::setQuickDuration
            )

            TimerConfigurationGroup(
                title = "Cấu hình giọng đọc",
                subtitle = "Áp dụng cho bộ đếm chạy trực tiếp trong NOBG.",
                headerIcon = Icons.Filled.GraphicEq,
                headerAccent = PremiumAccent.Teal,
                mode = config.mode,
                interval = config.intervalMinutes,
                duration = config.durationMinutes,
                intervalLabel = "Chu kỳ thông báo",
                durationLabel = "Thời lượng tổng",
                intervalValues = listOf(1 to "1 phút", 2 to "2 phút", 3 to "3 phút", 5 to "5 phút", 10 to "10 phút", 15 to "15 phút", 30 to "30 phút"),
                onModeSelected = viewModel::setMode,
                onIntervalSelected = viewModel::setInterval,
                onDurationSelected = viewModel::setDuration
            )

            TimerAudioGroup(
                volume = config.volume,
                audioDucking = config.audioDucking,
                onVolumeChange = viewModel::setVolume,
                onDuckingChange = viewModel::setAudioDucking
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PremiumTimerStatus(
    isRunning: Boolean,
    elapsedSeconds: Long,
    durationMinutes: Int,
    intervalMinutes: Int,
    onAction: () -> Unit
) {
    val formattedTime = remember(elapsedSeconds) {
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isRunning) Icons.Filled.Timer else Icons.Filled.TimerOff,
                    contentDescription = null,
                    tint = if (isRunning) PremiumAccent.Green else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isRunning) "Đang đếm" else "Sẵn sàng",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (durationMinutes > 0) "Báo mỗi ${intervalMinutes} phút · dừng sau $durationMinutes phút"
                        else "Báo mỗi ${intervalMinutes} phút · không giới hạn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = formattedTime,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else PremiumAccent.Blue
                )
            ) {
                Icon(if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isRunning) "Dừng bộ đếm" else "Bắt đầu", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TimerPresetGroup(
    onSelect: (SmartTimerMode, Int, Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        TimerGroupHeader(
            title = "Mẫu nhanh",
            subtitle = "Bắt đầu ngay với thiết lập thường dùng.",
            icon = Icons.Filled.PlayArrow,
            accent = PremiumAccent.Blue
        )
        PremiumInsetDivider()
        PremiumNavigationRow(
            title = "Mặc định",
            subtitle = "Đọc giờ thực tế",
            icon = Icons.Filled.Schedule,
            accent = PremiumAccent.Blue,
            trailing = "1 giờ · mỗi 2p",
            onClick = { onSelect(SmartTimerMode.CLOCK_TIME, 60, 2) }
        )
        PremiumInsetDivider()
        PremiumNavigationRow(
            title = "15 phút",
            subtitle = "Đọc thời gian đã trôi qua",
            icon = Icons.Filled.Timer,
            accent = PremiumAccent.Green,
            trailing = "Mỗi 1p",
            onClick = { onSelect(SmartTimerMode.ELAPSED_TIME, 15, 1) }
        )
        PremiumInsetDivider()
        PremiumNavigationRow(
            title = "30 phút",
            subtitle = "Đọc thời gian đã trôi qua",
            icon = Icons.Filled.Timer,
            accent = PremiumAccent.Orange,
            trailing = "Mỗi 2p",
            onClick = { onSelect(SmartTimerMode.ELAPSED_TIME, 30, 2) }
        )
    }
}

@Composable
private fun TimerConfigurationGroup(
    title: String,
    subtitle: String,
    headerIcon: ImageVector,
    headerAccent: Color,
    mode: SmartTimerMode,
    interval: Int,
    duration: Int,
    intervalLabel: String,
    durationLabel: String,
    intervalValues: List<Pair<Int, String>>,
    onModeSelected: (SmartTimerMode) -> Unit,
    onIntervalSelected: (Int) -> Unit,
    onDurationSelected: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        TimerGroupHeader(title, subtitle, headerIcon, headerAccent)
        PremiumInsetDivider()
        TimerModeRow(
            title = "Giờ thực tế",
            subtitle = "Ví dụ: tám giờ hai mươi",
            icon = Icons.Filled.Schedule,
            accent = PremiumAccent.Blue,
            selected = mode == SmartTimerMode.CLOCK_TIME,
            onClick = { onModeSelected(SmartTimerMode.CLOCK_TIME) }
        )
        PremiumInsetDivider()
        TimerModeRow(
            title = "Thời gian đã trôi qua",
            subtitle = "Ví dụ: đã trôi qua mười phút",
            icon = Icons.Filled.Timer,
            accent = PremiumAccent.Teal,
            selected = mode == SmartTimerMode.ELAPSED_TIME,
            onClick = { onModeSelected(SmartTimerMode.ELAPSED_TIME) }
        )
        PremiumInsetDivider()
        TimerValueRow(
            title = intervalLabel,
            icon = Icons.Filled.NotificationsActive,
            accent = PremiumAccent.Orange,
            values = intervalValues,
            selectedValue = interval,
            onSelected = onIntervalSelected
        )
        PremiumInsetDivider()
        TimerValueRow(
            title = durationLabel,
            icon = Icons.Filled.HourglassBottom,
            accent = PremiumAccent.Purple,
            values = listOf(15 to "15 phút", 30 to "30 phút", 60 to "1 giờ", 120 to "2 giờ", 0 to "Không giới hạn"),
            selectedValue = duration,
            onSelected = onDurationSelected
        )
    }
}

@Composable
private fun TimerGroupHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(PremiumDimens.IconSize))
        Spacer(Modifier.width(PremiumDimens.IconTextGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimerModeRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PremiumDimens.RowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = PremiumDimens.RowHorizontalPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(PremiumDimens.IconSize))
        Spacer(Modifier.width(PremiumDimens.IconTextGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Đã chọn", tint = PremiumAccent.Blue, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun TimerValueRow(
    title: String,
    icon: ImageVector,
    accent: Color,
    values: List<Pair<Int, String>>,
    selectedValue: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = values.firstOrNull { it.first == selectedValue }?.second ?: selectedValue.toString()

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PremiumDimens.RowMinHeight)
                .clickable { expanded = true }
                .padding(horizontal = PremiumDimens.RowHorizontalPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(PremiumDimens.IconSize))
            Spacer(Modifier.width(PremiumDimens.IconTextGap))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(currentLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 8.dp).size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = if (value == selectedValue) {
                        { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = PremiumAccent.Blue) }
                    } else null,
                    onClick = {
                        onSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TimerAudioGroup(
    volume: Float,
    audioDucking: Boolean,
    onVolumeChange: (Float) -> Unit,
    onDuckingChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        TimerGroupHeader(
            title = "Âm thanh",
            subtitle = "Điều chỉnh giọng đọc và âm thanh nền.",
            icon = Icons.Filled.MusicNote,
            accent = PremiumAccent.Pink
        )
        PremiumInsetDivider()
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = PremiumAccent.Teal, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(20.dp))
                Text("Âm lượng giọng đọc", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text("${(volume * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(value = volume, onValueChange = onVolumeChange, modifier = Modifier.padding(start = 48.dp))
        }
        PremiumInsetDivider()
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = PremiumDimens.RowMinHeight).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = PremiumAccent.Orange, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Giảm tiếng nhạc khi đọc", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("Tự động hạ âm lượng nội dung đang phát", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = audioDucking, onCheckedChange = onDuckingChange)
        }
    }
}
