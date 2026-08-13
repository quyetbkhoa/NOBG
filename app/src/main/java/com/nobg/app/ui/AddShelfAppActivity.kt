package com.nobg.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.nobg.app.data.NobgRepository
import com.nobg.app.service.MonitorService
import com.nobg.app.ui.theme.NobgTheme
import com.nobg.app.widget.FrozenAppsWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddShelfAppActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = NobgRepository(applicationContext)
        ContextCompat.startForegroundService(this, android.content.Intent(this, MonitorService::class.java))

        setContent {
            val themeMode = repo.getThemeMode()
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            NobgTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val scope = rememberCoroutineScope()
                    var currentShelfPkgs by remember { mutableStateOf<Set<String>>(emptySet()) }
                    var isRepoLoading by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        val existing = repo.getFrozenShelfApps().map { it.packageName }.toSet()
                        withContext(Dispatchers.Main) {
                            currentShelfPkgs = existing
                            isRepoLoading = false
                        }
                    }
                }

                    if (isRepoLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AddShelfAppDialog(
                        context = this@AddShelfAppActivity,
                        currentShelfPkgs = currentShelfPkgs,
                        onlyUserApps = true,
                        onDismiss = { finish() },
                        onConfirm = { addedPkgs ->
                            scope.launch(Dispatchers.IO) {
                                var addedCount = 0
                                for (pkg in addedPkgs) {
                                    if (repo.toggleAppFrozenShelf(pkg, true)) addedCount++
                                }
                                FrozenAppsWidgetProvider.updateAllWidgets(applicationContext)
                                withContext(Dispatchers.Main) {
                                    val message = when {
                                        addedPkgs.isEmpty() -> "Không có ứng dụng mới được chọn"
                                        addedCount == addedPkgs.size -> "🧊 Đã thêm $addedCount ứng dụng vào Kệ đóng băng!"
                                        addedCount == 0 -> "Không thể đóng băng app. Hãy kiểm tra Shizuku/ADB."
                                        else -> "Đã thêm $addedCount/${addedPkgs.size} app; một số app không thể đóng băng."
                                    }
                                    Toast.makeText(this@AddShelfAppActivity, message, Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            }
                        }
                        )
                    }
                }
            }
        }
    }
}
