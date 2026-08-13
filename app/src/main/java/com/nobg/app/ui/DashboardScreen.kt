package com.nobg.app.ui

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class FeatureEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val accentColor: Color
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onOpenAppList: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBatteryStats: () -> Unit,
    onOpenFreezerShelf: () -> Unit,
    onOpenSmartTimer: () -> Unit,
    onOpenAiChat: () -> Unit,
    onOpenNotificationRead: () -> Unit,
    onOpenAlgorithm: () -> Unit,
    onOpenSystemLists: () -> Unit
) {
    val apps by viewModel.appList.collectAsState()
    val shizukuReady by viewModel.shizukuReady.collectAsState()
    val context = LocalContext.current
    val batteryPct = remember(context) { getBatteryPercent(context) }

    val nobgCount = apps.count { it.config?.enabled == true }
    val shelfCount = apps.count { it.isFrozenShelf }
    val disabledCount = apps.count { it.isDisabled }
    val topBlocked = apps.filter { (it.config?.blockedCount ?: 0) > 0 }
        .maxByOrNull { it.config?.blockedCount ?: 0 }

    val suggestions = remember(nobgCount, shelfCount, disabledCount, topBlocked, batteryPct, shizukuReady) {
        buildList {
            if (!shizukuReady) add("Shizuku chưa sẵn sàng. Mở Cài đặt để hoàn tất quyền hệ thống.")
            if (nobgCount == 0) add("Chưa có ứng dụng nào được NOBG quản lý.")
            else add("$nobgCount ứng dụng đang được kiểm soát hoạt động nền.")
            if (shelfCount > 0) add("$shelfCount ứng dụng đang ở Kệ Đóng Băng.")
            topBlocked?.let { add("${it.label} đã được chặn chạy nền ${it.config?.blockedCount} lần.") }
            if (batteryPct != null && batteryPct <= 20) add("Pin còn $batteryPct%. Hãy đóng băng các ứng dụng ít dùng.")
            if (isEmpty()) add("Hệ thống đang ổn định và không cần xử lý thêm.")
        }
    }

    val features = listOf(
        FeatureEntry("Quản lý ứng dụng", "Kiểm soát và giới hạn chạy nền", Icons.Filled.PhoneAndroid, onOpenAppList, PremiumAccent.Blue),
        FeatureEntry("Kệ Đóng Băng", "Đóng băng và mở lại nhanh", Icons.Filled.AcUnit, onOpenFreezerShelf, PremiumAccent.Teal),
        FeatureEntry("Đếm giờ thông minh", "Nhắc giờ định kỳ bằng giọng nói", Icons.Filled.Timer, onOpenSmartTimer, PremiumAccent.Orange),
        FeatureEntry("Thống kê Pin", "Mức dùng pin, CPU và tốc độ sạc", Icons.Filled.BarChart, onOpenBatteryStats, PremiumAccent.Green),
        FeatureEntry("AI Trợ lý", "Gemini, Groq và OpenRouter", Icons.Filled.SmartToy, onOpenAiChat, PremiumAccent.Purple),
        FeatureEntry("Đọc thông báo", "Đọc TTS và tóm tắt bằng AI", Icons.Filled.Notifications, onOpenNotificationRead, PremiumAccent.Pink),
        FeatureEntry("Giải thuật", "Tìm hiểu cách NOBG hoạt động", Icons.Filled.Psychology, onOpenAlgorithm, PremiumAccent.Yellow),
        FeatureEntry("Danh sách hệ thống", "Whitelist, standby và AppOps", Icons.AutoMirrored.Filled.ListAlt, onOpenSystemLists, PremiumAccent.Teal),
        FeatureEntry("Cài đặt", "Quyền, giao diện và sao lưu", Icons.Filled.Settings, onOpenSettings, PremiumAccent.Blue)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("NOBG", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Cài đặt", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            val wide = maxWidth >= 700.dp
            val gutter = if (wide) PremiumDimens.WideScreenGutter else PremiumDimens.ScreenGutter
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = gutter)
                    .padding(top = 12.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PremiumDimens.GroupGap)
            ) {
                Column(modifier = Modifier.premiumContentWidth()) {
                    Text(
                        "Tổng quan",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                    )
                    OverviewCard(
                        batteryPct = batteryPct,
                        nobgCount = nobgCount,
                        shelfCount = shelfCount,
                        disabledCount = disabledCount,
                        suggestions = suggestions
                    )
                }

                Column(modifier = Modifier.premiumContentWidth()) {
                    PremiumSectionLabel("Tính năng")
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            FeatureGroup(features.take(5), Modifier.weight(1f))
                            FeatureGroup(features.drop(5), Modifier.weight(1f))
                        }
                    } else {
                        FeatureGroup(features)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(
    batteryPct: Int?,
    nobgCount: Int,
    shelfCount: Int,
    disabledCount: Int,
    suggestions: List<String>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Thiết bị của bạn", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (batteryPct != null) "Pin hiện tại $batteryPct%" else "Đang đồng bộ trạng thái",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(Icons.Filled.BarChart, contentDescription = null, tint = PremiumAccent.Green)
            }
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryStat(nobgCount.toString(), "Quản lý", Modifier.weight(1f))
                SummaryStat(shelfCount.toString(), "Đóng băng", Modifier.weight(1f))
                SummaryStat(disabledCount.toString(), "Vô hiệu hóa", Modifier.weight(1f))
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 20.dp),
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Gợi ý", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            suggestions.take(3).forEachIndexed { index, suggestion ->
                Text(
                    suggestion,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (index < suggestions.take(3).lastIndex) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FeatureGroup(features: List<FeatureEntry>, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        features.forEachIndexed { index, feature ->
            PremiumNavigationRow(
                title = feature.title,
                subtitle = feature.subtitle,
                icon = feature.icon,
                accent = feature.accentColor,
                onClick = feature.onClick
            )
            if (index < features.lastIndex) PremiumInsetDivider()
        }
    }
}

private fun getBatteryPercent(context: Context): Int? = try {
    (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
} catch (_: Exception) {
    null
}
