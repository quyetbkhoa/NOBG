package com.nobg.app.qs

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * A QS tile that launches a configurable app activity (shortcut).
 * Target is stored in SharedPreferences: nobg_prefs / qs_shortcut_package & qs_shortcut_activity
 */
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
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } ?: return

        try {
            startActivityAndCollapse(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
