package com.nobg.app.qs

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ScreenTimeoutTileService : TileService() {

    private val TIMEOUT_OPTIONS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Long-press = open Display settings
        try {
            val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000)
            val nextIdx = (TIMEOUT_OPTIONS.indexOfFirst { it >= current } + 1) % TIMEOUT_OPTIONS.size
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, TIMEOUT_OPTIONS[nextIdx])
            updateTile()
        } catch (e: SecurityException) {
            // No WRITE_SETTINGS: open system settings page
            startActivityAndCollapse(Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun updateTile() {
        val timeoutMs = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000)
        } catch (e: Exception) { 60_000 }

        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Thời gian sáng"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = formatTimeout(timeoutMs)
            }
            updateTile()
        }
    }

    private fun formatTimeout(ms: Int): String = when {
        ms < 60_000 -> "${ms / 1000}s"
        ms < 3600_000 -> "${ms / 60_000}m"
        else -> "${ms / 3600_000}h"
    }
}
