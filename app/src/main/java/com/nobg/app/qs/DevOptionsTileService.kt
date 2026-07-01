package com.nobg.app.qs

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class DevOptionsTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        try {
            val current = Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
            Settings.Global.putInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, if (current == 1) 0 else 1)
            updateTile()
        } catch (e: SecurityException) { /* No WRITE_SECURE_SETTINGS */ }
    }

    private fun updateTile() {
        val enabled = try {
            Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (e: Exception) { false }

        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Nhà phát triển"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (enabled) "Bật" else "Tắt"
            }
            updateTile()
        }
    }
}
