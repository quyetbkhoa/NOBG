package com.nobg.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TreeLeafNode(
    val title: String,
    val principle: String,
    val commands: List<String>,
    val codeSnippet: String
)

data class TreeNode(
    val id: String,
    val title: String,
    val icon: String,
    val leaves: List<TreeLeafNode>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlgorithmScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val treeData = remember { getAppFlowTreeData() }
    var expandedNodeId by remember { mutableStateOf<String?>(treeData.firstOrNull()?.id) }
    var expandedLeafTitle by remember { mutableStateOf<String?>(treeData.firstOrNull()?.leaves?.firstOrNull()?.title) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📚 Sơ Đồ Cây Thuật Toán & Lệnh System", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "🌲 NGUYÊN LÝ HOẠT ĐỘNG & FLOW HỆ THỐNG NOBG",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Nhấp vào các nhánh cây bên dưới để mở chi tiết thuật toán và lệnh Shell/Shizuku thực thi tương ứng cho từng tính năng.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            treeData.forEach { node ->
                val isNodeExpanded = (expandedNodeId == node.id)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isNodeExpanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isNodeExpanded) 3.dp else 1.dp)
                ) {
                    Column {
                        // NODE HEADER
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedNodeId = if (isNodeExpanded) null else node.id
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(node.icon, fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    node.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNodeExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                if (isNodeExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // NODE LEAVES (EXPANDABLE TREE BRANCHES)
                        AnimatedVisibility(visible = isNodeExpanded) {
                            Column(
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                Spacer(Modifier.height(4.dp))

                                node.leaves.forEach { leaf ->
                                    val isLeafExpanded = (expandedLeafTitle == leaf.title)

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isLeafExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        expandedLeafTitle = if (isLeafExpanded) null else leaf.title
                                                    },
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    leaf.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(
                                                    if (isLeafExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            AnimatedVisibility(visible = isLeafExpanded) {
                                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                                    Text(
                                                        "📌 Nguyên lý (Principle):",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(leaf.principle, style = MaterialTheme.typography.bodySmall)

                                                    if (leaf.commands.isNotEmpty()) {
                                                        Spacer(Modifier.height(8.dp))
                                                        Text(
                                                            "⚙️ Lệnh Shell / Shizuku thực thi:",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        leaf.commands.forEach { cmd ->
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    cmd,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.tertiary,
                                                                    modifier = Modifier.padding(6.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    if (leaf.codeSnippet.isNotBlank()) {
                                                        Spacer(Modifier.height(8.dp))
                                                        Text(
                                                            "💻 Code Logic (Kotlin):",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.08f),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                leaf.codeSnippet,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 10.sp,
                                                                modifier = Modifier.padding(8.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getAppFlowTreeData(): List<TreeNode> {
    return listOf(
        TreeNode(
            id = "node_1",
            title = "1. Dịch Vụ Giám Sát Ngầm (MonitorService Flow)",
            icon = "🛡️",
            leaves = listOf(
                TreeLeafNode(
                    title = "1.1 Lắng nghe chuyển đổi ứng dụng (UsageStatsManager Polling 1.5s)",
                    principle = "Dùng UsageEvents.Event.MOVE_TO_FOREGROUND lắng nghe mỗi 1.5 giây để xác định tức thì package nào vừa rời khỏi màn hình chính.",
                    commands = listOf("dumpsys usagestats", "appops check-op GET_USAGE_STATS"),
                    codeSnippet = "val events = usm.queryEvents(lastEventTime, now)\nwhile(events.hasNextEvent()) { ... MOVE_TO_FOREGROUND ... }"
                ),
                TreeLeafNode(
                    title = "1.2 Theo dõi trạng thái Pin & Dự đoán thời gian sạc đầy",
                    principle = "Nhận Broadcast Receiver sạc pin, tính tốc độ sạc phi tuyến tính (% pin / giây) từ các phiên trước để dự đoán chính xác phút sạc đầy 100%.",
                    commands = listOf("dumpsys battery", "dumpsys batterystats"),
                    codeSnippet = "val session = ChargingSessionEntity(...)\nrepo.insertChargingSession(session)"
                )
            )
        ),
        TreeNode(
            id = "node_2",
            title = "2. Thuật Toán Xử Lý App Ngầm (NOBG Modes)",
            icon = "⚡",
            leaves = listOf(
                TreeLeafNode(
                    title = "2.1 Standard Mode (Hạn chế ngầm mặc định)",
                    principle = "Ghi appops RUN_IN_BACKGROUND ignore và chuyển app sang standby bucket working_set. App giữ nguyên trong danh sách gần đây nhưng không chạy ngầm.",
                    commands = listOf(
                        "am set-standby-bucket <package> working_set",
                        "appops set <package> RUN_IN_BACKGROUND ignore",
                        "appops set <package> RUN_ANY_IN_BACKGROUND ignore"
                    ),
                    codeSnippet = "ShizukuManager.exec(\"appops set \$pkg RUN_IN_BACKGROUND ignore\")"
                ),
                TreeLeafNode(
                    title = "2.2 Aggressive Mode (Ép dừng Force Stop sau delay)",
                    principle = "Khi app rời khỏi màn hình chính, kích hoạt Coroutines delay(delaySeconds). Nếu người dùng không mở lại app, tiến hành ép dừng force-stop qua Shizuku.",
                    commands = listOf(
                        "am force-stop <package>",
                        "am kill <package>"
                    ),
                    codeSnippet = "delay(delaySeconds * 1000L)\nShizukuManager.exec(\"am force-stop \$pkg\")"
                ),
                TreeLeafNode(
                    title = "2.3 Disable-Enable Mode (Đóng băng Package 100%)",
                    principle = "Vô hiệu hóa ứng dụng khỏi hệ thống Android để giải phóng 100% tài nguyên CPU & RAM. Khi người dùng mở app trong NOBG, tự động enable lại tức thì.",
                    commands = listOf(
                        "pm disable-user --user 0 <package>",
                        "pm enable <package>"
                    ),
                    codeSnippet = "ShizukuManager.exec(\"pm disable-user --user 0 \$pkg\")"
                )
            )
        ),
        TreeNode(
            id = "node_3",
            title = "3. Cấu Hình Nâng Cao System Tweaks (Hidden Settings)",
            icon = "🛠️",
            leaves = listOf(
                TreeLeafNode(
                    title = "3.1 Ép Tần Số Quét Màn Hình 120Hz/144Hz Mọi Lúc",
                    principle = "Khóa cứng dải tần số quét min_refresh_rate và peak_refresh_rate không cho hệ thống tự tụt về 60Hz khi lướt web hoặc chơi game.",
                    commands = listOf(
                        "settings put global min_refresh_rate 120.0",
                        "settings put global peak_refresh_rate 120.0",
                        "settings put global user_refresh_rate 120"
                    ),
                    codeSnippet = "ShizukuManager.exec(\"settings put global peak_refresh_rate 120.0\")"
                ),
                TreeLeafNode(
                    title = "3.2 Ép Cửa Sổ Tự Do Freeform & Chia Đôi Màn Hình (Oppo Find N3 / Foldables)",
                    principle = "Ép TẤT CẢ ứng dụng cấm chia đôi (Instagram, Zalo, Ngân hàng, Game...) phải hỗ trợ chia đôi màn hình và mở cửa sổ nổi Freeform.",
                    commands = listOf(
                        "settings put global force_resizable_activities 1",
                        "settings put global enable_freeform_support 1",
                        "settings put global oppo_force_resizable 1",
                        "settings put global coloros_force_freeform 1"
                    ),
                    codeSnippet = "ShizukuManager.exec(\"settings put global enable_freeform_support 1\")"
                ),
                TreeLeafNode(
                    title = "3.3 Tắt Giới Hạn Cảnh Báo Âm Lượng Tai Nghe 60%",
                    principle = "Xóa bỏ thông báo cảnh báo âm lượng cao khi kết nối tai nghe hoặc loa Bluetooth.",
                    commands = listOf("settings put global safe_media_volume_option 0"),
                    codeSnippet = "ShizukuManager.exec(\"settings put global safe_media_volume_option 0\")"
                ),
                TreeLeafNode(
                    title = "3.4 Tắt Giữ Kết Nối 4G/5G Ngầm Khi Dùng Wi-Fi",
                    principle = "Ngắt Modem sóng di động 4G/5G ngầm khi đang bắt Wi-Fi để tiết kiệm 15% dung lượng pin chờ.",
                    commands = listOf("settings put global mobile_data_always_on 0"),
                    codeSnippet = "ShizukuManager.exec(\"settings put global mobile_data_always_on 0\")"
                )
            )
        ),
        TreeNode(
            id = "node_4",
            title = "4. Tích Hợp Quick Settings Tiles (Thanh Cài Đặt Nhanh)",
            icon = "⚙️",
            leaves = listOf(
                TreeLeafNode(
                    title = "4.1 Bật/Tắt Nhanh USB Debugging & Wireless ADB",
                    principle = "Thay đổi cấu hình ADB trực tiếp từ Tile Quick Settings mà không cần mở Cài đặt nhà phát triển.",
                    commands = listOf(
                        "settings put global adb_enabled 1",
                        "settings put global adb_wifi_enabled 1"
                    ),
                    codeSnippet = "Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, 1)"
                ),
                TreeLeafNode(
                    title = "4.2 Tùy Chỉnh Thời Gian Sáng Màn Hình (Screen Timeout)",
                    principle = "Chuyển nhanh các nấc thời gian sáng màn hình (15s, 1m, 10m, 30m, không tắt) từ thanh Quick Settings.",
                    commands = listOf("settings put system screen_off_timeout <ms>"),
                    codeSnippet = "Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)"
                )
            )
        ),
        TreeNode(
            id = "node_5",
            title = "5. Sao Lưu & Đồng Bộ Dữ Liệu Đa Thiết Bị",
            icon = "☁️",
            leaves = listOf(
                TreeLeafNode(
                    title = "5.1 Xuất / Nhập Cấu hình NOBG Định Dạng JSON",
                    principle = "Mã hóa danh sách trạng thái app, mode và delay của từng package thành JSON để truyền sang máy khác khôi phục 1 chạm.",
                    commands = listOf("JSON Export / Import Engine"),
                    codeSnippet = "val jsonStr = repo.exportConfigJson()\nrepo.importConfigJson(jsonStr)"
                )
            )
        )
    )
}
