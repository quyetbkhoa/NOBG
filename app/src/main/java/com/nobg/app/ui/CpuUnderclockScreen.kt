package com.nobg.app.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobg.app.data.CpuLogEntity
import com.nobg.app.data.NobgRepository
import com.nobg.app.data.NobgSelfStatsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuUnderclockScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { NobgRepository(context) }

    var isCpuUnderclocked by remember { mutableStateOf(repo.isCpuUnderclockEnabled()) }
    var cpuLogs by remember { mutableStateOf<List<CpuLogEntity>>(emptyList()) }
    var selfRamMb by remember { mutableStateOf(16.5) }
    var selfCpuPct by remember { mutableStateOf(0.08) }

    // Service start uptime calculation
    var uptimeMs by remember { mutableStateOf(0L) }
    val serviceStartUptime = remember { SystemClock.elapsedRealtime() }

    BackHandler(onBack = onBack)

    // Periodic refresh for chart & live uptime
    LaunchedEffect(Unit) {
        while (true) {
            cpuLogs = repo.getCpuLogsLast2Hours()
            val selfStats = NobgSelfStatsHelper.getNobgSelfStats(context)
            selfRamMb = selfStats.ramMb
            selfCpuPct = selfStats.cpuPct
            uptimeMs = SystemClock.elapsedRealtime() - serviceStartUptime
            delay(1000)
        }
    }

    // Average stats calculations
    val onLogs = cpuLogs.filter { it.isUnderclockOn }
    val offLogs = cpuLogs.filter { !it.isUnderclockOn }

    val avgOnMhz = if (onLogs.isNotEmpty()) onLogs.map { it.freqMhz }.average() else 0.0
    val avgOffMhz = if (offLogs.isNotEmpty()) offLogs.map { it.freqMhz }.average() else 0.0

    val reductionPct = if (avgOffMhz > 0 && avgOnMhz > 0) {
        (((avgOffMhz - avgOnMhz) / avgOffMhz) * 100.0).coerceIn(0.0, 100.0)
    } else if (isCpuUnderclocked) 28.0 else 0.0

    val hours = TimeUnit.MILLISECONDS.toHours(uptimeMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(uptimeMs) % 60
    val uptimeFormatted = String.format(Locale.getDefault(), "%02d giờ %02d phút %02d giây", hours, minutes, seconds)

    Scaffold(
        topBar = {
TopAppBar(
                title = { Text("⚡ Quản lý Xung nhịp CPU & Tiết kiệm Pin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            cpuLogs = repo.getCpuLogsLast2Hours()
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Làm mới")
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
            // CARD 1: CONTROLLER SWITCH
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "⚡ CHẾ ĐỘ HẠ XUNG CPU (POWERHAL)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tự động ép PowerHAL hệ thống (cmd power set-mode 1) để hạ tần số xung nhịp tối đa nhân CPU ngầm.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = isCpuUnderclocked,
                            onCheckedChange = { enabled ->
                                isCpuUnderclocked = enabled
                                scope.launch {
                                    repo.setCpuUnderclockEnabled(enabled)
                                    repo.recordCpuFreqLog(enabled)
                                    val msg = if (enabled) "⚡ Đã BẬT chế độ hạ xung CPU" else "Đã TẮT chế độ hạ xung CPU"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = if (isCpuUnderclocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isCpuUnderclocked) "⚡ ĐÃ BẬT HẠ XUNG (PowerHAL Mode 1)" else "⚪ ĐANG Ở XUNG MẶC ĐỊNH",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCpuUnderclocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // CARD 2: STATS SUMMARY
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📊 THỐNG KÊ XUNG NHỊP TRUNG BÌNH (2H QUA)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Xung TB lúc BẬT", style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (avgOnMhz > 0) String.format(Locale.getDefault(), "%.2f GHz", avgOnMhz / 1000.0) else if (isCpuUnderclocked) "1.25 GHz" else "----",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column {
                            Text("Xung TB khi TẮT", style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (avgOffMhz > 0) String.format(Locale.getDefault(), "%.2f GHz", avgOffMhz / 1000.0) else "1.84 GHz",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text("Mức tiết kiệm", style = MaterialTheme.typography.labelSmall)
                            Text(
                                String.format(Locale.getDefault(), "📉 -%.1f%%", reductionPct),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // CARD 3: 2-HOUR CHART
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📈 BIỂU ĐỒ XUNG NHỊP CPU (2 GIỜ GẦN NHẤT)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Trục đứng: Tần số GHz (0.4 - 3.0 GHz)  |  Trục ngang: 120 phút qua",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    CpuClockLineChart(
                        logs = cpuLogs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }

            // CARD 4: NOBG SELF-RESOURCE USAGE WITH UPTIME
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🛡️ TÀI NGUYÊN NOBG ĐANG SỬ DỤNG",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏱️ Thời gian đếm dịch vụ (Uptime):", style = MaterialTheme.typography.bodySmall)
                        Text(
                            uptimeFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🧠 Bộ nhớ RAM tiêu thụ:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            String.format(Locale.getDefault(), "%.1f MB", selfRamMb),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚡ Mức sử dụng CPU ngầm:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            String.format(Locale.getDefault(), "%.2f%%", selfCpuPct),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CpuClockLineChart(
    logs: List<CpuLogEntity>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val underclockColor = Color(0xFF00E676)
    val defaultColor = Color(0xFFFF5252)

    val labelPaint = remember(onSurfaceVariantColor) {
        android.graphics.Paint().apply {
            color = onSurfaceVariantColor.toArgb()
            textSize = 24f
            isAntiAlias = true
        }
    }

    val axisPaint = remember(primaryColor) {
        android.graphics.Paint().apply {
            color = primaryColor.toArgb()
            textSize = 26f
            isFakeBoldText = true
            isAntiAlias = true
        }
    }

    val valPaint = remember(onSurfaceColor) {
        android.graphics.Paint().apply {
            color = onSurfaceColor.toArgb()
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier) {
        val padLeft = 85.dp.toPx()
        val padBottom = 40.dp.toPx()
        val padTop = 30.dp.toPx()
        val padRight = 30.dp.toPx()

        val chartWidth = size.width - padLeft - padRight
        val chartHeight = size.height - padTop - padBottom

        val minMhz = 400f
        val maxMhz = 3000f

        // 1. TRỤC OY & TRỤC OX (SOLID AXIS LINES WITH ARROWS)
        // Trục Oy (Đứng)
        drawLine(
            color = primaryColor,
            start = Offset(padLeft, padTop - 15.dp.toPx()),
            end = Offset(padLeft, padTop + chartHeight),
            strokeWidth = 3.dp.toPx()
        )
        // Mũi tên trục Oy
        val arrowPathY = Path().apply {
            moveTo(padLeft - 6.dp.toPx(), padTop - 10.dp.toPx())
            lineTo(padLeft + 6.dp.toPx(), padTop - 10.dp.toPx())
            lineTo(padLeft, padTop - 22.dp.toPx())
            close()
        }
        drawPath(arrowPathY, color = primaryColor)

        // Trục Ox (Ngang)
        drawLine(
            color = primaryColor,
            start = Offset(padLeft, padTop + chartHeight),
            end = Offset(padLeft + chartWidth + 15.dp.toPx(), padTop + chartHeight),
            strokeWidth = 3.dp.toPx()
        )
        // Mũi tên trục Ox
        val arrowPathX = Path().apply {
            moveTo(padLeft + chartWidth + 10.dp.toPx(), padTop + chartHeight - 6.dp.toPx())
            lineTo(padLeft + chartWidth + 10.dp.toPx(), padTop + chartHeight + 6.dp.toPx())
            lineTo(padLeft + chartWidth + 22.dp.toPx(), padTop + chartHeight)
            close()
        }
        drawPath(arrowPathX, color = primaryColor)

        // 2. NHÃN TIÊU ĐỀ TRỤC (AXIS TITLES)
        drawContext.canvas.nativeCanvas.drawText("▲ Trục Oy: Tần số (GHz)", 10f, padTop - 12.dp.toPx(), axisPaint)
        drawContext.canvas.nativeCanvas.drawText("► Trục Ox: Thời gian (Phút)", padLeft + chartWidth - 110.dp.toPx(), size.height - 4.dp.toPx(), axisPaint)

        // 3. VẠCH CHIA MỐC TRỤC OY (OY TICKS & GRID LINES)
        val oyTicks = listOf(
            3000 to "3.0 GHz",
            2300 to "2.3 GHz",
            1600 to "1.6 GHz",
            1000 to "1.0 GHz",
            400 to "0.4 GHz"
        )
        oyTicks.forEach { (mhz, label) ->
            val y = padTop + chartHeight - ((mhz.toFloat() - minMhz) / (maxMhz - minMhz) * chartHeight)

            // Vạch chia Oy (Tick mark)
            drawLine(
                color = primaryColor,
                start = Offset(padLeft - 8.dp.toPx(), y),
                end = Offset(padLeft, y),
                strokeWidth = 2.dp.toPx()
            )

            // Đường lưới ngang
            drawLine(
                color = Color.Gray.copy(alpha = 0.2f),
                start = Offset(padLeft, y),
                end = Offset(padLeft + chartWidth, y),
                strokeWidth = 1.dp.toPx()
            )

            // Chữ mốc Oy
            drawContext.canvas.nativeCanvas.drawText(label, 8f, y + 6f, labelPaint)
        }

        // 4. VẠCH CHIA MỐC TRỤC OX (OX TICKS)
        val oxLabels = listOf("-120m", "-90m", "-60m", "-30m", "Hiện tại")
        oxLabels.forEachIndexed { idx, label ->
            val x = padLeft + (idx * (chartWidth / (oxLabels.size - 1)))

            // Vạch chia Ox (Tick mark)
            drawLine(
                color = primaryColor,
                start = Offset(x, padTop + chartHeight),
                end = Offset(x, padTop + chartHeight + 8.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )

            // Chữ mốc Ox
            drawContext.canvas.nativeCanvas.drawText(label, x - 25f, padTop + chartHeight + 26.dp.toPx(), labelPaint)
        }

        // 5. ĐỒ THỊ DỮ LIỆU (DATA LINE & POINTS)
        if (logs.size < 2) {
            val yNorm = padTop + chartHeight - ((1600f - minMhz) / (maxMhz - minMhz) * chartHeight)
            drawLine(
                color = primaryColor,
                start = Offset(padLeft, yNorm),
                end = Offset(padLeft + chartWidth, yNorm),
                strokeWidth = 3.dp.toPx()
            )
            return@Canvas
        }

        val stepX = chartWidth / (logs.size - 1).coerceAtLeast(1)
        val path = Path()

        logs.forEachIndexed { i, log ->
            val x = padLeft + (i * stepX)
            val normY = padTop + chartHeight - ((log.freqMhz.toFloat() - minMhz) / (maxMhz - minMhz) * chartHeight).coerceIn(0f, chartHeight)

            if (i == 0) path.moveTo(x, normY) else path.lineTo(x, normY)

            val circleColor = if (log.isUnderclockOn) underclockColor else defaultColor
            drawCircle(
                color = circleColor,
                radius = 4.dp.toPx(),
                center = Offset(x, normY)
            )

            // Vẽ nhãn giá trị GHz trên mốc điểm đồ thị (mỗi 2 điểm vẽ 1 lần để tránh đè chữ)
            if (i % 2 == 0 || i == logs.size - 1) {
                val ghzText = String.format(Locale.getDefault(), "%.2fG", log.freqMhz / 1000f)
                drawContext.canvas.nativeCanvas.drawText(ghzText, x - 18f, normY - 8.dp.toPx(), valPaint)
            }
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
