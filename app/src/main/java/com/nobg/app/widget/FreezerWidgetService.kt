package com.nobg.app.widget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.nobg.app.R
import com.nobg.app.data.NobgRepository
import com.nobg.app.ui.UnfreezeAndLaunchActivity
import kotlinx.coroutines.runBlocking

class FreezerWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return FreezerWidgetFactory(applicationContext)
    }
}

class FreezerWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private val items = mutableListOf<ShelfWidgetItem>()

    data class ShelfWidgetItem(
        val packageName: String,
        val appName: String,
        val iconBitmap: Bitmap?
    )

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val repo = NobgRepository(context)
        val pm = context.packageManager
        val list = mutableListOf<ShelfWidgetItem>()

        runBlocking {
            val shelfApps = repo.getFrozenShelfApps()
            for (app in shelfApps) {
                val (appName, bitmap) = getAppInfoBitmap(pm, app.packageName)
                list.add(ShelfWidgetItem(app.packageName, appName, bitmap))
            }
        }

        items.clear()
        items.addAll(list)
    }

    override fun onDestroy() {
        items.clear()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_frozen_item)
        if (position >= items.size) return views

        val item = items[position]
        views.setTextViewText(R.id.tv_widget_app_name, item.appName)

        if (item.iconBitmap != null) {
            views.setImageViewBitmap(R.id.iv_widget_app_icon, item.iconBitmap)
        } else {
            views.setImageViewResource(R.id.iv_widget_app_icon, android.R.drawable.sym_def_app_icon)
        }

        val fillInIntent = Intent().apply {
            putExtra("pkg_to_launch", item.packageName)
        }
        views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true

    private fun getAppInfoBitmap(pm: PackageManager, packageName: String): Pair<String, Bitmap?> {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            val drawable = pm.getApplicationIcon(appInfo)
            name to drawableToBitmap(drawable)
        } catch (_: Exception) {
            packageName to null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
