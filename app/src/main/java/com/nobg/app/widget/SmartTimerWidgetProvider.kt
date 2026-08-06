package com.nobg.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.nobg.app.R
import com.nobg.app.data.NobgRepository
import com.nobg.app.service.SmartTimerService

class SmartTimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_REFRESH_TIMER_WIDGET || action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SmartTimerWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            for (id in ids) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_TIMER_WIDGET = "com.nobg.app.action.REFRESH_SMART_TIMER_WIDGET"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, SmartTimerWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_TIMER_WIDGET
            }
            context.sendBroadcast(intent)
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_smart_timer)
            val isRunning = SmartTimerService.isServiceRunning

            val repo = NobgRepository(context)
            val config = repo.getSmartTimerConfig()

            if (isRunning) {
                val elapsedMins = if (config.startTimeMillis > 0) {
                    ((System.currentTimeMillis() - config.startTimeMillis) / (60 * 1000L)).toInt()
                } else 0

                val statusText = if (config.durationMinutes > 0) {
                    val remaining = (config.durationMinutes - elapsedMins).coerceAtLeast(0)
                    "Còn ${remaining}p"
                } else {
                    "Đang đếm"
                }

                views.setTextViewText(R.id.tv_timer_widget_status, statusText)
                views.setTextColor(R.id.tv_timer_widget_status, Color.parseColor("#38BDF8"))
            } else {
                views.setTextViewText(R.id.tv_timer_widget_status, "1h (2p/lần)")
                views.setTextColor(R.id.tv_timer_widget_status, Color.parseColor("#94A3B8"))
            }

            // Click action: Toggle the default preset (1h - 2p/min - real clock time)
            val toggleIntent = Intent(context, SmartTimerService::class.java).apply {
                action = SmartTimerService.ACTION_TOGGLE_QUICK_DEFAULT
            }
            val pendingToggle = PendingIntent.getService(
                context,
                101,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_smart_timer_root, pendingToggle)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
