package com.nobg.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nobg.app.data.BackgroundPowerState
import com.nobg.app.data.NobgMode
import com.nobg.app.shizuku.AppBatteryDetail
import com.nobg.app.shizuku.BatteryDumpsysParser
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AppManagementDialog(
    appModel: AppUiModel,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = appModel.config
    val isNobgEnabled = config?.enabled == true
    val currentNobgMode = if (config?.mode == NobgMode.DISABLE_ENABLE) {
        NobgMode.STANDARD
    } else {
        config?.mode ?: NobgMode.STANDARD
    }
    val currentDelay = config?.delaySeconds ?: 30
    val isShizukuAvailable = remember {
        ShizukuManager.isShizukuRunning() && ShizukuManager.hasPermission()
    }

    var localPowerState by remember(appModel.packageName, appModel.powerState) {
        mutableStateOf(appModel.powerState)
    }
    var isSystemWhitelisted by remember(appModel.packageName) { mutableStateOf(false) }
    var appBatteryDetail by remember(appModel.packageName) { mutableStateOf<AppBatteryDetail?>(null) }
    var detailStatsForDialog by remember { mutableStateOf<com.nobg.app.data.AppDetailStats?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }
    var showSearchMenu by remember { mutableStateOf(false) }

    LaunchedEffect(appModel.packageName) {
        if (ShizukuManager.isShizukuRunning() && ShizukuManager.hasPermission() && ShizukuManager.isServiceBound()) {
            isSystemWhitelisted = ShizukuManager.isSystemPowerWhitelisted(appModel.packageName)
        }
        appBatteryDetail = withContext(Dispatchers.IO) {
            val details = BatteryDumpsysParser.getAppBatteryDetails()
            val applicationInfo = try {
                context.packageManager.getApplicationInfo(appModel.packageName, 0)
            } catch (_: Exception) {
                null
            }
            val uid = applicationInfo?.uid?.toString().orEmpty()
            details[uid] ?: details[appModel.packageName]
        }
    }

    val iconBitmap = remember(appModel.icon) {
        appModel.icon?.let { drawable ->
            val width = drawable.intrinsicWidth.coerceIn(1, 128)
            val height = drawable.intrinsicHeight.coerceIn(1, 128)
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }
    }

    detailStatsForDialog?.let { stats ->
        AppDetailDialog(stats = stats, onDismiss = { detailStatsForDialog = null })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .widthIn(max = 720.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Quản lý ứng dụng",
                        modifier = Modifier.weight(1f),
                        fontSize = 27.sp,
                        lineHeight = 33.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(PremiumDimens.GroupGap)
                ) {
                    AppIdentityCard(
                        label = appModel.label,
                        packageName = appModel.packageName,
                        iconBitmap = iconBitmap,
                        isSystemWhitelisted = isSystemWhitelisted,
                        onShowSearchMenu = { showSearchMenu = true },
                        searchMenu = {
                            DropdownMenu(expanded = showSearchMenu, onDismissRequest = { showSearchMenu = false }) {
                                SearchEngine.entries.forEach { engine ->
                                    DropdownMenuItem(
                                        text = { Text("Tìm bằng ${engine.label}") },
                                        onClick = {
                                            showSearchMenu = false
                                            lookupPackageInfo(context, appModel.packageName, appModel.label, engine)
                                        }
                                    )
                                }
                            }
                        }
                    )

                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PowerBadge(localPowerState)
                        NobgBadge(isNobgEnabled, currentNobgMode, currentDelay)
                        if (appModel.isDisabled) DisabledBadge()
                    }

                    val meaningfulBatteryDetail = appBatteryDetail?.takeIf {
                        isShizukuAvailable && (it.totalCpuMs > 0 || it.wakeupCount > 0 || it.totalWakelockMs > 0)
                    }
                    Column {
                        PremiumSectionLabel("Hoạt động nền")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            meaningfulBatteryDetail?.let { detail ->
                                ManagementMetricRow(Icons.Filled.Speed, PremiumAccent.Blue, "Thời gian CPU", formatDurationShort(detail.totalCpuMs))
                                PremiumInsetDivider()
                                ManagementMetricRow(
                                    Icons.Filled.Bolt,
                                    if (detail.wakeupCount > 20) PremiumAccent.Pink else PremiumAccent.Orange,
                                    "Lượt đánh thức",
                                    "${detail.wakeupCount} lần"
                                )
                                if (detail.totalWakelockMs > 0) {
                                    PremiumInsetDivider()
                                    ManagementMetricRow(Icons.Filled.Security, PremiumAccent.Purple, "Giữ CPU nền", formatDurationShort(detail.totalWakelockMs))
                                }
                            }
                            if (meaningfulBatteryDetail != null) {
                                PremiumInsetDivider()
                            }
                            PremiumNavigationRow(
                                title = "Dòng thời gian và mức dùng pin",
                                subtitle = "Xem dữ liệu chi tiết trong 24 giờ gần nhất",
                                icon = Icons.Filled.BarChart,
                                accent = PremiumAccent.Green,
                                trailing = if (isDetailLoading) "Đang tải" else null,
                                onClick = {
                                    if (!isDetailLoading) {
                                        isDetailLoading = true
                                        scope.launch(Dispatchers.IO) {
                                            val endTime = System.currentTimeMillis()
                                            val stats = com.nobg.app.data.AppDetailStatsHelper.getAppDetailStats(
                                                context,
                                                appModel.packageName,
                                                endTime - 86_400_000L,
                                                endTime
                                            )
                                            withContext(Dispatchers.Main) {
                                                detailStatsForDialog = stats
                                                isDetailLoading = false
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Column {
                        PremiumSectionLabel("Pin hệ thống")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            val states = listOf(
                                BackgroundPowerState.RESTRICTED to Triple(Icons.Filled.Block, PremiumAccent.Pink, "Giới hạn hoạt động nền"),
                                BackgroundPowerState.OPTIMIZED to Triple(Icons.Filled.BatterySaver, PremiumAccent.Orange, "Android tự cân bằng"),
                                BackgroundPowerState.UNRESTRICTED to Triple(Icons.Filled.BatteryFull, PremiumAccent.Green, "Cho phép chạy nền")
                            )
                            states.forEachIndexed { index, (state, visual) ->
                                ManagementSelectionRow(
                                    title = state.label,
                                    subtitle = visual.third,
                                    icon = visual.first,
                                    accent = visual.second,
                                    selected = localPowerState == state,
                                    onClick = {
                                        localPowerState = state
                                        viewModel.changePowerState(appModel.packageName, state)
                                    }
                                )
                                if (index < states.lastIndex) PremiumInsetDivider()
                            }
                        }
                    }

                    Column {
                        PremiumSectionLabel("NOBG")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ManagementToggleRow(
                                title = "Kiểm soát chạy nền",
                                subtitle = if (isNobgEnabled) "NOBG đang quản lý ứng dụng này" else "NOBG chưa can thiệp ứng dụng này",
                                icon = Icons.Filled.Security,
                                accent = PremiumAccent.Blue,
                                checked = isNobgEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.toggleNobg(appModel.packageName, checked, currentNobgMode, currentDelay)
                                }
                            )

                            if (isNobgEnabled) {
                                PremiumInsetDivider()
                                ManagementSelectionRow(
                                    title = "Tiêu chuẩn",
                                    subtitle = "Chặn hoạt động nền và thông báo không cần thiết",
                                    icon = Icons.Filled.Security,
                                    accent = PremiumAccent.Teal,
                                    selected = currentNobgMode == NobgMode.STANDARD,
                                    onClick = { viewModel.changeMode(appModel.packageName, NobgMode.STANDARD) }
                                )
                                if (isShizukuAvailable) {
                                    PremiumInsetDivider()
                                    ManagementSelectionRow(
                                        title = "Mạnh",
                                        subtitle = "Chặn nền và ép dừng sau khoảng trễ",
                                        icon = Icons.Filled.Bolt,
                                        accent = PremiumAccent.Orange,
                                        selected = currentNobgMode == NobgMode.AGGRESSIVE,
                                        onClick = { viewModel.changeMode(appModel.packageName, NobgMode.AGGRESSIVE) }
                                    )
                                }
                                if (!isShizukuAvailable) {
                                    PremiumInsetDivider()
                                    ManagementInfoRow("Cấp quyền Shizuku để dùng chế độ Mạnh và đóng băng tức thì.")
                                }
                                if (currentNobgMode == NobgMode.AGGRESSIVE) {
                                    PremiumInsetDivider()
                                    AggressiveDelayRow(
                                        initialDelay = currentDelay,
                                        onDelayChanged = { viewModel.changeDelay(appModel.packageName, it) }
                                    )
                                }
                            }
                        }
                    }

                    Column {
                        PremiumSectionLabel("Thao tác")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ManagementToggleRow(
                                title = "Kệ Đóng Băng",
                                subtitle = "Tự vô hiệu hóa hoàn toàn sau khi thoát",
                                icon = Icons.Filled.AcUnit,
                                accent = PremiumAccent.Teal,
                                checked = appModel.isFrozenShelf,
                                onCheckedChange = { viewModel.toggleFrozenShelf(appModel.packageName, it) }
                            )
                            PremiumInsetDivider()
                            ManagementToggleRow(
                                title = "Ẩn khỏi danh sách",
                                subtitle = "Không hiển thị trong danh sách ứng dụng chính",
                                icon = Icons.Filled.VisibilityOff,
                                accent = PremiumAccent.Purple,
                                checked = appModel.isHidden,
                                onCheckedChange = { viewModel.toggleHideApp(appModel.packageName, it) }
                            )
                            if (appModel.isFrozenShelf || !appModel.isDisabled) {
                                PremiumInsetDivider()
                                PremiumNavigationRow(
                                    title = "Đóng băng ngay",
                                    subtitle = "Ép dừng và vô hiệu hóa ứng dụng",
                                    icon = Icons.Filled.AcUnit,
                                    accent = PremiumAccent.Blue,
                                    onClick = {
                                        viewModel.freezeAppImmediately(appModel.packageName)
                                        onDismiss()
                                    }
                                )
                            }
                            PremiumInsetDivider()
                            PremiumNavigationRow(
                                title = if (appModel.isDisabled) "Mở lại ứng dụng" else "Vô hiệu hóa ứng dụng",
                                subtitle = if (appModel.isDisabled) "Bật lại và khởi chạy ứng dụng" else "Ẩn ứng dụng khỏi launcher và ngăn hoạt động",
                                icon = Icons.Filled.PowerSettingsNew,
                                accent = if (appModel.isDisabled) PremiumAccent.Green else PremiumAccent.Pink,
                                onClick = {
                                    if (appModel.isDisabled) {
                                        viewModel.enableAndLaunchApp(appModel.packageName)
                                        onDismiss()
                                    } else {
                                        viewModel.disableApp(appModel.packageName)
                                    }
                                }
                            )
                        }
                    }

                    Column {
                        PremiumSectionLabel("Cài đặt Android")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            PremiumNavigationRow(
                                title = "Thông tin ứng dụng",
                                subtitle = "Quyền, bộ nhớ và thông báo",
                                icon = Icons.Filled.Settings,
                                accent = PremiumAccent.Blue,
                                onClick = { viewModel.openAppInfoSettings(context, appModel.packageName) }
                            )
                            PremiumInsetDivider()
                            PremiumNavigationRow(
                                title = "Cài đặt pin",
                                subtitle = "Mở trang quản lý pin của hệ thống",
                                icon = Icons.Filled.BatterySaver,
                                accent = PremiumAccent.Green,
                                onClick = { viewModel.openSystemBatterySettings(context, appModel.packageName) }
                            )
                            if (appModel.config != null) {
                                PremiumInsetDivider()
                                PremiumNavigationRow(
                                    title = "Đặt lại cấu hình",
                                    subtitle = "Xóa thiết lập NOBG riêng của ứng dụng",
                                    icon = Icons.Filled.Refresh,
                                    accent = PremiumAccent.Pink,
                                    onClick = {
                                        viewModel.resetApp(appModel.packageName)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIdentityCard(
    label: String,
    packageName: String,
    iconBitmap: ImageBitmap?,
    isSystemWhitelisted: Boolean,
    onShowSearchMenu: () -> Unit,
    searchMenu: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(iconBitmap, contentDescription = null, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
            } else {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label.take(1), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                if (isSystemWhitelisted) {
                    Text("Trong whitelist hệ thống", style = MaterialTheme.typography.labelSmall, color = PremiumAccent.Green)
                }
            }
            Box {
                IconButton(onClick = onShowSearchMenu) {
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Tra cứu ứng dụng", tint = PremiumAccent.Blue)
                }
                searchMenu()
            }
        }
    }
}

@Composable
private fun ManagementMetricRow(icon: ImageVector, accent: Color, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(20.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ManagementSelectionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Đã chọn", tint = PremiumAccent.Blue, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ManagementToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ManagementInfoRow(message: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = PremiumAccent.Orange, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AggressiveDelayRow(initialDelay: Int, onDelayChanged: (Int) -> Unit) {
    var delay by remember(initialDelay) { mutableFloatStateOf(initialDelay.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = PremiumAccent.Orange, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(20.dp))
            Text("Khoảng trễ trước khi ép dừng", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("${delay.toInt()} giây", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = delay,
            onValueChange = { delay = it },
            onValueChangeFinished = { onDelayChanged(delay.toInt()) },
            valueRange = 10f..1200f,
            modifier = Modifier.padding(start = 48.dp)
        )
    }
}

@Composable
fun PowerBadge(state: BackgroundPowerState) {
    if (state == BackgroundPowerState.OPTIMIZED || state == BackgroundPowerState.UNKNOWN) return
    val (background, content) = when (state) {
        BackgroundPowerState.RESTRICTED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        BackgroundPowerState.UNRESTRICTED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> return
    }
    Surface(color = background, shape = RoundedCornerShape(12.dp)) {
        Text(
            state.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NobgBadge(enabled: Boolean, mode: NobgMode, delaySeconds: Int) {
    if (!enabled) return
    val (text, background, content) = when (mode) {
        NobgMode.STANDARD -> Triple("NOBG Tiêu chuẩn", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        NobgMode.AGGRESSIVE -> Triple("NOBG Mạnh · ${delaySeconds}s", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        NobgMode.DISABLE_ENABLE -> Triple("NOBG Đóng băng", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Surface(color = background, shape = RoundedCornerShape(12.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DisabledBadge() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
        Text(
            "Đã vô hiệu hóa",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium
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
        clipboard.setPrimaryClip(ClipData.newPlainText("NOBG Package Query", prompt))
    } catch (_: Exception) {
    }

    val encodedQuery = Uri.encode(prompt)
    val url = when (engine) {
        SearchEngine.CHATGPT -> "https://chatgpt.com/?q=$encodedQuery"
        SearchEngine.GEMINI -> "https://gemini.google.com/app"
        SearchEngine.GOOGLE -> "https://www.google.com/search?q=$encodedQuery"
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        Toast.makeText(context, "Đã chép câu hỏi và mở ${engine.label}", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Không thể mở trình duyệt", Toast.LENGTH_SHORT).show()
    }
}
