package com.nobg.app.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.nobg.app.MainActivity
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
            val quickConfig = repo.getSmartTimerQuickConfig()

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
                views.setTextColor(R.id.tv_timer_widget_status, Color.WHITE)
            } else {
                val durationText = when {
                    quickConfig.durationMinutes == 0 -> "∞"
                    quickConfig.durationMinutes % 60 == 0 -> "${quickConfig.durationMinutes / 60}h"
                    else -> "${quickConfig.durationMinutes}p"
                }
                views.setTextViewText(
                    R.id.tv_timer_widget_status,
                    "$durationText · ${quickConfig.intervalMinutes}p"
                )
                views.setTextColor(R.id.tv_timer_widget_status, Color.WHITE)
            }

            // Click action: toggle the quick mode configured in the Timer screen.
            val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            val pendingToggle = if (isRunning || canPostNotifications) {
                val toggleIntent = Intent(context, SmartTimerService::class.java).apply {
                    action = SmartTimerService.ACTION_TOGGLE_WIDGET_QUICK
                }
                PendingIntent.getService(
                    context,
                    101,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                val permissionIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("open_screen", "SMART_TIMER")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                PendingIntent.getActivity(
                    context,
                    102,
                    permissionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            views.setOnClickPendingIntent(R.id.widget_smart_timer_root, pendingToggle)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
