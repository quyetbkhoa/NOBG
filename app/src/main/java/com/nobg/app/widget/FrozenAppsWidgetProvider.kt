package com.nobg.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.nobg.app.MainActivity
import com.nobg.app.R
import com.nobg.app.data.NobgRepository
import com.nobg.app.ui.UnfreezeAndLaunchActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FrozenAppsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_REFRESH_WIDGET || action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, FrozenAppsWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            for (id in ids) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.nobg.app.action.REFRESH_WIDGET"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, FrozenAppsWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            context.sendBroadcast(intent)
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_frozen_apps)

            // Read customization config
            val config = WidgetConfigManager.getConfig(context)

            // Dynamic Background Color with Opacity
            val alphaInt = ((config.opacityPct / 100f) * 255).toInt().coerceIn(0, 255)
            val bgColorInt = if (config.theme == "DARK") {
                android.graphics.Color.argb(alphaInt, 15, 23, 42) // #0F172A Slate Dark
            } else {
                android.graphics.Color.argb(alphaInt, 255, 255, 255) // #FFFFFF White
            }
            views.setInt(R.id.widget_container, "setBackgroundColor", bgColorInt)

            // Header Colors
            val titleColor = when (config.textColor) {
                "WHITE" -> android.graphics.Color.WHITE
                "BLACK" -> android.graphics.Color.parseColor("#0F172A")
                "ACCENT" -> if (config.theme == "DARK") android.graphics.Color.parseColor("#38BDF8") else android.graphics.Color.parseColor("#0284C7")
                else -> if (config.theme == "DARK") android.graphics.Color.parseColor("#38BDF8") else android.graphics.Color.parseColor("#0284C7")
            }
            val countColor = when (config.textColor) {
                "WHITE" -> android.graphics.Color.parseColor("#E2E8F0")
                "BLACK" -> android.graphics.Color.parseColor("#475569")
                "ACCENT" -> if (config.theme == "DARK") android.graphics.Color.parseColor("#7DD3FC") else android.graphics.Color.parseColor("#0369A1")
                else -> if (config.theme == "DARK") android.graphics.Color.parseColor("#94A3B8") else android.graphics.Color.parseColor("#64748B")
            }
            views.setTextColor(R.id.tv_widget_title, titleColor)
            views.setTextColor(R.id.tv_widget_count, countColor)
            views.setTextColor(R.id.tv_widget_empty, countColor)
            views.setInt(R.id.iv_widget_add, "setColorFilter", countColor)
            views.setInt(R.id.iv_widget_settings, "setColorFilter", countColor)

            // Dynamic Grid Columns
            views.setInt(R.id.widget_grid_view, "setNumColumns", config.numColumns)

            // Header & Blank area click intent -> Open NOBG directly to FREEZER_SHELF screen
            val openShelfIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("open_screen", "FREEZER_SHELF")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openShelfPendingIntent = PendingIntent.getActivity(
                context, 100, openShelfIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, openShelfPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_header, openShelfPendingIntent)
            views.setOnClickPendingIntent(R.id.tv_widget_title, openShelfPendingIntent)
            views.setOnClickPendingIntent(R.id.tv_widget_count, openShelfPendingIntent)
            views.setOnClickPendingIntent(R.id.tv_widget_empty, openShelfPendingIntent)

            // Add '+' button click intent -> Open AddShelfAppActivity
            val addAppIntent = Intent(context, com.nobg.app.ui.AddShelfAppActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val addAppPendingIntent = PendingIntent.getActivity(
                context, appWidgetId + 3000, addAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.iv_widget_add, addAppPendingIntent)

            // Settings gear click intent -> Open WidgetConfigActivity
            val configIntent = Intent(context, WidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val configPendingIntent = PendingIntent.getActivity(
                context, appWidgetId + 2000, configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.iv_widget_settings, configPendingIntent)

            // Individual app item click pending intent template
            val serviceIntent = Intent(context, FreezerWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_grid_view, serviceIntent)

            val clickIntent = Intent(context, UnfreezeAndLaunchActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_grid_view, pendingIntent)

            CoroutineScope(Dispatchers.IO).launch {
                val repo = NobgRepository(context)
                val shelfApps = repo.getFrozenShelfApps()
                views.setTextViewText(R.id.tv_widget_count, "${shelfApps.size} app")

                if (shelfApps.isEmpty()) {
                    views.setViewVisibility(R.id.tv_widget_empty, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_grid_view, View.GONE)
                } else {
                    views.setViewVisibility(R.id.tv_widget_empty, View.GONE)
                    views.setViewVisibility(R.id.widget_grid_view, View.VISIBLE)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_grid_view)
            }
        }
    }
}
