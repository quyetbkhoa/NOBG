package com.nobg.app.ui

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val accentColor: androidx.compose.ui.graphics.Color
)

@OptIn(ExperimentalMaterial3Api::class)
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

    val batteryPct = remember { getBatteryPercent(context) }

    val nobgCount = apps.count { it.config?.enabled == true }
    val shelfCount = apps.count { it.isFrozenShelf }
    val disabledCount = apps.count { it.isDisabled }
    val topBlocked = apps
        .filter { (it.config?.blockedCount ?: 0) > 0 }
        .maxByOrNull { it.config?.blockedCount ?: 0 }

    val suggestions = remember(nobgCount, shelfCount, disabledCount, topBlocked, batteryPct) {
        buildList {
            if (!shizukuReady) {
                add("⚠️ Shizuku chưa sẵn sàng — hãy mở Cài đặt để cấp quyền trước khi dùng tính năng.")
            }
            if (nobgCount == 0) {
                add("🛡️ Chưa app nào bật NOBG — bật quản lý để chặn chạy ngầm, tiết kiệm pin.")
            } else {
                add("🛡️ Có $nobgCount app đang được NOBG chặn chạy ngầm.")
            }
            if (shelfCount > 0) {
                add("🧊 Có $shelfCount app đang nằm trong Kệ Đóng Bằng — bấm icon trên widget để mở lại nhanh.")
            }
            if (disabledCount > 0) {
                add("❄️ $disabledCount app đang bị vô hiệu hóa hoàn toàn.")
            }
            topBlocked?.let { app ->
                add("🚫 \"${app.label}\" bị chặn chạy ngầm ${app.config?.blockedCount} lần — đang hoạt động hiệu quả.")
            }
            val pct = batteryPct
            if (pct != null && pct <= 20) {
                add("🔋 Pin đang yếu ($pct%) — nên đóng băng các app nền để giữ pin.")
            } else if (pct != null && pct >= 90) {
                add("🔋 Pin đang rất khỏe ($pct%).")
            }
            if (isEmpty()) {
                add("✅ Hệ thống đang ổn định — mọi thứ hoạt động tốt.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("NOBG", fontWeight = FontWeight.Bold)
                        Text(
                            "  ·  Trang chủ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Cài đặt")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // TỔNG QUAN & GỢI Ý
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "📋 Tổng quan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        batteryPct?.let {
                            Text(
                                "🔋 $it%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStat("🛡️", "$nobgCount", "Đang quản lý", Modifier.weight(1f))
                        SummaryStat("🧊", "$shelfCount", "Kệ Đóng Bằng", Modifier.weight(1f))
                        SummaryStat("❄️", "$disabledCount", "Đã vô hiệu hóa", Modifier.weight(1f))
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )

                    Text(
                        "💡 Gợi ý cho bạn",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    suggestions.forEach { s ->
                        Text(
                            s,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // DANH SÁCH TÍNH NĂNG
            Text(
                "Tính năng",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val features = listOf(
                FeatureEntry(
                    "Quản lý ứng dụng",
                    "Bật/tắt NOBG, chế độ chặn",
                    Icons.Filled.PhoneAndroid,
                    onOpenAppList,
                    MaterialTheme.colorScheme.primary
                ),
                FeatureEntry(
                    "Kệ Đóng Bằng",
                    "Đóng băng nhanh 1 chạm",
                    Icons.Filled.AcUnit,
                    onOpenFreezerShelf,
                    MaterialTheme.colorScheme.tertiary
                ),
                FeatureEntry(
                    "Đếm giờ thông minh",
                    "Hẹn giờ đóng băng",
                    Icons.Filled.Timer,
                    onOpenSmartTimer,
                    MaterialTheme.colorScheme.secondary
                ),
                FeatureEntry(
                    "Thống kê Pin",
                    "Pin, tốc độ sạc, CPU",
                    Icons.Filled.BarChart,
                    onOpenBatteryStats,
                    MaterialTheme.colorScheme.primary
                ),
                FeatureEntry(
                    "AI Trợ lý",
                    "Gemini, Groq, OpenRouter",
                    Icons.Filled.SmartToy,
                    onOpenAiChat,
                    MaterialTheme.colorScheme.tertiary
                ),
                FeatureEntry(
                    "Đọc thông báo",
                    "TTS + tóm tắt AI",
                    Icons.Filled.Notifications,
                    onOpenNotificationRead,
                    MaterialTheme.colorScheme.secondary
                ),
                FeatureEntry(
                    "Giải thuật",
                    "Cách NOBG hoạt động",
                    Icons.Filled.Psychology,
                    onOpenAlgorithm,
                    MaterialTheme.colorScheme.primary
                ),
                FeatureEntry(
                    "Danh sách hệ thống",
                    "Whitelist, standby, appops",
                    Icons.Filled.ListAlt,
                    onOpenSystemLists,
                    MaterialTheme.colorScheme.tertiary
                ),
                FeatureEntry(
                    "Cài đặt",
                    "Quyền, chủ đề, sao lưu",
                    Icons.Filled.Settings,
                    onOpenSettings,
                    MaterialTheme.colorScheme.secondary
                )
            )

            features.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { feature ->
                        FeatureCard(feature, Modifier.weight(1f))
                    }
                    if (rowItems.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$emoji $value", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FeatureCard(feature: FeatureEntry, modifier: Modifier = Modifier) {
    Card(
        onClick = feature.onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = feature.accentColor.copy(alpha = 0.15f)
            ) {
                Icon(
                    feature.icon,
                    contentDescription = null,
                    tint = feature.accentColor,
                    modifier = Modifier.padding(8.dp).size(22.dp)
                )
            }
            Text(
                feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                feature.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp
            )
        }
    }
}

private fun getBatteryPercent(context: Context): Int? {
    return try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (_: Exception) {
        null
    }
}
