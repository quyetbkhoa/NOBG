package com.nobg.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AlgorithmTopic(
    val title: String,
    val icon: String,
    val description: String,
    val algorithm: String,
    val commands: List<String>
) {
    APPOPS_RESTRICT(
        title = "Hạn chế pin & AppOps",
        icon = "🛡️",
        description = "Hạn chế các quyền chạy ngầm mặc định của Android mà không cần tắt ứng dụng hoàn toàn.",
        algorithm = """
            1. Sao lưu (Backup): Đọc trạng thái AppOps gốc của app lưu vào Room Database (bảng app_backup_state).
            2. Vô hiệu hóa 9 quyền AppOps nhạy cảm:
               - RUN_IN_BACKGROUND (Chạy ngầm)
               - RUN_ANY_IN_BACKGROUND (Chạy ngầm mọi lúc)
               - START_FOREGROUND (Khởi tạo Foreground Service)
               - WAKE_LOCK (Giữ CPU không ngủ)
               - ALARM_WAKEUP (Tự đánh thức máy)
               - BOOT_COMPLETED (Tự khởi chạy cùng hệ thống)
               - SYSTEM_ALERT_WINDOW (Vẽ trên ứng dụng khác)
            3. Khôi phục (Restore): Khi tắt NOBG, trả lại đúng quyền AppOps gốc từ database.
        """.trimIndent(),
        commands = listOf(
            "cmd appops set <package> RUN_IN_BACKGROUND ignore",
            "cmd appops set <package> RUN_ANY_IN_BACKGROUND ignore",
            "cmd appops set <package> START_FOREGROUND ignore",
            "appops set <package> WAKE_LOCK ignore",
            "dumpsys deviceidle whitelist -<package>"
        )
    ),

    FORCE_STOP(
        title = "Ép dừng ngầm (Aggressive)",
        icon = "⚡",
        description = "Ép dừng hẳn ứng dụng ngầm sau khoảng thời gian delay thiết lập khi người dùng rời khỏi app.",
        algorithm = """
            1. Polling 1.5s/lần: MonitorService truy vấn UsageStatsManager (queryEvents).
            2. Phát hiện sự kiện: Khi app phát ra event MOVE_TO_BACKGROUND (người dùng bấm Home/Back rời app).
            3. Đếm ngược Delay: Bắt đầu đếm ngược delaySeconds (10s đến 1200s).
            4. Thực thi diệt ngầm: Gọi lệnh Shizuku force-stop để giải phóng RAM & CPU lập tức.
        """.trimIndent(),
        commands = listOf(
            "am force-stop <package>",
            "pm force-stop <package>",
            "cmd activity force-stop <package>"
        )
    ),

    DISABLE_ENABLE(
        title = "Đóng băng App (Disable-Enable)",
        icon = "🧊",
        description = "Vô hiệu hóa package ứng dụng hoàn toàn để tiết kiệm 100% pin & tài nguyên.",
        algorithm = """
            1. Đóng băng (Freeze): Chuyển trạng thái Package sang COMPONENT_ENABLED_STATE_DISABLED_USER (3). App biến mất khỏi hệ thống.
            2. Xả đóng băng (Unfreeze): Khi bấm vào Shortcut hoặc Widget, UnfreezeAndLaunchActivity gọi lệnh Enable app và mở ngay lập tức.
            3. Tự đóng băng lại: Khi người dùng dùng xong và rời khỏi app, MonitorService tự động gọi lệnh Disable app lại.
        """.trimIndent(),
        commands = listOf(
            "pm disable-user --user 0 <package>",
            "pm enable <package>",
            "cmd package disable-user --user 0 <package>"
        )
    ),

    CPU_POWERHAL(
        title = "Giảm xung CPU (PowerHAL)",
        icon = "⚡",
        description = "Ép Android PowerHAL hạ tần số xung nhịp tối đa của các nhân CPU hiệu năng cao.",
        algorithm = """
            1. Kích hoạt PowerHAL Mode 1: Ép hệ điều hành chuyển Governor CPU sang chế độ Tiết kiệm điện.
            2. Giới hạn xung (Cap Max Frequency): Hạ 20% - 40% tần số xung nhịp tối đa của các nhân CPU Big Cores.
            3. Theo dõi & Ghi log: Hệ thống tự động ghi nhận dữ liệu GHz và vẽ đồ thị 2h gần nhất.
        """.trimIndent(),
        commands = listOf(
            "cmd power set-mode 1",
            "settings put global low_power 1",
            "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
        )
    ),

    BATTERY_PREDICT(
        title = "Dự đoán & Thống kê Pin",
        icon = "📊",
        description = "Thuật toán dự đoán thời gian sạc đầy và tính toán tốc độ hao pin.",
        algorithm = """
            1. Dự đoán thời gian sạc đầy (Non-linear Interpolation):
               RemainingMinutes = ∑ (DurationPerPct[i]) cho i từ CurrentLevel -> 100%
            2. Đo tốc độ xả pin (%/giờ):
               DrainRate (%/h) = (ΔBatteryPct / ΔTimeHours)
            3. Thống kê OnScreen / OffScreen drain rate tính riêng theo mốc thời gian.
        """.trimIndent(),
        commands = listOf(
            "dumpsys battery",
            "dumpsys batterystats",
            "dumpsys deviceidle"
        )
    ),

    QS_TILES(
        title = "Quick Settings & ADB",
        icon = "⚙️",
        description = "Tích hợp các nút bật tắt nhanh trên thanh trạng thái Quick Settings của Android.",
        algorithm = """
            1. Cấp quyền WRITE_SECURE_SETTINGS qua Shizuku/ADB.
            2. ContentObserver: Lắng nghe thay đổi cài đặt hệ thống thời gian thực.
            3. Ghi trực tiếp Settings.Global / Settings.System từ TileService.
        """.trimIndent(),
        commands = listOf(
            "pm grant com.nobg.app android.permission.WRITE_SECURE_SETTINGS",
            "settings put global adb_enabled 1",
            "settings put system screen_off_timeout 600000"
        )
    )
}

@Composable
fun AlgorithmDocDialog(
    onDismiss: () -> Unit
) {
    var selectedTopic by remember { mutableStateOf(AlgorithmTopic.APPOPS_RESTRICT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📘 Thuật toán & Lệnh hệ thống", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Menu dạng thanh cuộn ngang chứa các nút tính năng
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlgorithmTopic.values().forEach { topic ->
                        FilterChip(
                            selected = selectedTopic == topic,
                            onClick = { selectedTopic = topic },
                            label = { Text("${topic.icon} ${topic.title}") }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "${selectedTopic.icon} ${selectedTopic.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        selectedTopic.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))
                    Text("🧠 Nguyên lý & Thuật toán:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            selectedTopic.algorithm,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("💻 Lệnh Shell / ADB / Shizuku thực thi:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            selectedTopic.commands.forEach { cmd ->
                                Text(
                                    "> $cmd",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.inverseOnSurface
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
