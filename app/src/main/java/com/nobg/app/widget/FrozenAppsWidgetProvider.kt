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
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, FrozenAppsWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widget_grid_view)
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
