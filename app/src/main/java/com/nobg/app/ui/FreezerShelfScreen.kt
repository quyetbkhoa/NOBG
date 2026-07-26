package com.nobg.app.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobg.app.data.AppEntity
import com.nobg.app.data.NobgRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ShelfAppUiModel(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isFrozen: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezerShelfScreen(
    repo: NobgRepository,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val shelfEntities by repo.frozenShelfApps.collectAsState(initial = emptyList())
    var shelfUiApps by remember { mutableStateOf<List<ShelfAppUiModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddAppDialog by remember { mutableStateOf(false) }

    fun refreshShelfList() {
        scope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val uiModels = shelfEntities.map { entity ->
                val isFrozen = checkIsPackageDisabled(pm, entity.packageName)
                val (appName, icon) = getAppInfo(pm, entity.packageName)
                ShelfAppUiModel(
                    packageName = entity.packageName,
                    appName = appName,
                    icon = icon,
                    isFrozen = isFrozen
                )
            }
            withContext(Dispatchers.Main) {
                shelfUiApps = uiModels
                isLoading = false
            }
        }
    }

    LaunchedEffect(shelfEntities) {
        refreshShelfList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🧊 Kệ Đóng Bằng Ứng Dụng (Icebox)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddAppDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Thêm ứng dụng")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddAppDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Thêm App Đóng Bằng") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // HEADER BANNER
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "🧊 TỔNG SỐ APP TRÊN KỆ: ${shelfUiApps.size}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            val frozenCount = shelfUiApps.count { it.isFrozen }
                            Text(
                                "Đang đóng băng: $frozenCount app · Giải phóng 100% RAM/CPU",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.AcUnit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    repo.freezeAllShelfApps()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "🧊 Đã ĐÓNG BẰNG tất cả app trên Kệ!", Toast.LENGTH_SHORT).show()
                                        refreshShelfList()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("🧊 Đóng băng tất cả", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    repo.unfreezeAllShelfApps()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "☀️ Đã XẢ ĐÓNG BẰNG tất cả app!", Toast.LENGTH_SHORT).show()
                                        refreshShelfList()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("☀️ Xả băng tất cả", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (shelfUiApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧊", fontSize = 56.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Kệ Đóng Bằng Chưa Có Ứng Dụng Nào",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ấn nút '+' để chọn app vào Kệ đóng băng. App bị đóng băng sẽ biến mất khỏi màn hình và không ngốn 1% pin nào.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showAddAppDialog = true }) {
                            Text("➕ Thêm App Vào Kệ Ngay")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shelfUiApps, key = { it.packageName }) { app ->
                        ShelfAppGridItem(
                            app = app,
                            onLaunch = {
                                scope.launch(Dispatchers.IO) {
                                    val success = repo.unfreezeAndLaunch(context, app.packageName)
                                    com.nobg.app.widget.FrozenAppsWidgetProvider.updateAllWidgets(context)
                                    withContext(Dispatchers.Main) {
                                        if (!success) {
                                            Toast.makeText(context, "Không thể mở ứng dụng!", Toast.LENGTH_SHORT).show()
                                        }
                                        refreshShelfList()
                                    }
                                }
                            },
                            onToggleFreeze = {
                                scope.launch(Dispatchers.IO) {
                                    if (app.isFrozen) {
                                        repo.unfreezePackage(app.packageName)
                                    } else {
                                        repo.freezePackage(app.packageName)
                                    }
                                    com.nobg.app.widget.FrozenAppsWidgetProvider.updateAllWidgets(context)
                                    withContext(Dispatchers.Main) {
                                        refreshShelfList()
                                    }
                                }
                            },
                            onRemoveFromShelf = {
                                scope.launch(Dispatchers.IO) {
                                    repo.toggleAppFrozenShelf(app.packageName, false)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Đã xóa khỏi Kệ đóng băng", Toast.LENGTH_SHORT).show()
                                        refreshShelfList()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddAppDialog) {
        AddShelfAppDialog(
            context = context,
            currentShelfPkgs = shelfUiApps.map { it.packageName }.toSet(),
            onDismiss = { showAddAppDialog = false },
            onConfirm = { selectedPkgs ->
                showAddAppDialog = false
                scope.launch(Dispatchers.IO) {
                    for (pkg in selectedPkgs) {
                        repo.toggleAppFrozenShelf(pkg, true)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Đã thêm ${selectedPkgs.size} app vào Kệ đóng băng!", Toast.LENGTH_SHORT).show()
                        refreshShelfList()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfAppGridItem(
    app: ShelfAppUiModel,
    onLaunch: () -> Unit,
    onToggleFreeze: () -> Unit,
    onRemoveFromShelf: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onLaunch() },
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isFrozen) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onRemoveFromShelf,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Xóa khỏi Kệ",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (app.icon != null) {
                        val bitmap = remember(app.icon) { drawableToBitmap(app.icon) }
                        Image(
                            painter = BitmapPainter(bitmap.asImageBitmap()),
                            contentDescription = app.appName,
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(app.appName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (app.isFrozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (app.isFrozen) Icons.Default.AcUnit else Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    app.appName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(2.dp))

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (app.isFrozen) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                ) {
                    Text(
                        if (app.isFrozen) "🧊 Đóng băng" else "☀️ Đã mở",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (app.isFrozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("🚀 Mở ứng dụng (Xả băng)") },
                    onClick = {
                        showMenu = false
                        onLaunch()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (app.isFrozen) "☀️ Xả đóng băng" else "🧊 Đóng băng ngay") },
                    onClick = {
                        showMenu = false
                        onToggleFreeze()
                    }
                )
                DropdownMenuItem(
                    text = { Text("❌ Xóa khỏi Kệ", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onRemoveFromShelf()
                    }
                )
            }
        }
    }
}

@Composable
fun AddShelfAppDialog(
    context: Context,
    currentShelfPkgs: Set<String>,
    onlyUserApps: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<AppUiModelSelect>>(emptyList()) }
    var selectedPkgs by remember { mutableStateOf(currentShelfPkgs) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val list = packages.mapNotNull { pkgInfo ->
                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (onlyUserApps && isSystem) return@mapNotNull null

                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                AppUiModelSelect(
                    packageName = pkgInfo.packageName,
                    appName = label,
                    icon = icon,
                    isSystem = isSystem
                )
            }.sortedBy { it.appName.lowercase() }

            withContext(Dispatchers.Main) {
                installedApps = list
                isLoading = false
            }
        }
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("➕ Chọn App Vào Kệ Đóng Bằng", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.height(400.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tìm tên app...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filteredApps, key = { it.packageName }) { item ->
                            val isChecked = selectedPkgs.contains(item.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPkgs = if (isChecked) selectedPkgs - item.packageName else selectedPkgs + item.packageName
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedPkgs = if (checked) selectedPkgs + item.packageName else selectedPkgs - item.packageName
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                val bitmap = remember(item.icon) { drawableToBitmap(item.icon) }
                                Image(
                                    painter = BitmapPainter(bitmap.asImageBitmap()),
                                    contentDescription = item.appName,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.appName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedPkgs - currentShelfPkgs) }
            ) {
                Text("Lưu Kệ Đóng Bằng")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

data class AppUiModelSelect(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val isSystem: Boolean
)

private fun checkIsPackageDisabled(pm: PackageManager, packageName: String): Boolean {
    return try {
        val appInfo = pm.getApplicationInfo(packageName, 0)
        !appInfo.enabled
    } catch (_: Exception) {
        true
    }
}

private fun getAppInfo(pm: PackageManager, packageName: String): Pair<String, Drawable?> {
    return try {
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val name = pm.getApplicationLabel(appInfo).toString()
        val icon = pm.getApplicationIcon(appInfo)
        name to icon
    } catch (_: Exception) {
        packageName to null
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    } else {
        Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    }
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
