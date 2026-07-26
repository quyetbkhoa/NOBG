package com.nobg.app.widget

import android.content.Context

data class WidgetConfig(
    val theme: String = "DARK", // "DARK" or "LIGHT"
    val opacityPct: Int = 85,    // 0 to 100
    val numColumns: Int = 2,    // 2, 3, 4
    val iconSizeDp: Int = 48,   // 36, 48, 56
    val cornerRadiusDp: Int = 12 // 0 (square), 8, 12, 16, 24 (circle)
)

object WidgetConfigManager {
    private const val PREF_NAME = "nobg_widget_prefs"
    private const val KEY_THEME = "widget_theme"
    private const val KEY_OPACITY = "widget_opacity"
    private const val KEY_COLUMNS = "widget_columns"
    private const val KEY_ICON_SIZE = "widget_icon_size"
    private const val KEY_CORNER_RADIUS = "widget_corner_radius"

    fun getConfig(context: Context): WidgetConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return WidgetConfig(
            theme = prefs.getString(KEY_THEME, "DARK") ?: "DARK",
            opacityPct = prefs.getInt(KEY_OPACITY, 85),
            numColumns = prefs.getInt(KEY_COLUMNS, 2),
            iconSizeDp = prefs.getInt(KEY_ICON_SIZE, 48),
            cornerRadiusDp = prefs.getInt(KEY_CORNER_RADIUS, 12)
        )
    }

    fun saveConfig(context: Context, config: WidgetConfig) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME, config.theme)
            .putInt(KEY_OPACITY, config.opacityPct)
            .putInt(KEY_COLUMNS, config.numColumns)
            .putInt(KEY_ICON_SIZE, config.iconSizeDp)
            .putInt(KEY_CORNER_RADIUS, config.cornerRadiusDp)
            .apply()
    }
}
