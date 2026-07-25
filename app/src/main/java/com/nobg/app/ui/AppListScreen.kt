package com.nobg.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobg.app.data.NobgMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenBatteryStats: () -> Unit,
    onOpenAdvancedTweaks: () -> Unit,
    onOpenFreezerShelf: () -> Unit
) {
    val apps by viewModel.appList.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val shizukuReady by viewModel.shizukuReady.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val activeFilterCount by viewModel.activeFilterCount.collectAsState()

    val userSystemFilters by viewModel.userSystemFilters.collectAsState()
    val disabledFilters by viewModel.disabledFilters.collectAsState()
    val powerStateFilters by viewModel.powerStateFilters.collectAsState()
    val nobgStateFilters by viewModel.nobgStateFilters.collectAsState()
    val hiddenFilter by viewModel.hiddenFilter.collectAsState()

    var showShizukuWarning by remember { mutableStateOf(false) }
    var selectedAppForDialog by remember { mutableStateOf<AppUiModel?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var autoUpdateInfo by remember { mutableStateOf<com.nobg.app.update.UpdateInfo?>(null) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshShizukuStatus()
        val result = com.nobg.app.update.GitHubUpdater.checkForUpdates(context)
        if (result is com.nobg.app.update.UpdateResult.UpdateAvailable) {
            autoUpdateInfo = result.info
        }
    }

    if (autoUpdateInfo != null) {
        com.nobg.app.update.AutoUpdateDialog(
            updateInfo = autoUpdateInfo!!,
            context = context,
            onDismiss = { autoUpdateInfo = null }
        )
    }

    if (showShizukuWarning) {
        AlertDialog(
            onDismissRequest = { showShizukuWarning = false },
            title = { Text("Thiếu quyền Shizuku") },
            text = { Text("Ứng dụng chưa được cấp quyền Shizuku hoặc Shizuku chưa chạy.\n\nBạn vẫn có thể xem trạng thái và mở Cài đặt pin hệ thống để chỉnh tay. Mở Shizuku để chỉnh trực tiếp trong app.") },
            confirmButton = {
                TextButton(onClick = { showShizukuWarning = false }) { Text("Đóng") }
            }
        )
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            viewModel = viewModel,
            onDismiss = { showFilterSheet = false }
        )
    }

    if (selectedAppForDialog != null) {
        val currentSelected = apps.find { it.packageName == selectedAppForDialog!!.packageName } ?: selectedAppForDialog!!
        AppManagementDialog(
            appModel = currentSelected,
            viewModel = viewModel,
            onDismiss = { selectedAppForDialog = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NOBG - Quản lý app") },
                actions = {
                    IconButton(onClick = onOpenFreezerShelf) {
                        Text("🧊", fontSize = 18.sp)
                    }
                    IconButton(onClick = onOpenAdvancedTweaks) {
                        Text("🛠️", fontSize = 18.sp)
                    }
                    IconButton(onClick = onOpenBatteryStats) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Thống kê Pin & App")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Cài đặt")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("Tìm app hoặc package...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )

            // COMPACT ACTIVE FILTER BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main Filter Sheet Button
                FilterChip(
                    selected = activeFilterCount > 0,
                    onClick = { showFilterSheet = true },
                    label = {
                        Text(if (activeFilterCount > 0) "🔍 Bộ lọc ($activeFilterCount)" else "🔍 Bộ lọc")
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )

                // Quick Filter: App chưa tối ưu bao giờ
                val isNeverConfiguredSelected = NobgStateFilterOption.NEVER_CONFIGURED in nobgStateFilters
                FilterChip(
                    selected = isNeverConfiguredSelected,
                    onClick = {
                        val current = viewModel.nobgStateFilters.value
                        viewModel.nobgStateFilters.value = if (isNeverConfiguredSelected) {
                            current - NobgStateFilterOption.NEVER_CONFIGURED
                        } else {
                            current + NobgStateFilterOption.NEVER_CONFIGURED
                        }
                    },
                    label = { Text("✨ Chưa tối ưu bao giờ") }
                )

                // Render ONLY ACTIVE Filters
                userSystemFilters.forEach { opt ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.userSystemFilters.value = userSystemFilters - opt },
                        label = { Text(opt.label) },
                        trailingIcon = { Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                disabledFilters.forEach { opt ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.disabledFilters.value = disabledFilters - opt },
                        label = { Text(opt.label) },
                        trailingIcon = { Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                powerStateFilters.forEach { opt ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.powerStateFilters.value = powerStateFilters - opt },
                        label = { Text(opt.label) },
                        trailingIcon = { Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                nobgStateFilters.forEach { opt ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.nobgStateFilters.value = nobgStateFilters - opt },
                        label = { Text(opt.label) },
                        trailingIcon = { Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                if (hiddenFilter != HiddenFilterOption.EXCLUDE_HIDDEN) {
                    InputChip(
                        selected = true,
                        onClick = { viewModel.hiddenFilter.value = HiddenFilterOption.EXCLUDE_HIDDEN },
                        label = { Text(hiddenFilter.label) },
                        trailingIcon = { Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 2.dp))

            // Pull to Refresh Box
            val pullToRefreshState = rememberPullToRefreshState()
            if (pullToRefreshState.isRefreshing) {
                LaunchedEffect(Unit) {
                    viewModel.reloadAllData()
                    pullToRefreshState.endRefresh()
                }
            }
            LaunchedEffect(isRefreshing) {
                if (!isRefreshing) {
                    pullToRefreshState.endRefresh()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullToRefreshState.nestedScrollConnection)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(apps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            onOpenDialog = { selectedAppForDialog = app }
                        )
                        HorizontalDivider()
                    }
                }

                if (pullToRefreshState.isRefreshing) {
                    PullToRefreshContainer(
                        state = pullToRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppUiModel,
    onOpenDialog: () -> Unit
) {
    val config = app.config
    val enabled = config?.enabled == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDialog)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawableIcon(app.icon, modifier = Modifier.size(46.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            // State Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PowerBadge(state = app.powerState)
                NobgBadge(
                    enabled = enabled,
                    mode = config?.mode ?: NobgMode.STANDARD,
                    delaySeconds = config?.delaySeconds ?: 30
                )
                if (app.isDisabled) {
                    DisabledBadge()
                }
            }
            if (config != null && config.blockedCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Đã chặn ${config.blockedCount} lần",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DrawableIcon(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable == null) {
        Box(modifier = modifier.clip(RoundedCornerShape(8.dp)))
        return
    }
    val bitmap = remember(drawable) {
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp
    }
    Image(
        painter = BitmapPainter(bitmap.asImageBitmap()),
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(8.dp))
    )
}
