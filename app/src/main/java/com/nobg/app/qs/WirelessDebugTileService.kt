package com.nobg.app.qs

import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nobg.app.R

class WirelessDebugTileService : TileService() {
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor("adb_wifi_enabled"),
            false,
            observer
        )
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        contentResolver.unregisterContentObserver(observer)
    }

    override fun onClick() {
        super.onClick()
        try {
            val current = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0)
            val newState = if (current == 1) 0 else 1
            Settings.Global.putInt(contentResolver, "adb_wifi_enabled", newState)
            updateTileState(newState == 1)
        } catch (e: SecurityException) { /* No WRITE_SECURE_SETTINGS */ }
    }

    private fun updateTile() {
        val enabled = try {
            Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (e: Exception) { false }
        updateTileState(enabled)
    }

    private fun updateTileState(enabled: Boolean) {
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "ADB K.Dây"
            icon = Icon.createWithResource(this@WirelessDebugTileService, R.drawable.ic_qs_wifi)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (enabled) "Bật" else "Tắt"
            }
            updateTile()
        }
    }
}

