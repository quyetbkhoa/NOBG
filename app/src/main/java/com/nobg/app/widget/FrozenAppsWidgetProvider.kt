package com.nobg.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nobg.app.R
import com.nobg.app.data.NobgMode
import com.nobg.app.data.NobgRepository
import com.nobg.app.ui.UnfreezeAndLaunchActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FrozenAppsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repo = NobgRepository(context)
        CoroutineScope(Dispatchers.IO).launch {
            val allEnabled = repo.getEnabledApps()
            val frozenApps = allEnabled.filter { it.mode == NobgMode.DISABLE_ENABLE }

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, frozenApps.map { it.packageName })
            }
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, frozenPkgs: List<String>) {
            val views = RemoteViews(context.packageName, R.layout.widget_frozen_apps)

            if (frozenPkgs.isEmpty()) {
                views.setTextViewText(R.id.tv_widget_status, "🧊 Chưa có ứng dụng nào được Đóng băng")
            } else {
                views.setTextViewText(R.id.tv_widget_status, "🧊 Kệ đóng băng: ${frozenPkgs.size} ứng dụng")
            }

            // Click open first frozen app if available or open NOBG
            if (frozenPkgs.isNotEmpty()) {
                val firstPkg = frozenPkgs.first()
                val intent = Intent(context, UnfreezeAndLaunchActivity::class.java).apply {
                    putExtra("pkg_to_launch", firstPkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            } else {
                val intent = Intent(context, com.nobg.app.MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
