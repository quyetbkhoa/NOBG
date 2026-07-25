package com.nobg.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
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
    val tabs = listOf("App tiêu thụ pin", "Chỉ số Pin chung", "⚡ Tốc độ sạc")
    var selectedTab by remember { mutableStateOf(0) }
    var showResetAppUsageDialog by remember { mutableStateOf(false) }
    var showResetOverviewDialog by remember { mutableStateOf(false) }
    var showResetChargingDialog by remember { mutableStateOf(false) }

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
                    IconButton(
                        onClick = {
                            when (selectedTab) {
                                0 -> showResetAppUsageDialog = true
                                1 -> showResetOverviewDialog = true
                                2 -> showResetChargingDialog = true
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Reset tab hiện tại", tint = MaterialTheme.colorScheme.error)
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
                2 -> ChargingSessionsTab(viewModel)
            }
        }
    }

    if (showResetAppUsageDialog) {
        AlertDialog(
            onDismissRequest = { showResetAppUsageDialog = false },
            title = { Text("Reset Thống kê App") },
            text = { Text("Bạn có muốn đặt lại mốc thời gian và tính lại dữ liệu thời gian/pin sử dụng của tất cả App không?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAppUsageStats()
                    showResetAppUsageDialog = false
                }) { Text("Đặt lại", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetAppUsageDialog = false }) { Text("Hủy") }
            }
        )
    }

    if (showResetOverviewDialog) {
        AlertDialog(
            onDismissRequest = { showResetOverviewDialog = false },
            title = { Text("Reset Chỉ số Pin chung") },
            text = { Text("Bạn có muốn xóa toàn bộ lịch sử đo pin ngầm và đo lại các chỉ số trung bình từ đầu không?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetOverviewBatteryLogs()
                    showResetOverviewDialog = false
                }) { Text("Xóa dữ liệu", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetOverviewDialog = false }) { Text("Hủy") }
            }
        )
    }

    if (showResetChargingDialog) {
        AlertDialog(
            onDismissRequest = { showResetChargingDialog = false },
            title = { Text("Reset Lịch sử Tốc độ Sạc") },
            text = { Text("Bạn có muốn xóa tất cả lịch sử các phiên sạc đã lưu không?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllChargingSessions()
                    showResetChargingDialog = false
                }) { Text("Xóa lịch sử", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetChargingDialog = false }) { Text("Hủy") }
            }
        )
    }
}

// ========== TAB 1: App Usage ==========

@Composable
private fun AppUsageTab(viewModel: BatteryStatsViewModel) {
    val items by viewModel.usageStats.collectAsState()
    val currentInterval by viewModel.currentInterval.collectAsState()
    val anchorTimeMs by viewModel.anchorTimeMs.collectAsState()
    val selectedAppDetail by viewModel.selectedAppDetail.collectAsState()
    val isLoadingDetail by viewModel.isLoadingDetail.collectAsState()

    val sdf = remember { SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()) }

    if (selectedAppDetail != null) {
        AppDetailDialog(
            stats = selectedAppDetail!!,
            onDismiss = { viewModel.clearSelectedAppDetail() }
        )
    }

    if (isLoadingDetail) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text("Đang tải chi tiết ứng dụng...")
                }
            }
        )
    }

    Column {
        // Sub-tabs: 1 Ngày / 1 Tuần / ⚡ Sạc đầy gần nhất
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsInterval.values().forEach { interval ->
                FilterChip(
                    selected = currentInterval == interval,
                    onClick = { viewModel.loadUsageStats(interval) },
                    label = { Text(interval.label) }
                )
            }
        }

        if (anchorTimeMs > 0) {
            val labelText = when (currentInterval) {
                StatsInterval.SINCE_CHARGED -> "⚡ Tính từ mốc sạc đầy: ${sdf.format(Date(anchorTimeMs))}"
                StatsInterval.DAILY -> "📅 Tính trong 24 giờ qua (${sdf.format(Date(anchorTimeMs))})"
                StatsInterval.WEEKLY -> "📅 Tính trong 7 ngày qua (${sdf.format(Date(anchorTimeMs))})"
            }
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Không có dữ liệu. Hãy đảm bảo đã cấp quyền Usage Stats.")
            }
        } else {
            val maxMah = items.maxOfOrNull { it.batteryMah } ?: 1.0
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.packageName }) { item ->
                    AppUsageRow(
                        item = item,
                        maxMah = maxMah,
                        onClick = { viewModel.selectAppDetail(item.packageName) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(item: UsageItem, maxMah: Double, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawableImage(item.icon, modifier = Modifier.size(42.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.label, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    "Màn hình: ${formatDurationShort(item.totalTimeInForeground)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            // Battery bar & CPU stats
            val fraction = (item.batteryMah / maxMah.coerceAtLeast(1.0)).toFloat().coerceIn(0.01f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Pin: ${String.format("%.1f", item.batteryMah)} mAh (${String.format("%.1f", item.batteryPct)}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                if (item.wakeupCount > 0 || item.totalCpuMs > 0) {
                    Text(
                        "⏰ ${item.wakeupCount} wakeups | ⚡ CPU: ${formatDurationShort(item.totalCpuMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = lerp(Color(0xFF4CAF50), Color(0xFFF44336), fraction),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
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
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val labelPaint = remember(onSurfaceVariantColor) {
        android.graphics.Paint().apply {
            color = onSurfaceVariantColor.toArgb()
            textSize = 22f
            isAntiAlias = true
        }
    }

    val tooltipPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }
    }

    val tooltipBgPaint = remember(primaryColor) {
        android.graphics.Paint().apply {
            color = primaryColor.toArgb()
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier.pointerInput(curve) {
            detectTapGestures { tapOffset ->
                if (curve.isEmpty()) return@detectTapGestures
                val padLeft = 60.dp.toPx()
                val padRight = 15.dp.toPx()
                val chartW = size.width - padLeft - padRight
                val minPct = curve.minOf { it.batteryPct }
                val maxPct = curve.maxOf { it.batteryPct }
                val pctRange = (maxPct - minPct).coerceAtLeast(1)

                var closestIdx = 0
                var minDist = Float.MAX_VALUE
                curve.forEachIndexed { idx, point ->
                    val x = padLeft + ((point.batteryPct - minPct).toFloat() / pctRange) * chartW
                    val dist = Math.abs(x - tapOffset.x)
                    if (dist < minDist) {
                        minDist = dist
                        closestIdx = idx
                    }
                }
                selectedIndex = closestIdx
            }
        }
    ) {
        if (curve.isEmpty()) return@Canvas

        val padLeft = 60.dp.toPx()
        val padBottom = 28.dp.toPx()
        val padTop = 15.dp.toPx()
        val padRight = 15.dp.toPx()

        val chartW = size.width - padLeft - padRight
        val chartH = size.height - padTop - padBottom

        val maxSeconds = curve.maxOf { it.secondsPerPct }.coerceAtLeast(1f)
        val minPct = curve.minOf { it.batteryPct }
        val maxPct = curve.maxOf { it.batteryPct }
        val pctRange = (maxPct - minPct).coerceAtLeast(1)

        // Draw Oy & Ox Axis lines
        drawLine(primaryColor.copy(alpha = 0.7f), Offset(padLeft, padTop), Offset(padLeft, padTop + chartH), strokeWidth = 2.dp.toPx())
        drawLine(primaryColor.copy(alpha = 0.7f), Offset(padLeft, padTop + chartH), Offset(padLeft + chartW, padTop + chartH), strokeWidth = 2.dp.toPx())

        // Oy Axis (Giây / 1% pin) Ticks & Labels
        for (i in 0..4) {
            val ratio = i / 4f
            val y = padTop + chartH * (1f - ratio)
            val secVal = (maxSeconds * ratio).toInt()
            drawLine(gridColor, Offset(padLeft, y), Offset(padLeft + chartW, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("${secVal}s", 8f, y + 6f, labelPaint)
        }

        // Ox Axis (% Pin) Ticks & Labels
        val stepPct = pctRange / 4.coerceAtLeast(1)
        for (i in 0..4) {
            val pctVal = minPct + (stepPct * i).coerceAtMost(maxPct - minPct)
            val x = padLeft + ((pctVal - minPct).toFloat() / pctRange) * chartW
            drawLine(primaryColor.copy(alpha = 0.5f), Offset(x, padTop + chartH), Offset(x, padTop + chartH + 5.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText("$pctVal%", x - 15f, size.height - 4f, labelPaint)
        }

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

        val linePath = Path()
        curve.forEachIndexed { idx, point ->
            val x = padLeft + ((point.batteryPct - minPct).toFloat() / pctRange) * chartW
            val y = padTop + chartH * (1f - point.secondsPerPct / maxSeconds)
            if (idx == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        drawPath(linePath, color = primaryColor, style = Stroke(width = 2.dp.toPx()))

        curve.forEach { point ->
            val x = padLeft + ((point.batteryPct - minPct).toFloat() / pctRange) * chartW
            val y = padTop + chartH * (1f - point.secondsPerPct / maxSeconds)
            drawCircle(primaryColor, radius = 3.dp.toPx(), center = Offset(x, y))
        }

        // Crosshair Projection Lines ("gióng sang 2 bên") & Tooltip
        selectedIndex?.let { idx ->
            if (idx in curve.indices) {
                val pt = curve[idx]
                val x = padLeft + ((pt.batteryPct - minPct).toFloat() / pctRange) * chartW
                val y = padTop + chartH * (1f - pt.secondsPerPct / maxSeconds)

                val dashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                drawLine(primaryColor.copy(alpha = 0.9f), Offset(padLeft, y), Offset(x, y), strokeWidth = 1.5.dp.toPx(), pathEffect = dashEffect)
                drawLine(primaryColor.copy(alpha = 0.9f), Offset(x, y), Offset(x, padTop + chartH), strokeWidth = 1.5.dp.toPx(), pathEffect = dashEffect)

                drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(x, y))
                drawCircle(primaryColor, radius = 4.dp.toPx(), center = Offset(x, y))

                val text = "⚡ ${pt.batteryPct}% Pin — ${pt.secondsPerPct.toInt()}s / 1%"
                val textW = tooltipPaint.measureText(text)
                val rectLeft = (x - textW / 2f - 16f).coerceIn(padLeft, padLeft + chartW - textW - 32f)
                val rectTop = (y - 38.dp.toPx()).coerceAtLeast(padTop)
                drawContext.canvas.nativeCanvas.drawRoundRect(rectLeft, rectTop, rectLeft + textW + 32f, rectTop + 32.dp.toPx(), 12f, 12f, tooltipBgPaint)
                drawContext.canvas.nativeCanvas.drawText(text, rectLeft + 16f, rectTop + 22.dp.toPx(), tooltipPaint)
            }
        }
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

fun formatDurationShort(millis: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(millis)
    val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h} giờ ${m} phút" else "${m} phút"
}
