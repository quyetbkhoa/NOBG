package com.nobg.app.widget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
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

    private var currentConfig = WidgetConfig()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val repo = NobgRepository(context)
        val pm = context.packageManager
        val list = mutableListOf<ShelfWidgetItem>()

        currentConfig = WidgetConfigManager.getConfig(context)

        runBlocking {
            val shelfApps = repo.getFrozenShelfApps()
            for (app in shelfApps) {
                val (appName, bitmap) = getAppInfoBitmap(pm, app.packageName, currentConfig.iconSizeDp, currentConfig.cornerRadiusDp)
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

        val textColor = when (currentConfig.textColor) {
            "WHITE" -> android.graphics.Color.WHITE
            "BLACK" -> android.graphics.Color.parseColor("#0F172A")
            "ACCENT" -> if (currentConfig.theme == "DARK") android.graphics.Color.parseColor("#38BDF8") else android.graphics.Color.parseColor("#0284C7")
            else -> if (currentConfig.theme == "DARK") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#0F172A")
        }
        views.setTextColor(R.id.tv_widget_app_name, textColor)

        if (item.iconBitmap != null) {
            views.setImageViewBitmap(R.id.iv_widget_app_icon, item.iconBitmap)
        } else {
            views.setImageViewResource(R.id.iv_widget_app_icon, android.R.drawable.sym_def_app_icon)
        }

        val isDeleteMode = WidgetConfigManager.isDeleteMode(context)
        if (isDeleteMode) {
            views.setViewVisibility(R.id.iv_widget_delete_badge, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.iv_widget_delete_badge, android.view.View.GONE)
        }

        val fillInIntent = Intent().apply {
            putExtra("pkg_to_launch", item.packageName)
            putExtra("is_delete_mode", isDeleteMode)
        }
        views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private fun getAppInfoBitmap(pm: PackageManager, packageName: String, iconSizeDp: Int, cornerRadiusDp: Int): Pair<String, Bitmap?> {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            val drawable = pm.getApplicationIcon(appInfo)
            val density = context.resources.displayMetrics.density
            val sizePx = (iconSizeDp * density).toInt().coerceAtLeast(24)
            val radiusPx = (cornerRadiusDp * density).toInt().coerceAtLeast(0)
            val rawBitmap = drawableToBitmap(drawable, sizePx)
            name to getRoundedBitmap(rawBitmap, sizePx, radiusPx)
        } catch (_: Exception) {
            packageName to null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getRoundedBitmap(src: Bitmap, sizePx: Int, radiusPx: Int): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())

        if (radiusPx > 0) {
            canvas.drawRoundRect(rect, radiusPx.toFloat(), radiusPx.toFloat(), paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(src, null, rect, paint)
        return output
    }
}
