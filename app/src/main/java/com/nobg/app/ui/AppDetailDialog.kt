package com.nobg.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nobg.app.data.AppDetailStats
import com.nobg.app.data.TimeInterval
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailDialog(
    stats: AppDetailStats,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Thông tin",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Header Title
                    Text(
                        text = "Chi tiết",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // App Info Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DrawableImage(
                            drawable = stats.icon,
                            modifier = Modifier.size(54.dp)
                        )
                        Column {
                            Text(
                                text = stats.label,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stats.packageName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Section 1: Event Timeline
                    EventTimelineCard(stats = stats)

                    // Section 2: Usage Stats
                    UsageStatsCard(stats = stats)

                    // Section 3: Network Data
                    NetworkStatsCard(stats = stats)

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun EventTimelineCard(stats: AppDetailStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Dòng thời gian sự kiện",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = "Khi ứng dụng ở trên màn hình trong phiên này. Mỗi thanh là một khoảng tiền cảnh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = Color(0xFF8A9EFF), label = "Tiền cảnh")
                LegendItem(color = Color(0xFFE08BB7), label = "Dịch vụ nền")
            }

            // Timeline Canvas
            TimelineChart(
                startTimeMs = stats.startTimeMs,
                endTimeMs = stats.endTimeMs,
                fgIntervals = stats.foregroundIntervals,
                fgServiceIntervals = stats.fgServiceIntervals,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            )

            // Timeline Markers (e.g. 0, +4h, +8h, +11h 26m)
            val durationMs = (stats.endTimeMs - stats.startTimeMs).coerceAtLeast(1L)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+${formatDurationHours(durationMs / 3)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+${formatDurationHours((durationMs * 2) / 3)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+${formatDurationShort(durationMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Sub-metrics
            Text(
                text = "${stats.fgSessionCount} phiên tiền cảnh · tổng ${formatDurationShort(stats.totalFgTimeMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Phiên dài nhất: ${formatDurationShort(stats.longestFgSessionMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${stats.fgServiceRunCount} lần chạy dịch vụ nền",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${stats.userInteractionCount} tương tác",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TimelineChart(
    startTimeMs: Long,
    endTimeMs: Long,
    fgIntervals: List<TimeInterval>,
    fgServiceIntervals: List<TimeInterval>,
    modifier: Modifier = Modifier
) {
    val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val fgColor = Color(0xFF8A9EFF)
    val serviceColor = Color(0xFFE08BB7)

    Canvas(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
        val totalMs = (endTimeMs - startTimeMs).toFloat().coerceAtLeast(1f)
        val cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

        // Track Background
        drawRoundRect(
            color = trackBg,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, size.height),
            cornerRadius = cornerRadius
        )

        val rowHeight = (size.height - 12.dp.toPx()) / 2f
        val fgTop = 4.dp.toPx()
        val serviceTop = fgTop + rowHeight + 4.dp.toPx()

        // Draw Foreground Intervals
        for (interval in fgIntervals) {
            val startRel = ((interval.startMs - startTimeMs).toFloat() / totalMs).coerceIn(0f, 1f)
            val endRel = ((interval.endMs - startTimeMs).toFloat() / totalMs).coerceIn(0f, 1f)
            val left = startRel * size.width
            val width = ((endRel - startRel) * size.width).coerceAtLeast(3.dp.toPx())

            drawRoundRect(
                color = fgColor,
                topLeft = Offset(left, fgTop),
                size = Size(width, rowHeight),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }

        // Draw Service Intervals
        for (interval in fgServiceIntervals) {
            val startRel = ((interval.startMs - startTimeMs).toFloat() / totalMs).coerceIn(0f, 1f)
            val endRel = ((interval.endMs - startTimeMs).toFloat() / totalMs).coerceIn(0f, 1f)
            val left = startRel * size.width
            val width = ((endRel - startRel) * size.width).coerceAtLeast(3.dp.toPx())

            drawRoundRect(
                color = serviceColor,
                topLeft = Offset(left, serviceTop),
                size = Size(width, rowHeight),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
    }
}

@Composable
private fun UsageStatsCard(stats: AppDetailStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sử dụng",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricCell(modifier = Modifier.weight(1f), label = "Thời gian màn hình", value = formatDurationShort(stats.totalFgTimeMs))
                    MetricCell(modifier = Modifier.weight(1f), label = "Lượt mở", value = "${stats.openCount}")
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricCell(modifier = Modifier.weight(1f), label = "Thời gian hiển thị", value = formatDurationShort(stats.totalFgTimeMs))
                    MetricCell(modifier = Modifier.weight(1f), label = "Dịch vụ tiền cảnh", value = formatDurationShort(stats.totalFgServiceTimeMs))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricCell(modifier = Modifier.weight(1f), label = "Hao pin", value = "${String.format("%.0f", stats.batteryMah)} mAh")
                    MetricCell(modifier = Modifier.weight(1f), label = "Nhóm chờ", value = stats.standbyBucket)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricCell(modifier = Modifier.weight(1f), label = "% pin", value = "${String.format("%.1f", stats.batteryPct)} %")
                    MetricCell(modifier = Modifier.weight(1f), label = "% mỗi giờ", value = "${String.format("%.1f", stats.pctPerHour)} %/h")
                }
            }
        }
    }
}

@Composable
private fun NetworkStatsCard(stats: AppDetailStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Mạng",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell(modifier = Modifier.weight(1f), label = "Wi-Fi", value = formatBytes(stats.wifiBytes))
                MetricCell(modifier = Modifier.weight(1f), label = "Dữ liệu di động", value = formatBytes(stats.mobileBytes))
            }
        }
    }
}

@Composable
private fun MetricCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DrawableImage(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        return
    }
    val bitmap = remember(drawable) {
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp
    }
    Image(
        painter = BitmapPainter(bitmap.asImageBitmap()),
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(12.dp))
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "--"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatDurationHours(millis: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(millis)
    return "${h}h"
}
