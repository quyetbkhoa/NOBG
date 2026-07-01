package com.nobg.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.BitmapPainter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.nobg.app.data.NobgMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val apps by viewModel.appList.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val showSystem by viewModel.showSystemApps.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NOBG - Quản lý app") },
                actions = {
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
                    .padding(12.dp),
                placeholder = { Text("Tìm app hoặc package...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem("Tất cả", filter == AppFilter.ALL) { viewModel.setFilter(AppFilter.ALL) }
                FilterChipItem("User", filter == AppFilter.USER) { viewModel.setFilter(AppFilter.USER) }
                FilterChipItem("Hệ thống", filter == AppFilter.SYSTEM) { viewModel.setFilter(AppFilter.SYSTEM) }
                FilterChipItem("Đang bật", filter == AppFilter.ENABLED_ONLY) { viewModel.setFilter(AppFilter.ENABLED_ONLY) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hiển thị app hệ thống", modifier = Modifier.weight(1f))
                Switch(checked = showSystem, onCheckedChange = viewModel::setShowSystemApps)
            }

            HorizontalDivider()

            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(app = app, viewModel = viewModel)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun AppRow(app: AppUiModel, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val config = app.config
    val enabled = config?.enabled == true

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawableIcon(app.icon, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (config != null && config.blockedCount > 0) {
                    Text(
                        "Đã chặn ${config.blockedCount} lần",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (config?.mode == NobgMode.DISABLE_ENABLE) {
                IconButton(onClick = { viewModel.launchDisabledApp(app.packageName) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Mở app")
                }
            }

            if (app.config != null) {
                IconButton(onClick = { viewModel.resetApp(app.packageName) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset về mặc định")
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    val mode = config?.mode ?: NobgMode.STANDARD
                    val delay = config?.delaySeconds ?: 30
                    viewModel.toggleNobg(app.packageName, checked, mode, delay)
                    if (checked) expanded = true
                }
            )
        }

        if (enabled) {
            ModeSelector(
                pkg = app.packageName,
                currentMode = config?.mode ?: NobgMode.STANDARD,
                delaySeconds = config?.delaySeconds ?: 30,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun ModeSelector(
    pkg: String,
    currentMode: NobgMode,
    delaySeconds: Int,
    viewModel: MainViewModel
) {
    Column(modifier = Modifier.padding(start = 64.dp, end = 12.dp, bottom = 8.dp)) {
        SingleChoiceSegmented(
            options = listOf(
                NobgMode.STANDARD to "Standard",
                NobgMode.AGGRESSIVE to "Aggressive",
                NobgMode.DISABLE_ENABLE to "Disable/Enable"
            ),
            selected = currentMode,
            onSelect = { viewModel.changeMode(pkg, it) }
        )

        if (currentMode == NobgMode.AGGRESSIVE) {
            var sliderValue by remember(pkg) { mutableStateOf(delaySeconds.toFloat()) }
            Text("Delay trước khi kill: ${sliderValue.toInt()} giây", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { viewModel.changeDelay(pkg, sliderValue.toInt()) },
                valueRange = 10f..1200f
            )
        }

        when (currentMode) {
            NobgMode.STANDARD -> Text(
                "Chặn background/thông báo/autostart. KHÔNG kill, app vẫn ở trong đa nhiệm.",
                style = MaterialTheme.typography.labelSmall
            )
            NobgMode.AGGRESSIVE -> Text(
                "Chặn background + kill hẳn tiến trình sau khoảng delay ở trên.",
                style = MaterialTheme.typography.labelSmall
            )
            NobgMode.DISABLE_ENABLE -> Text(
                "Vô hiệu hóa app khi rời đi. Bấm nút ▶ ở trên để mở lại app.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SingleChoiceSegmented(
    options: List<Pair<NobgMode, String>>,
    selected: NobgMode,
    onSelect: (NobgMode) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            val isSelected = mode == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(mode) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.padding(end = 6.dp)
            )
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
