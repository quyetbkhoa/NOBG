package com.nobg.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nobg.app.data.NobgRepository
import com.nobg.app.service.MonitorService
import com.nobg.app.ui.AddShelfAppDialog
import com.nobg.app.ui.theme.NobgTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class WidgetShelfAppUi(
    val packageName: String,
    val appName: String,
    val icon: Bitmap?
)

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))

        // Set result to CANCELED first in case user backs out without saving
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            val themeMode = com.nobg.app.data.NobgRepository(this).getThemeMode()
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            NobgTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WidgetConfigScreen(
                        initialConfig = WidgetConfigManager.getConfig(this),
                        onSave = { config ->
                            WidgetConfigManager.saveConfig(this, config)
                            FrozenAppsWidgetProvider.updateAllWidgets(this)

                            val resultValue = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(Activity.RESULT_OK, resultValue)
                            finish()
                        },
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    initialConfig: WidgetConfig,
    onSave: (WidgetConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { NobgRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var theme by remember { mutableStateOf(initialConfig.theme) }
    var textColorSetting by remember { mutableStateOf(initialConfig.textColor) }
    var opacityPct by remember { mutableFloatStateOf(initialConfig.opacityPct.toFloat()) }
    var numColumns by remember { mutableIntStateOf(initialConfig.numColumns) }
    var iconSizeDp by remember { mutableIntStateOf(initialConfig.iconSizeDp) }
    var cornerRadiusDp by remember { mutableIntStateOf(initialConfig.cornerRadiusDp) }
    var shelfApps by remember { mutableStateOf<List<WidgetShelfAppUi>>(emptyList()) }
    var shelfRevision by remember { mutableIntStateOf(0) }
    var showAddApps by remember { mutableStateOf(false) }
    var isShelfLoading by remember { mutableStateOf(true) }

    LaunchedEffect(shelfRevision) {
        isShelfLoading = true
        shelfApps = withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            repo.getFrozenShelfApps().map { entity ->
                try {
                    val appInfo = packageManager.getApplicationInfo(entity.packageName, 0)
                    WidgetShelfAppUi(
                        packageName = entity.packageName,
                        appName = packageManager.getApplicationLabel(appInfo).toString(),
                        icon = drawableToBitmap(packageManager.getApplicationIcon(appInfo), 48)
                    )
                } catch (_: Exception) {
                    WidgetShelfAppUi(entity.packageName, entity.packageName, null)
                }
            }
        }
        isShelfLoading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt Widget", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            onSave(
                                WidgetConfig(
                                    theme = theme,
                                    textColor = textColorSetting,
                                    opacityPct = opacityPct.toInt(),
                                    numColumns = numColumns,
                                    iconSizeDp = iconSizeDp,
                                    cornerRadiusDp = cornerRadiusDp
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Lưu và cập nhật Widget", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 840.dp)
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = "ỨNG DỤNG TRÊN KỆ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Quản lý nội dung Widget",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Thêm hoặc xóa app tại đây; Widget cập nhật ngay.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = { showAddApps = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Thêm")
                        }
                    }

                    HorizontalDivider()

                    when {
                        isShelfLoading -> Box(
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        shelfApps.isEmpty() -> Text(
                            "Chưa có ứng dụng. Widget hiện chỉ có ô Cài đặt.",
                            modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> shelfApps.forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon.asImageBitmap(),
                                        contentDescription = app.appName,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(app.appName.take(1).uppercase(), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        app.appName,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val removed = withContext(Dispatchers.IO) {
                                                repo.toggleAppFrozenShelf(app.packageName, false)
                                            }
                                            if (!removed) {
                                                Toast.makeText(context, "Không thể rã đông app. Hãy kiểm tra Shizuku/ADB.", Toast.LENGTH_SHORT).show()
                                            }
                                            FrozenAppsWidgetProvider.updateAllWidgets(context)
                                            shelfRevision++
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Xóa ${app.appName} khỏi Kệ",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // LIVE WIDGET PREVIEW CARD
            Text(
                text = "XEM TRƯỚC WIDGET",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            val bgAlpha = opacityPct / 100f
            val previewBgColor = if (theme == "DARK") {
                Color(15, 23, 42, (bgAlpha * 255).toInt())
            } else {
                Color(255, 255, 255, (bgAlpha * 255).toInt())
            }

            val appTextColor = when (textColorSetting) {
                "WHITE" -> Color.White
                "BLACK" -> Color(0xFF0F172A)
                "ACCENT" -> if (theme == "DARK") Color(0xFF38BDF8) else Color(0xFF0284C7)
                else -> if (theme == "DARK") Color.White else Color(0xFF0F172A) // SYSTEM
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = previewBgColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    val previewItems = shelfApps
                        .take((numColumns - 1).coerceAtLeast(0))
                        .map { it.appName to false } + ("Cài đặt" to true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        previewItems.forEachIndexed { index, (name, isSettings) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f).padding(4.dp)
                            ) {
                                val radiusCornerDp = when (cornerRadiusDp) {
                                    12 -> 12.dp
                                    18 -> 18.dp
                                    else -> 28.dp // circle
                                }

                                Box(
                                    modifier = Modifier
                                        .size((iconSizeDp * 0.85f).dp)
                                        .clip(RoundedCornerShape(radiusCornerDp))
                                        .background(
                                            if (isSettings) Color(0xFF0EA5E9)
                                            else listOf(Color(0xFF0068FF), Color(0xFF1877F2), Color(0xFFFF0000))[index % 3]
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSettings) {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size((iconSizeDp * 0.52f).dp)
                                        )
                                    } else {
                                        Text(
                                            text = name.take(1),
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = (iconSizeDp * 0.35f).sp
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = appTextColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        repeat((numColumns - previewItems.size).coerceAtLeast(0)) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            HorizontalDivider()

            // 1. CHỦ ĐỀ NỀN WIDGET
            Text(
                text = "1. CHỦ ĐỀ NỀN (THEME)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("DARK" to "⬛ Nền Đen", "LIGHT" to "⬜ Nền Trắng").forEach { (th, label) ->
                    val selected = (theme == th)
                    OutlinedButton(
                        onClick = { theme = th },
                        modifier = Modifier.weight(1f),
                        colors = if (selected) ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            // 2. MÀU CHỮ WIDGET (TEXT COLOR)
            Text(
                text = "2. MÀU CHỮ WIDGET (TEXT COLOR)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "SYSTEM" to "⚙️ Hệ thống",
                    "WHITE" to "⚪ Trắng",
                    "BLACK" to "⚫ Đen",
                    "ACCENT" to "🔷 Xanh"
                ).forEach { (tc, label) ->
                    val selected = (textColorSetting == tc)
                    OutlinedButton(
                        onClick = { textColorSetting = tc },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                        colors = if (selected) ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 11.sp)
                    }
                }
            }

            // 3. ĐỘ MỜ NỀN (OPACITY SLIDER)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "3. ĐỘ MỜ NỀN (OPACITY)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${opacityPct.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = opacityPct,
                onValueChange = { opacityPct = it },
                valueRange = 0f..100f,
                steps = 19 // 5% increments
            )

            // 4. SỐ CỘT (COLUMNS)
            Text(
                text = "4. SỐ CỘT HÀNG (COLUMNS)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2 to "2 Cột", 3 to "3 Cột", 4 to "4 Cột").forEach { (col, label) ->
                    val selected = (numColumns == col)
                    OutlinedButton(
                        onClick = { numColumns = col },
                        modifier = Modifier.weight(1f),
                        colors = if (selected) ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            // 5. KÍCH THƯỚC ICON APP
            Text(
                text = "5. KÍCH THƯỚC ICON ỨNG DỤNG",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(36 to "Nhỏ (36dp)", 48 to "Vừa (48dp)", 56 to "Lớn (56dp)").forEach { (sz, label) ->
                    val selected = (iconSizeDp == sz)
                    OutlinedButton(
                        onClick = { iconSizeDp = sz },
                        modifier = Modifier.weight(1f),
                        colors = if (selected) ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 12.sp)
                    }
                }
            }

            // 6. BO GÓC AVATAR ỨNG DỤNG
            Text(
                text = "6. BO GÓC AVATAR ỨNG DỤNG",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(12 to "Vừa (12dp)", 18 to "Bo Tròn (18dp)", 24 to "Tròn (Circle)").forEach { (rad, label) ->
                    val selected = (cornerRadiusDp == rad)
                    OutlinedButton(
                        onClick = { cornerRadiusDp = rad },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = if (selected) ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    if (showAddApps) {
        AddShelfAppDialog(
            context = context,
            currentShelfPkgs = shelfApps.mapTo(mutableSetOf()) { it.packageName },
            onlyUserApps = true,
            onDismiss = { showAddApps = false },
            onConfirm = { addedPackages ->
                showAddApps = false
                scope.launch {
                    val addedCount = withContext(Dispatchers.IO) {
                        addedPackages.count { repo.toggleAppFrozenShelf(it, true) }
                    }
                    if (addedPackages.isNotEmpty() && addedCount < addedPackages.size) {
                        Toast.makeText(
                            context,
                            if (addedCount == 0) "Không thể đóng băng app. Hãy kiểm tra Shizuku/ADB."
                            else "Đã thêm $addedCount/${addedPackages.size} app.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    FrozenAppsWidgetProvider.updateAllWidgets(context)
                    shelfRevision++
                }
            }
        )
    }
}

private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable, sizeDp: Int): Bitmap {
    val density = android.content.res.Resources.getSystem().displayMetrics.density
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
    if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
        return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
    }
    return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
    }
}
