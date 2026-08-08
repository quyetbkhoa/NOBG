package com.nobg.app.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobg.app.shell.PrivilegedShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Các danh sách hệ thống có thể xem qua ADB/Shizuku */
enum class SystemListType(
    val title: String,
    val subtitle: String,
    val purpose: String
) {
    DOZE_WHITELIST(
        "🛡️ Doze Whitelist",
        "App được miễn khỏi chế độ ngủ sâu (Doze)",
        "Danh sách các ứng dụng được hệ thống miễn khỏi chế độ Doze — khi máy ở trạng thái ngủ sâu, các app này vẫn được chạy nền, giữ mạng và nhận thông báo gần như bình thường.\n\nApp không nằm trong danh sách sẽ bị \"đóng băng\" tạm thời khi màn hình tắt để tiết kiệm pin.\n\nDanh sách thường gồm: launcher, đồng hồ/báo thức, app nhắn tin quan trọng, Google Play Services...\n\nNOBG có thể thêm/xóa app khỏi danh sách này qua lệnh dumpsys deviceidle whitelist +pkg / -pkg."
    ),
    NETWORK_WHITELIST(
        "📶 Data Saver Whitelist",
        "App được dùng dữ liệu nền khi Data Saver bật",
        "Danh sách ứng dụng được phép dùng dữ liệu nền khi bật chế độ hạn chế dữ liệu nền (Data Saver / restrictBackground) hoặc khi máy ở Doze.\n\nApp không nằm trong whitelist sẽ bị chặn truy cập mạng khi chạy nền — tương đương việc \"không cho app chạy mạng ngầm\" rất hiệu quả để tiết kiệm pin và data.\n\nDump cũng chứa blacklist — app bị chặn dữ liệu nền kể cả khi bình thường."
    ),
    STANDBY_BUCKETS(
        "🪫 App Standby Buckets",
        "Mức ưu tiên chạy nền của từng app",
        "Hệ thống phân loại từng ứng dụng vào 6 nhóm theo tần suất sử dụng:\n• active — đang dùng\n• working_set — hay dùng\n• frequent — thường xuyên\n• rare — ít dùng\n• restricted — hạn chế tối đa\n• never — chưa từng dùng\n\nApp ở nhóm càng thấp càng bị giới hạn job nền, truy cập mạng và báo thức.\n\nNOBG có thể ép một app vào nhóm \"restricted\" bằng lệnh am set-standby-bucket để ngăn chạy nền, không cần bật chế độ tiết kiệm pin."
    ),
    BACKGROUND_APP_OPS(
        "🚫 AppOps Nền bị chặn",
        "App đang bị chặn chạy nền qua AppOps",
        "Danh sách các ứng dụng đang bị chặn các thao tác nền qua AppOps:\n• RUN_IN_BACKGROUND — không cho chạy nền\n• START_FOREGROUND — không cho tự mở dịch vụ nổi\n• RUN_ANY_IN_BACKGROUND — không cho chạy nền mọi lúc\n\nĐây CHÍNH LÀ cơ chế NOBG dùng để chặn app chạy ngầm (appops set ... deny).\n\nApp bị chặn 3 thao tác này gần như không thể chạy ngầm, không gọi điện thoại/tin nhắn trong nền, không gửi dữ liệu nền."
    ),
    USAGE_STATS_ACCESS(
        "📊 Usage Stats Access",
        "App được cấp quyền đọc thống kê sử dụng",
        "Danh sách ứng dụng được cấp quyền đọc thống kê sử dụng (PACKAGE_USAGE_STATS) — biết app nào được mở khi nào, dùng bao lâu, tần suất...\n\nQuyền này cấp qua lệnh pm grant <pkg> android.permission.PACKAGE_USAGE_STATS (cần ADB/Shizuku) hoặc từ màn hình cấp quyền trong Cài đặt.\n\nNOBG tự cấp cho chính nó để theo dõi app đang chạy và thống kê thời gian sử dụng màn hình."
    ),
    NOTIFICATION_LISTENERS(
        "🔔 Notification Listeners",
        "App được phép đọc mọi thông báo",
        "Các ứng dụng được phép đọc toàn bộ thông báo trên máy (NotificationListenerService) — gồm cả nội dung tin nhắn.\n\nNOBG nằm trong danh sách này để đọc thông báo bằng giọng nói và tóm tắt bằng AI.\n\nĐây là quyền rất nhạy cảm về quyền riêng tư — chỉ nên cấp cho ứng dụng đáng tin cậy."
    ),
    ACCESSIBILITY_SERVICES(
        "♿ Accessibility Services",
        "Dịch vụ trợ năng đang bật",
        "Danh sách các dịch vụ trợ năng (AccessibilityService) đang được bật.\n\nDịch vụ trợ năng có quyền rất mạnh: đọc toàn bộ nội dung trên màn hình, thao tác thay người dùng (bấm nút, nhập chữ)...\n\nChỉ nên bật cho ứng dụng bạn tin tưởng tuyệt đối. NOBG không sử dụng dịch vụ trợ năng."
    ),
    DEVICE_ADMINS(
        "🏛️ Device Admins",
        "App quản trị thiết bị đang kích hoạt",
        "Danh sách các ứng dụng được cấp quyền Quản trị viên thiết bị (Device Administrator).\n\nQuyền này rất mạnh: khóa màn hình, xóa toàn bộ dữ liệu máy (factory reset), đổi mật khẩu, theo dõi hoạt động...\n\nChỉ nên kích hoạt cho ứng dụng bạn tin tưởng tuyệt đối (như Find My Device, ứng dụng bảo mật của cơ quan).\n\nBạn có thể quản lý tại Cài đặt → Bảo mật → Ứng dụng quản trị thiết bị.\n\nNOBG không yêu cầu quyền này."
    ),
    APP_HIBERNATION(
        "😴 App Hibernation",
        "App bị đưa vào ngủ đông (Android 12+)",
        "App Hibernation là tính năng từ Android 12: khi một ứng dụng không được mở trong nhiều tuần, hệ thống tự động \"ngủ đông\" nó — thu hồi quyền đã cấp, xóa bộ nhớ đệm, đóng băng để giải phóng tài nguyên.\n\nApp bị ngủ đông sẽ: mất quyền đã cấp, không chạy nền, không nhận thông báo cho đến khi bạn mở lại.\n\nDanh sách này cho biết app nào đang ở trạng thái ngủ đông. Không hỗ trợ trên máy chạy Android dưới 12."
    )
}

private data class SystemListItem(
    val title: String,
    val subtitle: String,
    val badge: String? = null,
    val badgeColor: Color? = null
)

private sealed class LoadState {
    data object Loading : LoadState()
    data class Success(val items: List<SystemListItem>) : LoadState()
    data class Error(val message: String) : LoadState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemListsScreen(onBack: () -> Unit) {
    var selectedType by remember { mutableStateOf<SystemListType?>(null) }
    var infoType by remember { mutableStateOf<SystemListType?>(null) }

    if (selectedType != null) {
        SystemListDetailScreen(
            type = selectedType!!,
            onBack = { selectedType = null },
            onShowInfo = { infoType = selectedType!! }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("📋 Danh sách hệ thống", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "Xem các danh sách whitelist & phân loại của máy (đọc qua Shizuku/ADB).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(SystemListType.entries.toList()) { type ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedType = type }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(type.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    type.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { infoType = type }) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = "Thông tin",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    infoType?.let { type ->
        InfoDialog(type = type, onDismiss = { infoType = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemListDetailScreen(
    type: SystemListType,
    onBack: () -> Unit,
    onShowInfo: () -> Unit
) {
    val context = LocalContext.current
    var state by remember(type) { mutableStateOf<LoadState>(LoadState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(type, reloadKey) {
        state = LoadState.Loading
        state = withContext(Dispatchers.IO) {
            try {
                if (!PrivilegedShell.isReady()) {
                    LoadState.Error("Chưa có quyền shell — hãy bật Shizuku hoặc kết nối ADB trong Cài đặt trước.")
                } else {
                    LoadState.Success(loadList(type, context))
                }
            } catch (e: Exception) {
                LoadState.Error("Không đọc được danh sách: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(type.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = onShowInfo) {
                        Icon(Icons.Filled.Info, contentDescription = "Thông tin danh sách")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is LoadState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text("Đang đọc dữ liệu hệ thống...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            is LoadState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { reloadKey++ }) {
                            Text("🔄 Thử lại")
                        }
                    }
                }
            }
            is LoadState.Success -> {
                if (s.items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Danh sách trống — không có app nào.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "${s.items.size} mục trong danh sách",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(s.items) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                item.badge?.let { badge ->
                                    Text(
                                        badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = item.badgeColor ?: MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                (item.badgeColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
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

@Composable
private fun InfoDialog(type: SystemListType, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
        title = { Text(type.title, fontWeight = FontWeight.Bold) },
        text = {
            Text(type.purpose, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đã hiểu")
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────
// DATA LOADERS (chạy trên Dispatchers.IO)
// ─────────────────────────────────────────────────────────────

private suspend fun loadList(type: SystemListType, context: Context): List<SystemListItem> {
    return when (type) {
        SystemListType.DOZE_WHITELIST -> loadDozeWhitelist(context)
        SystemListType.NETWORK_WHITELIST -> loadNetworkWhitelist(context)
        SystemListType.STANDBY_BUCKETS -> loadStandbyBuckets(context)
        SystemListType.BACKGROUND_APP_OPS -> loadBackgroundAppOps(context)
        SystemListType.USAGE_STATS_ACCESS -> loadUsageStatsAccess(context)
        SystemListType.NOTIFICATION_LISTENERS -> loadNotificationListeners(context)
        SystemListType.ACCESSIBILITY_SERVICES -> loadAccessibilityServices(context)
        SystemListType.DEVICE_ADMINS -> loadDeviceAdmins(context)
        SystemListType.APP_HIBERNATION -> loadAppHibernation(context)
    }
}

private suspend fun execShell(cmd: String): String {
    val out = PrivilegedShell.exec(cmd)
    if (out.startsWith("ERROR")) throw RuntimeException(out.removePrefix("ERROR:").trim())
    return out
}

private fun labelOf(context: Context, pkg: String): String {
    return try {
        val pm = context.packageManager
        val label = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        if (label.isBlank()) pkg else label
    } catch (_: Exception) {
        pkg
    }
}

private suspend fun loadUidToPackageMap(): Map<Int, String> {
    val out = execShell("pm list packages -U")
    return out.lineSequence()
        .mapNotNull { line ->
            val m = Regex("package:(\\S+)\\s+uid:(\\d+)").find(line) ?: return@mapNotNull null
            m.groupValues[2].toIntOrNull()?.let { it to m.groupValues[1] }
        }
        .toMap()
}

// 🛡️ Doze Whitelist
private suspend fun loadDozeWhitelist(context: Context): List<SystemListItem> {
    val out = execShell("dumpsys deviceidle whitelist")
    val items = mutableListOf<SystemListItem>()
    out.lineSequence().forEach { line ->
        val t = line.trim()
        val pkgPart = when {
            t.startsWith("system whitelist:") || t.startsWith("system idle whitelist:") -> {
                t.substringAfter(":").trim()
            }
            t.startsWith("user whitelist:") -> t.substringAfter(":").trim()
            t.startsWith("temp whitelist:") || t.startsWith("temporary whitelist:") -> {
                t.substringAfter(":").trim()
            }
            else -> null
        }
        pkgPart?.let { raw ->
            raw.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { pkg ->
                val base = pkg.substringBefore("(").trim()
                val isTemp = t.startsWith("temp")
                items.add(
                    SystemListItem(
                        title = labelOf(context, base),
                        subtitle = base,
                        badge = if (isTemp) "Tạm thời" else if (t.startsWith("system")) "Hệ thống" else "Người dùng"
                    )
                )
            }
        }
    }
    return items.distinctBy { it.subtitle }.sortedBy { it.title }
}

// 📶 Data Saver Whitelist/Blacklist
private suspend fun loadNetworkWhitelist(context: Context): List<SystemListItem> {
    val out = execShell("dumpsys netpolicy")
    val uidMap = loadUidToPackageMap()
    val items = mutableListOf<SystemListItem>()
    var section: String? = null
    out.lineSequence().forEach { line ->
        val t = line.trim()
        when {
            t.startsWith("restrictBackground whitelist:") -> section = "WHITELIST"
            t.startsWith("restrictBackground blacklist:") -> section = "BLACKLIST"
            t.isBlank() && section != null && !line.startsWith(" ") -> section = null
        }
        if (section != null && t.isNotEmpty() && !t.startsWith("restrictBackground")) {
            Regex("\\d{4,6}").findAll(t).forEach { m ->
                val uid = m.value.toIntOrNull() ?: return@forEach
                val pkg = uidMap[uid] ?: return@forEach
                items.add(
                    SystemListItem(
                        title = labelOf(context, pkg),
                        subtitle = pkg,
                        badge = if (section == "WHITELIST") "Whitelist" else "Blacklist",
                        badgeColor = if (section == "WHITELIST") Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                )
            }
        }
    }
    return items.distinctBy { it.subtitle + it.badge }.sortedBy { it.title }
}

// 🪫 Standby Buckets (truy vấn từng app, giới hạn song song 8)
private suspend fun loadStandbyBuckets(context: Context): List<SystemListItem> {
    val out = execShell("pm list packages -U")
    val pkgs = out.lineSequence()
        .mapNotNull { Regex("package:(\\S+)\\s+uid:(\\d+)").find(it)?.groupValues?.get(1) }
        .toList()

    val semaphore = Semaphore(8)
    val bucketColors = mapOf(
        "active" to Color(0xFF2E7D32),
        "working_set" to Color(0xFF1565C0),
        "frequent" to Color(0xFFF9A825),
        "rare" to Color(0xFFEF6C00),
        "restricted" to Color(0xFFC62828),
        "never" to Color(0xFF757575)
    )
    val bucketLabels = mapOf(
        "active" to "Đang dùng",
        "working_set" to "Hay dùng",
        "frequent" to "Thường xuyên",
        "rare" to "Ít dùng",
        "restricted" to "Hạn chế",
        "never" to "Chưa dùng"
    )

    return kotlinx.coroutines.coroutineScope {
        pkgs.map { pkg ->
            async {
                semaphore.withPermit {
                    try {
                        val b = execShell("am get-standby-bucket $pkg").trim().lowercase()
                        if (b.isBlank() || b == "unknown") return@withPermit null
                        SystemListItem(
                            title = labelOf(context, pkg),
                            subtitle = pkg,
                            badge = bucketLabels[b] ?: b,
                            badgeColor = bucketColors[b]
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
            .sortedWith { a, b ->
                val byBucket = bucketLabels.keys.indexOf(a.badge).compareTo(bucketLabels.keys.indexOf(b.badge))
                if (byBucket != 0) byBucket else a.title.compareTo(b.title)
            }
    }
}

// 🚫 AppOps nền bị chặn
private suspend fun loadBackgroundAppOps(context: Context): List<SystemListItem> {
    val out = execShell("dumpsys appops")
    val targetOps = setOf("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND", "START_FOREGROUND")
    val deniedModes = setOf("deny", "ignore")

    val items = mutableListOf<SystemListItem>()
    var currentPkg: String? = null
    val deniedOps = mutableListOf<String>()

    fun flush() {
        val pkg = currentPkg ?: return
        if (deniedOps.isNotEmpty()) {
            items.add(
                SystemListItem(
                    title = labelOf(context, pkg),
                    subtitle = pkg,
                    badge = deniedOps.joinToString(" + ").replace("_", " "),
                    badgeColor = Color(0xFFC62828)
                )
            )
        }
        deniedOps.clear()
    }

    out.lineSequence().forEach { line ->
        val pkgMatch = Regex("^\\s*Package (\\S+):").find(line)
        if (pkgMatch != null) {
            flush()
            currentPkg = pkgMatch.groupValues[1]
            return@forEach
        }
        val t = line.trim()
        val opMatch = Regex("^(RUN_IN_BACKGROUND|RUN_ANY_IN_BACKGROUND|START_FOREGROUND):\\s*mode=([a-z]+)").find(t)
        if (opMatch != null && opMatch.groupValues[1] in targetOps && opMatch.groupValues[2] in deniedModes) {
            deniedOps.add(opMatch.groupValues[1])
        }
    }
    flush()
    return items.sortedBy { it.title }
}

// 📊 Usage Stats granted
private suspend fun loadUsageStatsAccess(context: Context): List<SystemListItem> {
    val out = execShell("dumpsys usagestats")
    val uidMap = loadUidToPackageMap()
    val uids = mutableSetOf<Int>()
    var inGranted = false
    out.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (!inGranted) {
            if (trimmed.contains("granted:")) inGranted = true
        } else {
            when {
                trimmed.startsWith("granted") -> {}
                trimmed.isEmpty() || line.startsWith("  ") || trimmed.startsWith("android:uid") -> {
                    Regex("\\d{4,6}").findAll(line).forEach { m ->
                        m.value.toIntOrNull()?.let { uids.add(it) }
                    }
                }
                else -> inGranted = false
            }
        }
    }
    return uids.mapNotNull { uid ->
        val pkg = uidMap[uid] ?: return@mapNotNull null
        SystemListItem(
            title = labelOf(context, pkg),
            subtitle = pkg,
            badge = "UID $uid"
        )
    }.sortedBy { it.title }
}

// 🔔 Notification Listeners
private suspend fun loadNotificationListeners(context: Context): List<SystemListItem> {
    val out = execShell("dumpsys notification --noredact | grep -E \"mListeners|mNotificationListeners|activeNotificationListeners\"")
    val pkgs = mutableSetOf<String>()
    out.lineSequence().forEach { line ->
        Regex("ComponentInfo\\{([^/]+)/").findAll(line).forEach { m ->
            pkgs.add(m.groupValues[1])
        }
    }
    return pkgs.map { pkg ->
        SystemListItem(
            title = labelOf(context, pkg),
            subtitle = pkg,
            badge = "Listener"
        )
    }.sortedBy { it.title }
}

// ♿ Accessibility Services
private suspend fun loadAccessibilityServices(context: Context): List<SystemListItem> {
    val out = execShell("settings get secure enabled_accessibility_services")
    if (out.trim().isBlank() || out.trim().startsWith("null") || out.trim().startsWith("ERROR")) {
        return emptyList()
    }
    return out.split(':').mapNotNull { component ->
        val pkg = component.substringBefore('/').trim()
        if (pkg.isBlank()) null else SystemListItem(
            title = labelOf(context, pkg),
            subtitle = component.trim(),
            badge = "Bật"
        )
    }.sortedBy { it.title }
}

// 🏛️ Device Admins
private suspend fun loadDeviceAdmins(context: Context): List<SystemListItem> {
    val out = execShell("dumpsys device_policy")
    val items = mutableListOf<SystemListItem>()
    var inSection = false
    out.lineSequence().forEach { line ->
        val t = line.trim()
        when {
            t.contains("Enabled Device Admins", ignoreCase = true) ||
                t.contains("Active Device Admins", ignoreCase = true) -> inSection = true
            t.isBlank() || (line.isNotBlank() && line.startsWith("  ") && !line.startsWith("    ")) -> inSection = false
        }
        if (inSection) {
            val pkgMatch = Regex("^Package\\s+(\\S+?)\\s*:?$").find(t)
            val pkg = pkgMatch?.groupValues?.get(1)?.trimEnd(':')
                ?: Regex("ComponentInfo\\{([^/]+)/").find(t)?.groupValues?.get(1)
                ?: return@forEach
            items.add(
                SystemListItem(
                    title = labelOf(context, pkg),
                    subtitle = pkg,
                    badge = "Quản trị viên",
                    badgeColor = Color(0xFFE65100)
                )
            )
        }
    }
    return items.distinctBy { it.subtitle }.sortedBy { it.title }
}

// 😴 App Hibernation (Android 12+)
private suspend fun loadAppHibernation(context: Context): List<SystemListItem> {
    val probe = try {
        execShell("cmd hibernation get-application-hibernation-state 0 android").trim()
    } catch (e: Exception) {
        throw RuntimeException("Thiết bị không hỗ trợ App Hibernation (cần Android 12+): ${e.message}")
    }
    if (probe != "true" && probe != "false") {
        throw RuntimeException("Phản hồi lạ từ hệ thống: \"$probe\"")
    }

    val out = execShell("pm list packages -U")
    val pkgs = out.lineSequence()
        .mapNotNull { Regex("package:(\\S+)\\s+uid:(\\d+)").find(it)?.groupValues?.get(1) }
        .toList()

    val semaphore = Semaphore(8)
    return kotlinx.coroutines.coroutineScope {
        pkgs.map { pkg ->
            async {
                semaphore.withPermit {
                    try {
                        val h = execShell("cmd hibernation get-application-hibernation-state 0 $pkg").trim()
                        if (h != "true") return@withPermit null
                        SystemListItem(
                            title = labelOf(context, pkg),
                            subtitle = pkg,
                            badge = "Ngủ đông",
                            badgeColor = Color(0xFF6A1B9A)
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull().sortedBy { it.title }
    }
}
