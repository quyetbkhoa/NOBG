package com.nobg.app.qs

import android.app.PendingIntent
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ScreenTimeoutTileService : TileService() {

    private val TIMEOUT_OPTIONS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT),
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
            val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000)
            val nextIdx = (TIMEOUT_OPTIONS.indexOfFirst { it >= current } + 1) % TIMEOUT_OPTIONS.size
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, TIMEOUT_OPTIONS[nextIdx])
        } catch (e: SecurityException) {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTile() {
        val timeoutMs = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000)
        } catch (e: Exception) { 60_000 }

        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Thời gian sáng"
            icon = createTextIcon(formatTimeout(timeoutMs))
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

    private fun createTextIcon(text: String): Icon {
        val size = 100
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val xPos = (canvas.width / 2).toFloat()
        val yPos = (canvas.height / 2 - (paint.descent() + paint.ascent()) / 2)
        canvas.drawText(text, xPos, yPos, paint)
        return Icon.createWithBitmap(bitmap)
    }
}

