package com.nobg.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobg.app.ui.theme.NobgTheme

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
    var theme by remember { mutableStateOf(initialConfig.theme) }
    var textColorSetting by remember { mutableStateOf(initialConfig.textColor) }
    var opacityPct by remember { mutableStateOf(initialConfig.opacityPct.toFloat()) }
    var numColumns by remember { mutableStateOf(initialConfig.numColumns) }
    var iconSizeDp by remember { mutableStateOf(initialConfig.iconSizeDp) }
    var cornerRadiusDp by remember { mutableStateOf(initialConfig.cornerRadiusDp) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎨 Tùy chỉnh Giao diện Widget", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
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
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Lưu & Cập nhật Widget", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // LIVE WIDGET PREVIEW CARD
            Text(
                text = "👁️ XEM TRƯỚC GIAO DIỆN REALTIME",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            val bgAlpha = opacityPct / 100f
            val previewBgColor = if (theme == "DARK") {
                Color(15, 23, 42, (bgAlpha * 255).toInt())
            } else {
                Color(255, 255, 255, (bgAlpha * 255).toInt())
            }

            val titleColor = when (textColorSetting) {
                "WHITE" -> Color.White
                "BLACK" -> Color(0xFF0F172A)
                "ACCENT" -> if (theme == "DARK") Color(0xFF38BDF8) else Color(0xFF0284C7)
                else -> if (theme == "DARK") Color(0xFF38BDF8) else Color(0xFF0284C7) // SYSTEM
            }

            val countColor = when (textColorSetting) {
                "WHITE" -> Color.White.copy(alpha = 0.8f)
                "BLACK" -> Color(0xFF475569)
                "ACCENT" -> if (theme == "DARK") Color(0xFF7DD3FC) else Color(0xFF0369A1)
                else -> if (theme == "DARK") Color(0xFF94A3B8) else Color(0xFF64748B) // SYSTEM
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = previewBgColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NOBG",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "2 app",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = countColor
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = countColor
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Grid preview items
                    val sampleApps = listOf("Zalo", "Facebook", "YouTube", "Messenger")
                    val displayCount = numColumns.coerceAtMost(4)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (i in 0 until displayCount) {
                            val name = sampleApps[i]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(4.dp)
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
                                            when (i) {
                                                0 -> Color(0xFF0068FF)
                                                1 -> Color(0xFF1877F2)
                                                2 -> Color(0xFFFF0000)
                                                else -> Color(0xFF0084FF)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name.take(1),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (iconSizeDp * 0.35f).sp
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = appTextColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // 1. CHỦ ĐỀ NỀN WIDGET
            Text(
                text = "1. CHỦ ĐỀ NỀN (THEME)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
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
                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            // 2. MÀU CHỮ WIDGET (TEXT COLOR)
            Text(
                text = "2. MÀU CHỮ WIDGET (TEXT COLOR)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
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
                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp)
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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${opacityPct.toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
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
                fontWeight = FontWeight.Bold,
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
                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            // 5. KÍCH THƯỚC ICON APP
            Text(
                text = "5. KÍCH THƯỚC ICON ỨNG DỤNG",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
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
                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    }
                }
            }

            // 6. BO GÓC AVATAR ỨNG DỤNG
            Text(
                text = "6. BO GÓC AVATAR ỨNG DỤNG",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
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
                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}
