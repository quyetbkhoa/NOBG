package com.nobg.app.widget

import android.appwidget.AppWidgetManager
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
import kotlinx.coroutines.runBlocking

class FreezerWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return FreezerWidgetFactory(applicationContext, widgetId)
    }
}

class FreezerWidgetFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private data class ShelfWidgetItem(
        val packageName: String? = null,
        val appName: String,
        val iconBitmap: Bitmap? = null,
        val isSettings: Boolean = false
    )

    private val items = mutableListOf<ShelfWidgetItem>()
    private var currentConfig = WidgetConfig()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val repo = NobgRepository(context)
        val packageManager = context.packageManager
        val refreshedItems = mutableListOf<ShelfWidgetItem>()
        currentConfig = WidgetConfigManager.getConfig(context)

        runBlocking {
            repo.getFrozenShelfApps().forEach { app ->
                val (name, bitmap) = getAppInfoBitmap(
                    packageManager,
                    app.packageName,
                    currentConfig.iconSizeDp,
                    currentConfig.cornerRadiusDp
                )
                refreshedItems.add(
                    ShelfWidgetItem(
                        packageName = app.packageName,
                        appName = name,
                        iconBitmap = bitmap
                    )
                )
            }
        }

        // Cài đặt luôn là item cuối, kể cả khi Kệ chưa có ứng dụng.
        refreshedItems.add(ShelfWidgetItem(appName = "Cài đặt", isSettings = true))
        items.clear()
        items.addAll(refreshedItems)
    }

    override fun onDestroy() = items.clear()

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val itemLayout = when (currentConfig.iconSizeDp) {
            36 -> R.layout.widget_frozen_item_small
            56 -> R.layout.widget_frozen_item_large
            else -> R.layout.widget_frozen_item
        }
        val views = RemoteViews(context.packageName, itemLayout)
        val item = items.getOrNull(position) ?: return views
        views.setTextViewText(R.id.tv_widget_app_name, item.appName)

        val textColor = when (currentConfig.textColor) {
            "WHITE" -> android.graphics.Color.WHITE
            "BLACK" -> android.graphics.Color.parseColor("#0F172A")
            "ACCENT" -> if (currentConfig.theme == "DARK") {
                android.graphics.Color.parseColor("#38BDF8")
            } else {
                android.graphics.Color.parseColor("#0284C7")
            }
            else -> if (currentConfig.theme == "DARK") {
                android.graphics.Color.WHITE
            } else {
                android.graphics.Color.parseColor("#0F172A")
            }
        }
        views.setTextColor(R.id.tv_widget_app_name, textColor)

        if (item.isSettings) {
            views.setImageViewResource(R.id.iv_widget_app_icon, R.drawable.ic_widget_settings_app)
        } else if (item.iconBitmap != null) {
            views.setImageViewBitmap(R.id.iv_widget_app_icon, item.iconBitmap)
        } else {
            views.setImageViewResource(R.id.iv_widget_app_icon, android.R.drawable.sym_def_app_icon)
        }

        val fillInIntent = Intent().apply {
            if (item.isSettings) {
                putExtra("open_widget_settings", true)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            } else {
                putExtra("pkg_to_launch", item.packageName)
            }
        }
        views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 3
    override fun getItemId(position: Int): Long = if (items.getOrNull(position)?.isSettings == true) {
        Long.MAX_VALUE
    } else {
        items.getOrNull(position)?.packageName?.hashCode()?.toLong() ?: position.toLong()
    }
    override fun hasStableIds(): Boolean = true

    private fun getAppInfoBitmap(
        packageManager: PackageManager,
        packageName: String,
        iconSizeDp: Int,
        cornerRadiusDp: Int
    ): Pair<String, Bitmap?> {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val name = packageManager.getApplicationLabel(appInfo).toString()
            val drawable = packageManager.getApplicationIcon(appInfo)
            val density = context.resources.displayMetrics.density
            val sizePx = (iconSizeDp * density).toInt().coerceAtLeast(24)
            val radiusPx = (cornerRadiusDp * density).toInt().coerceAtLeast(0)
            name to getRoundedBitmap(drawableToBitmap(drawable, sizePx), sizePx, radiusPx)
        } catch (_: Exception) {
            packageName to null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }

    private fun getRoundedBitmap(source: Bitmap, sizePx: Int, radiusPx: Int): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        if (radiusPx > 0) {
            canvas.drawRoundRect(rect, radiusPx.toFloat(), radiusPx.toFloat(), paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(source, null, rect, paint)
        return output
    }
}
