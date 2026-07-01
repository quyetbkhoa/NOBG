package com.nobg.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryStatsScreen(
    viewModel: BatteryStatsViewModel,
    onBack: () -> Unit
) {
    val tabs = listOf("App", "Chung")
    var selectedTab by remember { mutableStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thống kê Pin & Sử dụng") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Reset")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> AppUsageTab(viewModel)
                1 -> OverviewTab(viewModel)
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Xác nhận Reset") },
            text = { Text("Bạn có chắc muốn xóa toàn bộ lịch sử pin và bắt đầu thống kê lại từ đầu không?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetData()
                    showResetDialog = false
                }) { Text("Xóa", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Hủy") }
            }
        )
    }
}

// ========== TAB 1: App Usage ==========

@Composable
private fun AppUsageTab(viewModel: BatteryStatsViewModel) {
    val items by viewModel.usageStats.collectAsState()
    val currentInterval by viewModel.currentInterval.collectAsState()

    Column {
        // Sub-tabs: 1 Ngày / 1 Tuần
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentInterval == StatsInterval.DAILY,
                onClick = { viewModel.loadUsageStats(StatsInterval.DAILY) },
                label = { Text("1 Ngày") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = currentInterval == StatsInterval.WEEKLY,
                onClick = { viewModel.loadUsageStats(StatsInterval.WEEKLY) },
                label = { Text("1 Tuần") },
                modifier = Modifier.weight(1f)
            )
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Không có dữ liệu. Hãy cấp quyền Usage Stats.")
            }
        } else {
            val maxMah = items.maxOfOrNull { it.batteryMah } ?: 1.0
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.packageName }) { item ->
                    AppUsageRow(item = item, maxMah = maxMah)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(item: UsageItem, maxMah: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawableImage(item.icon, modifier = Modifier.size(42.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            // Battery bar
            if (item.batteryMah > 0) {
                val fraction = (item.batteryMah / maxMah.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Pin: ${String.format("%.1f", item.batteryMah)} mAh",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Dùng: ${formatDurationShort(item.totalTimeInForeground)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                        color = lerp(Color(0xFF4CAF50), Color(0xFFF44336), fraction),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            } else {
                Text(
                    "Dùng: ${formatDurationShort(item.totalTimeInForeground)} · Không có dữ liệu pin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ========== TAB 2: Overview ==========

@Composable
private fun OverviewTab(viewModel: BatteryStatsViewModel) {
    val overview by viewModel.overview.collectAsState()
    val curve by viewModel.chargingCurve.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadOverview() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (!overview.hasData) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔋", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Đang thu thập dữ liệu pin...\nHãy dùng máy bình thường vài tiếng.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (overview.hasData) {
            item {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                Text(
                    "Tính từ: ${sdf.format(Date(overview.sinceMs))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Time to full card (only when charging)
            if (overview.timeToFullMinutes >= 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("⚡ Đang sạc — ${overview.currentChargeLevel}%", fontWeight = FontWeight.Bold)
                                Text(
                                    "Đầy pin sau khoảng ${formatMinutes(overview.timeToFullMinutes)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            item { StatMetricCard("📅 Pin dùng/ngày TB", overview.avgDischargePctPerDay, "% / ngày", isNegative = true) }
            item { StatMetricCard("🔌 Pin sạc/ngày TB", overview.avgChargePctPerDay, "% / ngày") }
            item { StatMetricCard("☀️ Tốc độ hao (màn hình sáng)", overview.drainRateOnscreen, "% / giờ", isNegative = true) }
            item { StatMetricCard("🌙 Tốc độ hao (màn hình tắt)", overview.drainRateOffscreen, "% / giờ", isNegative = true) }
            item { StatMetricCard("⚡ Tốc độ sạc", overview.chargeRate, "% / giờ") }

            // Charging curve chart
            if (curve.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Biểu đồ tốc độ sạc", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Trục X: % pin  |  Trục Y: giây/% (thấp hơn = sạc nhanh hơn)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            ChargingCurveChart(curve = curve, modifier = Modifier.fillMaxWidth().height(180.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricCard(
    title: String,
    value: Double,
    unit: String,
    isNegative: Boolean = false
) {
    var showConverted by remember { mutableStateOf(false) }
    val primaryValue = if (value > 0) String.format("%.1f", value) else "—"
    val convertedHours = if (value > 0) 100.0 / value else 0.0
    val convertedText = if (value > 0) "≈ ${String.format("%.1f", convertedHours)} giờ / 100% pin" else "—"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (value > 0) showConverted = !showConverted },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (showConverted) convertedText else "$primaryValue $unit",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                if (value > 0) {
                    Text(
                        if (showConverted) "Bấm để xem %/giờ" else "Bấm → đổi sang giờ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChargingCurveChart(curve: List<ChargingCurvePoint>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val surfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Canvas(modifier = modifier) {
        if (curve.isEmpty()) return@Canvas

        val padLeft = 40.dp.toPx()
        val padBottom = 24.dp.toPx()
        val padTop = 8.dp.toPx()
        val padRight = 8.dp.toPx()

        val chartW = size.width - padLeft - padRight
        val chartH = size.height - padTop - padBottom

        val maxSeconds = curve.maxOf { it.secondsPerPct }.coerceAtLeast(1f)
        val minPct = curve.minOf { it.batteryPct }
        val maxPct = curve.maxOf { it.batteryPct }
        val pctRange = (maxPct - minPct).coerceAtLeast(1)

        // Grid lines
        for (i in 0..4) {
            val y = padTop + chartH * (1f - i / 4f)
            drawLine(surfaceVariant, Offset(padLeft, y), Offset(padLeft + chartW, y), strokeWidth = 1.dp.toPx())
        }

        // Gradient fill under curve
        val path = Path()
        curve.forEachIndexed { idx, point ->
            val x = padLeft + ((point.batteryPct - minPct).toFloat() / pctRange) * chartW
            val y = padTop + chartH * (1f - point.secondsPerPct / maxSeconds)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val lastX = padLeft + ((curve.last().batteryPct - minPct).toFloat() / pctRange) * chartW
        path.lineTo(lastX, padTop + chartH)
        path.lineTo(padLeft, padTop + chartH)
        path.close()
        drawPath(path, brush = Brush.verticalGradient(
            listOf(primaryColor.copy(alpha = 0.4f), primaryColor.copy(alpha = 0.05f)),
            startY = padTop, endY = padTop + chartH
        ))

        // Curve line
        val linePath = Path()
        curve.forEachIndexed { idx, point ->
            val x = padLeft + ((point.batteryPct - minPct).toFloat() / pctRange) * chartW
            val y = padTop + chartH * (1f - point.secondsPerPct / maxSeconds)
            if (idx == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        drawPath(linePath, color = primaryColor, style = Stroke(width = 2.dp.toPx()))

        // Dots for each data point
        curve.forEach { point ->
            val x = padLeft + ((point.batteryPct - minPct).toFloat() / pctRange) * chartW
            val y = padTop + chartH * (1f - point.secondsPerPct / maxSeconds)
            drawCircle(primaryColor, radius = 3.dp.toPx(), center = Offset(x, y))
        }

        // Axes
        drawLine(
            color = surfaceVariant.copy(alpha = 0.8f),
            start = Offset(padLeft, padTop),
            end = Offset(padLeft, padTop + chartH),
            strokeWidth = 1.5.dp.toPx()
        )
        drawLine(
            color = surfaceVariant.copy(alpha = 0.8f),
            start = Offset(padLeft, padTop + chartH),
            end = Offset(padLeft + chartW, padTop + chartH),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

// ========== Helpers ==========

@Composable
private fun DrawableImage(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable == null) {
        Box(modifier = modifier.clip(RoundedCornerShape(8.dp))) {}
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
        modifier = modifier.clip(RoundedCornerShape(10.dp))
    )
}

private fun lerp(a: Color, b: Color, t: Float): Color {
    val t2 = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t2,
        green = a.green + (b.green - a.green) * t2,
        blue = a.blue + (b.blue - a.blue) * t2,
        alpha = 1f
    )
}

private fun formatDurationShort(millis: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(millis)
    val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h} giờ ${m} phút" else "${m} phút"
}
