package com.nobg.app.qs

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class AppShortcutTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val prefs = getSharedPreferences("nobg_prefs", MODE_PRIVATE)
        val pkg = prefs.getString("qs_shortcut_package", null)
        qsTile?.apply {
            state = if (pkg != null) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
            label = if (pkg != null) prefs.getString("qs_shortcut_label", "Shortcut") ?: "Shortcut" else "Shortcut (chưa cấu hình)"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (pkg != null) "Nhấn để mở" else "Vào NOBG để cấu hình"
            }
            if (pkg != null) {
                getAppIconAsQsIcon(this@AppShortcutTileService, pkg)?.let { icon = it }
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("nobg_prefs", MODE_PRIVATE)
        val pkg = prefs.getString("qs_shortcut_package", null) ?: return
        val activity = prefs.getString("qs_shortcut_activity", null)

        val intent = if (activity != null) {
            Intent().apply {
                setClassName(pkg, activity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else {
            packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAppIconAsQsIcon(context: Context, packageName: String): Icon? {
        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val size = 144
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            Icon.createWithBitmap(bitmap)
        } catch (e: Exception) {
            null
        }
    }
}

