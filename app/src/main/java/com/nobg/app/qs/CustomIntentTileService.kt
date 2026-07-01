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

class CustomIntentTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val prefs = getSharedPreferences("nobg_prefs", MODE_PRIVATE)
        val action = prefs.getString("qs_intent_action", null)
        val dataUri = prefs.getString("qs_intent_data", null)
        val pkg = prefs.getString("qs_intent_package", null)
        val cls = prefs.getString("qs_intent_class", null)
        
        qsTile?.apply {
            state = if (!action.isNullOrBlank() || !dataUri.isNullOrBlank() || !cls.isNullOrBlank()) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
            label = prefs.getString("qs_intent_label", "Intent") ?: "Intent"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (!action.isNullOrBlank() || !dataUri.isNullOrBlank() || !cls.isNullOrBlank()) "Nhấn để chạy" else "Chưa cấu hình"
            }
            if (pkg != null) {
                getAppIconAsQsIcon(this@CustomIntentTileService, pkg)?.let { icon = it }
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("nobg_prefs", MODE_PRIVATE)
        val action = prefs.getString("qs_intent_action", null)
        val dataUri = prefs.getString("qs_intent_data", null)
        val pkg = prefs.getString("qs_intent_package", null)
        val cls = prefs.getString("qs_intent_class", null)
        val type = prefs.getString("qs_intent_type", "activity") // activity, service, broadcast

        if (action.isNullOrBlank() && dataUri.isNullOrBlank() && cls.isNullOrBlank()) return

        val intent = Intent().apply {
            if (!action.isNullOrBlank()) this.action = action.trim()
            if (!dataUri.isNullOrBlank()) this.data = android.net.Uri.parse(dataUri.trim())
            if (!pkg.isNullOrBlank() && !cls.isNullOrBlank()) {
                setClassName(pkg, cls)
            } else if (!pkg.isNullOrBlank()) {
                setPackage(pkg)
            }
            if (type == "activity") {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        try {
            when (type) {
                "service" -> startService(intent)
                "broadcast" -> sendBroadcast(intent)
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        startActivityAndCollapse(pi)
                    } else {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(intent)
                    }
                }
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
