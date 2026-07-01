package com.nobg.app.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobg.app.qs.*
import com.nobg.app.shizuku.ShizukuManager
import kotlinx.coroutines.launch

// Simple data holders
data class AppInfo(val label: String, val packageName: String)
data class ActivityInfo(val label: String, val className: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizukuReady = remember {
        ShizukuManager.isShizukuRunning() && ShizukuManager.hasPermission() && ShizukuManager.isServiceBound()
    }

    var hasWriteSecure by remember {
        mutableStateOf(
            context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val hasWriteSettings = remember { Settings.System.canWrite(context) }

    var showAdbDialog by remember { mutableStateOf<String?>(null) }
    var showShortcutConfig by remember { mutableStateOf(false) }
    var shizukuGranting by remember { mutableStateOf(false) }
    var shizukuGrantMsg by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chỉnh sửa Quick Setting") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val statusColor = if (hasWriteSecure) MaterialTheme.colorScheme.primaryContainer
                                  else MaterialTheme.colorScheme.errorContainer
                Card(
                    colors = CardDefaults.cardColors(containerColor = statusColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (hasWriteSecure) "✅ Đã có quyền WRITE_SECURE_SETTINGS"
                            else "⚠️ Chưa có quyền WRITE_SECURE_SETTINGS",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!hasWriteSecure) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Cần quyền này để bật/tắt USB Debug, ADB Không dây, Tuỳ chọn Nhà phát triển.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            if (shizukuReady) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            shizukuGranting = true
                                            val result = ShizukuManager.exec(
                                                "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
                                            )
                                            shizukuGranting = false
                                            if (result.isBlank() || result.startsWith("ERROR").not()) {
                                                hasWriteSecure = context.checkCallingOrSelfPermission(
                                                    "android.permission.WRITE_SECURE_SETTINGS"
                                                ) == PackageManager.PERMISSION_GRANTED
                                                shizukuGrantMsg = if (hasWriteSecure) "✅ Cấp quyền thành công!" else "Kết quả: $result"
                                            } else {
                                                shizukuGrantMsg = "❌ Lỗi: $result"
                                            }
                                        }
                                    },
                                    enabled = !shizukuGranting
                                ) {
                                    if (shizukuGranting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("Cấp quyền qua Shizuku")
                                }
                                shizukuGrantMsg?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(it, style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                OutlinedButton(onClick = {
                                    showAdbDialog = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
                                }) { Text("Hiện lệnh ADB để cấp quyền") }
                            }
                        }
                    }
                }
            }

            item { Text("Các nút Quick Setting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

            item {
                QsTileCard(
                    title = "🔌 USB Debug",
                    description = "Nút bật/tắt USB Debugging trong vùng thông báo nhanh.",
                    requiresSecure = true, hasSecure = hasWriteSecure, shizukuReady = shizukuReady,
                    onAdd = { addTileToQs(context, UsbDebugTileService::class.java) },
                    onShowAdb = { showAdbDialog = buildAdbGrantSecure(context.packageName) }
                )
            }
            item {
                QsTileCard(
                    title = "📡 ADB Không dây",
                    description = "Nút bật/tắt gỡ lỗi không dây (Wireless ADB) trong QS.",
                    requiresSecure = true, hasSecure = hasWriteSecure, shizukuReady = shizukuReady,
                    onAdd = { addTileToQs(context, WirelessDebugTileService::class.java) },
                    onShowAdb = { showAdbDialog = buildAdbGrantSecure(context.packageName) }
                )
            }
            item {
                QsTileCard(
                    title = "🛠️ Tuỳ chọn nhà phát triển",
                    description = "Nút bật/tắt Developer Options trong QS.",
                    requiresSecure = true, hasSecure = hasWriteSecure, shizukuReady = shizukuReady,
                    onAdd = { addTileToQs(context, DevOptionsTileService::class.java) },
                    onShowAdb = { showAdbDialog = buildAdbGrantSecure(context.packageName) }
                )
            }
            item {
                QsTileCard(
                    title = "⏱️ Thời gian sáng màn hình",
                    description = "Bấm để chuyển vòng: 15s → 30s → 1m → 2m → 5m → 10m.",
                    requiresSecure = false, hasSecure = hasWriteSettings, shizukuReady = shizukuReady,
                    onAdd = { addTileToQs(context, ScreenTimeoutTileService::class.java) },
                    onShowAdb = { showAdbDialog = "adb shell appops set ${context.packageName} android:write_settings allow" }
                )
            }
            item {
                // Shortcut tile card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🚀 Shortcut mở App", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        val prefs = context.getSharedPreferences("nobg_prefs", Context.MODE_PRIVATE)
                        val configuredPkg = prefs.getString("qs_shortcut_package", null)
                        val configuredLabel = prefs.getString("qs_shortcut_label", null)
                        if (configuredPkg != null) {
                            Text(
                                "Đã cấu hình: ${configuredLabel ?: configuredPkg}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text("Chưa cấu hình. Chọn app để mở nhanh qua QS.", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showShortcutConfig = true },
                                modifier = Modifier.weight(1f)
                            ) { Text("Chọn App") }
                            Button(
                                onClick = { addTileToQs(context, AppShortcutTileService::class.java) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Thêm vào QS") }
                        }
                    }
                }
            }
        }
    }

    // ADB command dialog
    showAdbDialog?.let { cmd ->
        AlertDialog(
            onDismissRequest = { showAdbDialog = null },
            title = { Text("Lệnh ADB cần chạy") },
            text = {
                Column {
                    Text("Kết nối ADB rồi chạy lệnh sau:", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            cmd,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAdbDialog = null }) { Text("Đã hiểu") } }
        )
    }

    // Shortcut config - app picker
    if (showShortcutConfig) {
        AppPickerSheet(context = context, onDismiss = { showShortcutConfig = false })
    }
}

// ========== App Picker ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(context: Context, onDismiss: () -> Unit) {
    val prefs = context.getSharedPreferences("nobg_prefs", Context.MODE_PRIVATE)

    // Load app list
    val allApps = remember {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = context.packageManager.queryIntentActivities(intent, 0)
        apps.sortedBy { it.loadLabel(context.packageManager).toString() }
            .map { AppInfo(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
    }

    var step by remember { mutableStateOf(0) } // 0=pick mode/app, 1=pick activity, 2=set label, 3=custom intent
    var isCustomMode by remember { mutableStateOf(false) }
    var customAction by remember { mutableStateOf(prefs.getString("qs_shortcut_action", "") ?: "") }
    var customData by remember { mutableStateOf(prefs.getString("qs_shortcut_data", "") ?: "") }
    
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var selectedActivity by remember { mutableStateOf<ActivityInfo?>(null) }
    var labelText by remember { mutableStateOf(prefs.getString("qs_shortcut_label", "") ?: "") }
    var appActivities by remember { mutableStateOf<List<ActivityInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    fun loadActivities(pkg: String) {
        val intent = Intent()
        intent.setPackage(pkg)
        val resolved = context.packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        val mainActivity = context.packageManager.getLaunchIntentForPackage(pkg)?.component?.className
        val list = mutableListOf<ActivityInfo>()
        if (mainActivity != null) {
            list.add(ActivityInfo("▶ Màn hình chính", ""))
        }
        resolved.sortedBy { it.loadLabel(context.packageManager).toString() }
            .forEach {
                val name = it.activityInfo.name
                if (name != mainActivity) {
                    list.add(ActivityInfo(it.loadLabel(context.packageManager).toString().ifBlank { name.substringAfterLast('.') }, name))
                }
            }
        appActivities = list
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Text(
                    text = when (step) {
                        0 -> "Chọn chức năng phím tắt"
                        1 -> "Chọn màn hình / Activity"
                        2 -> "Tên hiển thị trên QS"
                        else -> "Cấu hình Custom Intent"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                HorizontalDivider()

                when (step) {
                    // Step 0: Pick App or Custom Intent
                    0 -> {
                        Button(
                            onClick = { step = 3; isCustomMode = true; labelText = prefs.getString("qs_shortcut_label", "") ?: "Custom Intent" },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        ) { Text("Chạy Custom Intent (Advanced)") }
                        Text("Hoặc chọn ứng dụng:", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Tìm app...") },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true
                        )
                        val filtered = allApps.filter {
                            it.label.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                        }
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filtered, key = { it.packageName }) { app ->
                                ListItem(
                                    headlineContent = { Text(app.label, fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.clickable {
                                        selectedApp = app
                                        labelText = app.label
                                        searchQuery = ""
                                        isCustomMode = false
                                        loadActivities(app.packageName)
                                        step = 1
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }

                    // Step 1: Pick Activity
                    1 -> {
                        Text(
                            "App: ${selectedApp?.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(appActivities, key = { it.className }) { act ->
                                ListItem(
                                    headlineContent = { Text(act.label, fontWeight = FontWeight.Medium) },
                                    supportingContent = if (act.className.isNotEmpty()) {
                                        { Text(act.className.substringAfterLast('.'), style = MaterialTheme.typography.labelSmall) }
                                    } else null,
                                    modifier = Modifier.clickable {
                                        selectedActivity = act
                                        step = 2
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }

                    // Step 2: Confirm label
                    2 -> {
                        Column(modifier = Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "App: ${selectedApp?.label}\nMàn hình: ${selectedActivity?.label?.ifEmpty { "Màn hình chính" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = labelText,
                                onValueChange = { labelText = it },
                                label = { Text("Tên hiển thị trên tile QS") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    // Step 3: Custom intent config
                    3 -> {
                        Column(modifier = Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Cấu hình chạy Intent bằng mã (dành cho người dùng nâng cao).", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = customAction,
                                onValueChange = { customAction = it },
                                label = { Text("Action (vd: android.settings.SETTINGS)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = customData,
                                onValueChange = { customData = it },
                                label = { Text("Data URI (vd: https://google.com)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = labelText,
                                onValueChange = { labelText = it },
                                label = { Text("Tên hiển thị trên tile QS") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                HorizontalDivider()
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = {
                        if (step == 3) step = 0
                        else if (step > 0) step-- 
                        else onDismiss()
                    }) { Text(if (step > 0) "Quay lại" else "Hủy") }

                    if (step == 2 || step == 3) {
                        Button(onClick = {
                            if (step == 3) {
                                prefs.edit()
                                    .putBoolean("qs_shortcut_is_custom", true)
                                    .putString("qs_shortcut_action", customAction.trim())
                                    .putString("qs_shortcut_data", customData.trim())
                                    .putString("qs_shortcut_label", labelText.trim().ifEmpty { "Custom Intent" })
                                    .apply()
                            } else {
                                val pkg = selectedApp?.packageName ?: return@Button
                                val actClass = selectedActivity?.className?.takeIf { it.isNotEmpty() }
                                prefs.edit()
                                    .putBoolean("qs_shortcut_is_custom", false)
                                    .putString("qs_shortcut_package", pkg)
                                    .putString("qs_shortcut_activity", actClass)
                                    .putString("qs_shortcut_label", labelText.trim().ifEmpty { selectedApp?.label ?: "Shortcut" })
                                    .apply()
                            }
                            onDismiss()
                        }) { Text("Lưu") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QsTileCard(
    title: String, description: String,
    requiresSecure: Boolean, hasSecure: Boolean, shizukuReady: Boolean,
    onAdd: () -> Unit, onShowAdb: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            if (requiresSecure && !hasSecure) {
                Text(
                    "⚠️ Cần quyền WRITE_SECURE_SETTINGS để hoạt động.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (requiresSecure && !hasSecure) {
                    OutlinedButton(onClick = onShowAdb, modifier = Modifier.weight(1f)) {
                        Text("Xem lệnh ADB", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Text("Thêm vào QS", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun addTileToQs(context: Context, serviceClass: Class<*>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val sbm = context.getSystemService(StatusBarManager::class.java)
        sbm.requestAddTileService(
            ComponentName(context, serviceClass),
            "NOBG Tile",
            android.graphics.drawable.Icon.createWithResource(context, android.R.drawable.ic_menu_info_details),
            { cmd -> cmd.run() }, {}
        )
    } else {
        try {
            context.startActivity(Intent("android.settings.ACTION_QS_SETTINGS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }
}

private fun buildAdbGrantSecure(pkg: String) =
    "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
