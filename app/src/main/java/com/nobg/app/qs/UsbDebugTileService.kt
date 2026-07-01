package com.nobg.app.qs

import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nobg.app.R

class UsbDebugTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        try {
            val current = Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0)
            Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, if (current == 1) 0 else 1)
            updateTile()
        } catch (e: SecurityException) {
            // No WRITE_SECURE_SETTINGS - ignore
        }
    }

    private fun updateTile() {
        val enabled = try {
            Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) { false }

        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "USB Debug"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (enabled) "Bật" else "Tắt"
            }
            updateTile()
        }
    }
}
