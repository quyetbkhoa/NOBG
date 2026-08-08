package com.nobg.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nobg.app.data.NobgRepository
import com.nobg.app.ui.theme.NobgTheme
import com.nobg.app.widget.FrozenAppsWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddShelfAppActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = NobgRepository(applicationContext)

        setContent {
            val themeMode = repo.getThemeMode()
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            NobgTheme(darkTheme = darkTheme) {
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
                                val addedCount = addedPkgs.size
                                for (pkg in addedPkgs) {
                                    repo.toggleAppFrozenShelf(pkg, true)
                                }
                                FrozenAppsWidgetProvider.updateAllWidgets(applicationContext)
                                withContext(Dispatchers.Main) {
                                    if (addedCount > 0) {
                                        Toast.makeText(
                                            this@AddShelfAppActivity,
                                            "🧊 Đã thêm $addedCount ứng dụng vào Kệ đóng bằng!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
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
