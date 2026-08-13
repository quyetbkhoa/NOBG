package com.nobg.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.widget.RemoteViews
import com.nobg.app.MainActivity
import com.nobg.app.R
import com.nobg.app.ui.UnfreezeAndLaunchActivity

class FrozenAppsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FrozenAppsWidgetProvider::class.java))
            ids.forEach { updateAppWidget(context, manager, it) }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.nobg.app.action.REFRESH_WIDGET"

        fun updateAllWidgets(context: Context) {
            context.sendBroadcast(
                Intent(context, FrozenAppsWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                }
            )
        }

        fun updateAppWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_frozen_apps)
                val config = WidgetConfigManager.getConfig(context)

                val alpha = ((config.opacityPct / 100f) * 255).toInt().coerceIn(0, 255)
                val backgroundColor = if (config.theme == "DARK") {
                    android.graphics.Color.argb(alpha, 15, 23, 42)
                } else {
                    android.graphics.Color.argb(alpha, 255, 255, 255)
                }
                views.setImageViewBitmap(
                    R.id.widget_background,
                    createRoundedBackground(backgroundColor, config.theme == "DARK")
                )
                views.setInt(R.id.widget_grid_view, "setNumColumns", config.numColumns)

                // Vùng trống của widget vẫn mở trực tiếp Kệ Đóng Băng.
                val openShelfIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("open_screen", "FREEZER_SHELF")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                views.setOnClickPendingIntent(
                    R.id.widget_container,
                    PendingIntent.getActivity(
                        context,
                        appWidgetId + 1000,
                        openShelfIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                val serviceIntent = Intent(context, FreezerWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.widget_grid_view, serviceIntent)

                val itemIntent = Intent(context, UnfreezeAndLaunchActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                views.setPendingIntentTemplate(
                    R.id.widget_grid_view,
                    PendingIntent.getActivity(
                        context,
                        appWidgetId,
                        itemIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                )

                manager.updateAppWidget(appWidgetId, views)
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_grid_view)
            } catch (error: Exception) {
                android.util.Log.e("FrozenWidget", "Widget update failed for id $appWidgetId", error)
            }
        }

        private fun createRoundedBackground(color: Int, isDark: Boolean): Bitmap {
            val size = 128
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val rect = RectF(1f, 1f, size - 1f, size - 1f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = color
            }
            canvas.drawRoundRect(rect, 22f, 22f, paint)
            paint.apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                this.color = if (isDark) {
                    android.graphics.Color.argb(90, 148, 163, 184)
                } else {
                    android.graphics.Color.argb(100, 255, 255, 255)
                }
            }
            canvas.drawRoundRect(rect, 22f, 22f, paint)
            return bitmap
        }
    }
}
