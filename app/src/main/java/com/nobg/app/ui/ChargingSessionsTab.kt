package com.nobg.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobg.app.data.ChargingPredictor
import com.nobg.app.data.ChargingSessionEntity
import com.nobg.app.data.SpeedStepPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChargingSessionsTab(viewModel: BatteryStatsViewModel) {
    val sessions by viewModel.chargingSessions.collectAsState()
    val isFullSoundEnabled by viewModel.isFullBatterySoundEnabled.collectAsState()
    val currentPrediction by viewModel.predictionResult.collectAsState()
    val overview by viewModel.overview.collectAsState()

    var selectedSessionForDialog by remember { mutableStateOf<ChargingSessionEntity?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    if (selectedSessionForDialog != null) {
        ChargingSessionDetailDialog(
            session = selectedSessionForDialog!!,
            onDismiss = { selectedSessionForDialog = null }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Xóa lịch sử phiên sạc") },
            text = { Text("Bạn có chắc chắn muốn xóa toàn bộ lịch sử tất cả các phiên sạc không?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllChargingSessions()
                    showClearConfirm = false
                }) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Hủy") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Prediction & Sound Alert Toggle
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "⚡ Dự đoán sạc đầy (Phi tuyến tính)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    if (overview.timeToFullMinutes >= 0 && overview.currentChargeLevel >= 0) {
                        Text(
                            text = "Pin ${overview.currentChargeLevel}% → Dự kiến sạc đầy 100% sau khoảng ${currentPrediction.remainingMinutes} phút",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Text(
                            text = "🔌 Thiết bị hiện đang dùng pin. Dự đoán dựa trên trung bình ${sessions.size} phiên sạc lịch sử.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Column {
                                Text(
                                    text = "Âm thanh khi đầy 100%",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Phát chuông thông báo khi sạc đạt 100%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Switch(
                            checked = isFullSoundEnabled,
                            onCheckedChange = viewModel::setFullBatterySoundEnabled
                        )
                    }
                }
            }
        }

        // Card 2: Overall Speed Chart (Ox: % Pin, Oy: Thời gian sạc)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "📈 Biểu đồ Tốc độ Sạc Tổng hợp (0% → 100%)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Trục Ox: % Pin  |  Trục Oy: Thời gian sạc tích lũy (phút)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SpeedCumulativeChart(
                        points = currentPrediction.curvePoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }

        // Card 3: Sessions List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lịch sử các phiên sạc (${sessions.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (sessions.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa lịch sử", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chưa có phiên sạc nào được lưu.\nHãy cắm sạc để ứng dụng ghi nhận biểu đồ.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                SessionItemRow(
                    session = session,
                    onClick = { selectedSessionForDialog = session }
                )
            }
        }
    }
}

@Composable
private fun SessionItemRow(
    session: ChargingSessionEntity,
    onClick: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${session.startLevel}% → ${session.endLevel}%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (session.isCompletedToFull) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (session.isCompletedToFull) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Sạc đầy 100%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sdf.format(Date(session.startTimeMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDurationShort(session.totalDurationSeconds * 1000L),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Bấm xem biểu đồ →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingSessionDetailDialog(
    session: ChargingSessionEntity,
    onDismiss: () -> Unit
) {
    val points = remember(session) { ChargingPredictor.parsePointsJson(session.pointsJson) }
    val sdf = remember { SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        },
        title = {
            Column {
                Text("Biểu đồ phiên sạc", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    sdf.format(Date(session.startTimeMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mức sạc: ${session.startLevel}% → ${session.endLevel}%", fontWeight = FontWeight.SemiBold)
                    Text("Thời gian: ${formatDurationShort(session.totalDurationSeconds * 1000L)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Text(
                    "Trục Ox: % Pin  |  Trục Oy: Thời gian sạc (phút)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IndividualSessionChart(
                    points = points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    )
}

@Composable
private fun SpeedCumulativeChart(points: List<SpeedStepPoint>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    val labelPaint = remember(onSurfaceVariantColor) {
        android.graphics.Paint().apply {
            color = onSurfaceVariantColor.toArgb()
            textSize = 22f
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas

        val padLeft = 55.dp.toPx()
        val padBottom = 28.dp.toPx()
        val padTop = 15.dp.toPx()
        val padRight = 15.dp.toPx()

        val chartW = size.width - padLeft - padRight
        val chartH = size.height - padTop - padBottom

        val maxMinutes = points.maxOfOrNull { it.cumulativeMinutes }?.coerceAtLeast(1f) ?: 60f

        // Draw Oy & Ox Axis lines
        drawLine(primaryColor.copy(alpha = 0.7f), Offset(padLeft, padTop), Offset(padLeft, padTop + chartH), strokeWidth = 2.dp.toPx())
        drawLine(primaryColor.copy(alpha = 0.7f), Offset(padLeft, padTop + chartH), Offset(padLeft + chartW, padTop + chartH), strokeWidth = 2.dp.toPx())

        // Grid lines & Oy Axis (Thời gian sạc phút) Labels
        for (i in 0..4) {
            val ratio = i / 4f
            val y = padTop + chartH * (1f - ratio)
            val minVal = (maxMinutes * ratio).toInt()
            drawLine(gridColor, Offset(padLeft, y), Offset(padLeft + chartW, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("${minVal}m", 8f, y + 6f, labelPaint)
        }

        // Ox Axis (% Pin) Labels
        val stepPct = listOf(0, 25, 50, 75, 100)
        for (pctVal in stepPct) {
            val x = padLeft + (pctVal / 100f) * chartW
            drawLine(primaryColor.copy(alpha = 0.5f), Offset(x, padTop + chartH), Offset(x, padTop + chartH + 4.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("$pctVal%", x - 12f, size.height - 4f, labelPaint)
        }

        // Line Path
        val path = Path()
        val linePath = Path()

        points.forEachIndexed { idx, pt ->
            val x = padLeft + (pt.batteryPct / 100f) * chartW
            val y = padTop + chartH * (1f - (pt.cumulativeMinutes / maxMinutes))

            if (idx == 0) {
                path.moveTo(x, y)
                linePath.moveTo(x, y)
            } else {
                path.lineTo(x, y)
                linePath.lineTo(x, y)
            }
        }

        path.lineTo(padLeft + chartW, padTop + chartH)
        path.lineTo(padLeft, padTop + chartH)
        path.close()

        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.4f), primaryColor.copy(alpha = 0.05f)),
                startY = padTop, endY = padTop + chartH
            )
        )

        drawPath(
            path = linePath,
            color = primaryColor,
            style = Stroke(width = 2.5.dp.toPx())
        )

        points.forEach { pt ->
            val x = padLeft + (pt.batteryPct / 100f) * chartW
            val y = padTop + chartH * (1f - (pt.cumulativeMinutes / maxMinutes))
            drawCircle(primaryColor, radius = 3.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun IndividualSessionChart(points: List<com.nobg.app.data.ChargingPoint>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    val labelPaint = remember(onSurfaceVariantColor) {
        android.graphics.Paint().apply {
            color = onSurfaceVariantColor.toArgb()
            textSize = 22f
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val startTs = points.first().timestampMs
        val totalSec = ((points.last().timestampMs - startTs) / 1000f).coerceAtLeast(1f)
        val totalMin = totalSec / 60f
        val minPct = points.minOf { it.batteryPct }
        val maxPct = points.maxOf { it.batteryPct }
        val pctDiff = (maxPct - minPct).coerceAtLeast(1)

        val padLeft = 55.dp.toPx()
        val padBottom = 28.dp.toPx()
        val padTop = 15.dp.toPx()
        val padRight = 15.dp.toPx()

        val chartW = size.width - padLeft - padRight
        val chartH = size.height - padTop - padBottom

        // Draw Oy & Ox Axis lines
        drawLine(primaryColor.copy(alpha = 0.7f), Offset(padLeft, padTop), Offset(padLeft, padTop + chartH), strokeWidth = 2.dp.toPx())
        drawLine(primaryColor.copy(alpha = 0.7f), Offset(padLeft, padTop + chartH), Offset(padLeft + chartW, padTop + chartH), strokeWidth = 2.dp.toPx())

        // Grid lines & Oy Axis (Thời gian sạc phút) Labels
        for (i in 0..4) {
            val ratio = i / 4f
            val y = padTop + chartH * (1f - ratio)
            val minVal = (totalMin * ratio).toInt()
            drawLine(gridColor, Offset(padLeft, y), Offset(padLeft + chartW, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("${minVal}m", 8f, y + 6f, labelPaint)
        }

        // Ox Axis (% Pin) Labels
        val stepPct = pctDiff / 4.coerceAtLeast(1)
        for (i in 0..4) {
            val pctVal = minPct + (stepPct * i).coerceAtMost(maxPct - minPct)
            val x = padLeft + ((pctVal - minPct).toFloat() / pctDiff) * chartW
            drawLine(primaryColor.copy(alpha = 0.5f), Offset(x, padTop + chartH), Offset(x, padTop + chartH + 4.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("$pctVal%", x - 12f, size.height - 4f, labelPaint)
        }

        val path = Path()
        val linePath = Path()

        points.forEachIndexed { idx, pt ->
            val relPct = (pt.batteryPct - minPct).toFloat() / pctDiff
            val elapsedMin = (pt.timestampMs - startTs) / 60000f

            val x = padLeft + relPct * chartW
            val y = padTop + chartH * (1f - (elapsedMin / totalMin.coerceAtLeast(0.1f)))

            if (idx == 0) {
                path.moveTo(x, y)
                linePath.moveTo(x, y)
            } else {
                path.lineTo(x, y)
                linePath.lineTo(x, y)
            }
        }

        path.lineTo(padLeft + chartW, padTop + chartH)
        path.lineTo(padLeft, padTop + chartH)
        path.close()

        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.35f), primaryColor.copy(alpha = 0.05f)),
                startY = padTop, endY = padTop + chartH
            )
        )

        drawPath(
            path = linePath,
            color = primaryColor,
            style = Stroke(width = 2.5.dp.toPx())
        )

        points.forEach { pt ->
            val relPct = (pt.batteryPct - minPct).toFloat() / pctDiff
            val elapsedMin = (pt.timestampMs - startTs) / 60000f

            val x = padLeft + relPct * chartW
            val y = padTop + chartH * (1f - (elapsedMin / totalMin.coerceAtLeast(0.1f)))
            drawCircle(primaryColor, radius = 3.5.dp.toPx(), center = Offset(x, y))
        }
    }
}
