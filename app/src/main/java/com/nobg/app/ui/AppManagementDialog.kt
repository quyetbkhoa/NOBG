package com.nobg.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobg.app.data.BackgroundPowerState
import com.nobg.app.data.NobgMode
import com.nobg.app.shizuku.ShizukuManager

fun Modifier.drawVerticalScrollbar(
    scrollState: ScrollState,
    color: Color
): Modifier = drawWithContent {
    drawContent()
    if (scrollState.maxValue > 0) {
        val elementHeight = size.height / (scrollState.maxValue + size.height)
        val scrollbarHeight = (elementHeight * size.height).coerceAtLeast(36.dp.toPx())
        val scrollbarOffsetY = (scrollState.value.toFloat() / scrollState.maxValue) * (size.height - scrollbarHeight)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - 6.dp.toPx(), scrollbarOffsetY),
            size = Size(4.dp.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagementDialog(
    appModel: AppUiModel,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val config = appModel.config
    val isNobgEnabled = config?.enabled == true
    val currentNobgMode = if (config?.mode == NobgMode.DISABLE_ENABLE) NobgMode.STANDARD else (config?.mode ?: NobgMode.STANDARD)
    val currentDelay = config?.delaySeconds ?: 30

    var localPowerState by remember(appModel.packageName, appModel.powerState) {
        mutableStateOf(appModel.powerState)
    }

    var isSystemWhitelisted by remember { mutableStateOf(false) }

    LaunchedEffect(appModel.packageName) {
        if (ShizukuManager.isShizukuRunning() && ShizukuManager.hasPermission() && ShizukuManager.isServiceBound()) {
            isSystemWhitelisted = ShizukuManager.isSystemPowerWhitelisted(appModel.packageName)
        }
    }

    val iconBitmap = remember(appModel.icon) {
        appModel.icon?.let { drawable ->
            val bmp = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        }
    }

    val scrollState = rememberScrollState()
    val scrollbarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .padding(vertical = 12.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {},
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quản lý ứng dụng",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Đóng")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .drawVerticalScrollbar(scrollState, scrollbarColor)
                    .padding(end = 6.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header: App Info Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(appModel.label.take(1), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appModel.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = appModel.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // AI / Google Query Button
                        var showSearchMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSearchMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Help,
                                    contentDescription = "Hỏi tác dụng ứng dụng",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = showSearchMenu,
                                onDismissRequest = { showSearchMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("🤖 Hỏi ChatGPT (Tự điền)") },
                                    onClick = {
                                        showSearchMenu = false
                                        lookupPackageInfo(context, appModel.packageName, appModel.label, SearchEngine.CHATGPT)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("✨ Hỏi Google Gemini") },
                                    onClick = {
                                        showSearchMenu = false
                                        lookupPackageInfo(context, appModel.packageName, appModel.label, SearchEngine.GEMINI)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🔍 Tìm trên Google") },
                                    onClick = {
                                        showSearchMenu = false
                                        lookupPackageInfo(context, appModel.packageName, appModel.label, SearchEngine.GOOGLE)
                                    }
                                )
                            }
                        }
                    }
                }

                if (isSystemWhitelisted) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Thuộc System Whitelist mặc định của Android.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Current Badges Overview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PowerBadge(state = localPowerState)
                    NobgBadge(enabled = isNobgEnabled, mode = currentNobgMode, delaySeconds = currentDelay)
                    if (appModel.isDisabled) {
                        DisabledBadge()
                    }
                }

                // APP CPU & WAKEUP STATS CARD
                var appDetailStats by remember { mutableStateOf<com.nobg.app.shizuku.AppBatteryDetail?>(null) }
                LaunchedEffect(appModel.packageName) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val allDetails = com.nobg.app.shizuku.BatteryDumpsysParser.getAppBatteryDetails()
                        val ai = try { context.packageManager.getApplicationInfo(appModel.packageName, 0) } catch (_: Exception) { null }
                        val uid = ai?.uid?.toString() ?: ""
                        appDetailStats = allDetails[uid] ?: allDetails[appModel.packageName]
                    }
                }

                if (appDetailStats != null && (appDetailStats!!.totalCpuMs > 0 || appDetailStats!!.wakeupCount > 0 || appDetailStats!!.totalWakelockMs > 0)) {
                    Text(
                        text = "📊 THỐNG KÊ CPU & ĐÁNH THỨC",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("⚡ Thời gian CPU sử dụng:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatDurationShort(appDetailStats!!.totalCpuMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("⏰ Số lần đánh thức (Wakeups):", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${appDetailStats!!.wakeupCount} lần",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (appDetailStats!!.wakeupCount > 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (appDetailStats!!.totalWakelockMs > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🔒 Giữ CPU ngầm (Wakelock):", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        formatDurationShort(appDetailStats!!.totalWakelockMs),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 1: BACKGROUND POWER MODE (NO DETAILED DESCRIPTIONS)
                Text(
                    text = "🔋 CHẾ ĐỘ TIẾT KIỆM PIN HỆ THỐNG",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)) {
                        val states = listOf(
                            BackgroundPowerState.RESTRICTED,
                            BackgroundPowerState.OPTIMIZED,
                            BackgroundPowerState.UNRESTRICTED
                        )

                        states.forEachIndexed { index, state ->
                            val isSelected = localPowerState == state
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                                    .clickable {
                                        localPowerState = state
                                        viewModel.changePowerState(appModel.packageName, state)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        localPowerState = state
                                        viewModel.changePowerState(appModel.packageName, state)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${state.emoji} ${state.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                            if (index < states.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                // SECTION 2: NOBG CONFIGURATION (RENAMED TO "Cấu hình nobg")
                Text(
                    text = "🛡️ Cấu hình nobg",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isNobgEnabled) "Đã bật NOBG ngầm" else "Đang tắt NOBG ngầm",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isNobgEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.toggleNobg(
                                        appModel.packageName,
                                        checked,
                                        currentNobgMode,
                                        currentDelay
                                    )
                                }
                            )
                        }

                        if (isNobgEnabled) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Chế độ xử lý:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))

                            val modes = listOf(
                                NobgMode.STANDARD to "Standard (Chặn ngầm/thông báo)",
                                NobgMode.AGGRESSIVE to "Aggressive (Chặn & Ép diệt)"
                            )

                            modes.forEach { (mode, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { viewModel.changeMode(appModel.packageName, mode) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = (currentNobgMode == mode),
                                        onClick = { viewModel.changeMode(appModel.packageName, mode) }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            if (currentNobgMode == NobgMode.AGGRESSIVE) {
                                Spacer(Modifier.height(6.dp))
                                var sliderVal by remember(appModel.packageName, currentDelay) {
                                    mutableStateOf(currentDelay.toFloat())
                                }
                                Text(
                                    text = "Delay trước khi kill: ${sliderVal.toInt()} giây",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Slider(
                                    value = sliderVal,
                                    onValueChange = { sliderVal = it },
                                    onValueChangeFinished = {
                                        viewModel.changeDelay(appModel.packageName, sliderVal.toInt())
                                    },
                                    valueRange = 10f..1200f
                                )
                            }
                        }
                    }
                }

                // SECTION 3: ACTIONS & RECOVERY (INCLUDES COMPACT DISABLE BUTTON)
                Text(
                    text = "⚙️ THAO TÁC & KHÔI PHỤC",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Hide App row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🙈 Ẩn khỏi danh sách",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = appModel.isHidden,
                                onCheckedChange = { hide ->
                                    viewModel.toggleHideApp(appModel.packageName, hide)
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // 2. Compact Disable / Enable App Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (appModel.isDisabled) "❄️ Đã vô hiệu hóa" else "❄️ Vô hiệu hóa ứng dụng",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (appModel.isDisabled) {
                                Button(
                                    onClick = {
                                        viewModel.enableAndLaunchApp(appModel.packageName)
                                        onDismiss()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Mở lại", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.disableApp(appModel.packageName)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Disable", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // 3. Other system action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.openAppInfoSettings(context, appModel.packageName)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cài đặt app", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.openSystemBatterySettings(context, appModel.packageName)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cài đặt pin", style = MaterialTheme.typography.labelSmall)
                            }

                            if (appModel.config != null) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.resetApp(appModel.packageName)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reset app", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun PowerBadge(state: BackgroundPowerState) {
    val (bgColor, textColor) = when (state) {
        BackgroundPowerState.RESTRICTED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        BackgroundPowerState.OPTIMIZED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BackgroundPowerState.UNRESTRICTED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        BackgroundPowerState.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "${state.emoji} ${state.label}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NobgBadge(enabled: Boolean, mode: NobgMode, delaySeconds: Int) {
    if (!enabled) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "⚪ NOBG: Tắt",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        val (text, color) = when (mode) {
            NobgMode.STANDARD -> "🛡️ Standard" to MaterialTheme.colorScheme.primaryContainer
            NobgMode.AGGRESSIVE -> "⚡ Aggressive (${delaySeconds}s)" to MaterialTheme.colorScheme.errorContainer
            else -> "🛡️ Standard" to MaterialTheme.colorScheme.primaryContainer
        }
        val textColor = when (mode) {
            NobgMode.STANDARD -> MaterialTheme.colorScheme.onPrimaryContainer
            NobgMode.AGGRESSIVE -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
        Surface(
            color = color,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DisabledBadge() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "❄️ Đã vô hiệu hóa",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class SearchEngine(val label: String) {
    CHATGPT("ChatGPT"),
    GEMINI("Gemini"),
    GOOGLE("Google")
}

fun lookupPackageInfo(context: Context, packageName: String, label: String, engine: SearchEngine) {
    val prompt = "Ứng dụng $label (package: $packageName) trên Android có tác dụng gì? Có an toàn để tắt hoặc vô hiệu hóa (disable) không?"
    
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("NOBG Package Query", prompt)
        clipboard.setPrimaryClip(clip)
    } catch (_: Exception) {}

    val encodedQuery = Uri.encode(prompt)
    val url = when (engine) {
        SearchEngine.CHATGPT -> "https://chatgpt.com/?q=$encodedQuery"
        SearchEngine.GEMINI -> "https://gemini.google.com/app"
        SearchEngine.GOOGLE -> "https://www.google.com/search?q=$encodedQuery"
    }

    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Toast.makeText(context, "Đã chép câu hỏi & chuyển sang ${engine.label}!", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Không thể mở trình duyệt", Toast.LENGTH_SHORT).show()
    }
}
