package com.nobg.app.widget

import android.content.Context

data class WidgetConfig(
    val theme: String = "DARK", // "DARK" or "LIGHT"
    val textColor: String = "SYSTEM", // "SYSTEM", "WHITE", "BLACK", "ACCENT"
    val opacityPct: Int = 85,    // 0 to 100
    val numColumns: Int = 2,    // 2, 3, 4
    val iconSizeDp: Int = 48,   // 36, 48, 56
    val cornerRadiusDp: Int = 18 // 12 (Vừa), 18 (Bo Tròn), 24 (Tròn)
)

object WidgetConfigManager {
    private const val PREF_NAME = "nobg_widget_prefs"
    private const val KEY_THEME = "widget_theme"
    private const val KEY_TEXT_COLOR = "widget_text_color"
    private const val KEY_OPACITY = "widget_opacity"
    private const val KEY_COLUMNS = "widget_columns"
    private const val KEY_ICON_SIZE = "widget_icon_size"
    private const val KEY_CORNER_RADIUS = "widget_corner_radius"

    private const val KEY_DELETE_MODE = "widget_delete_mode"

    fun getConfig(context: Context): WidgetConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return WidgetConfig(
            theme = prefs.getString(KEY_THEME, "DARK") ?: "DARK",
            textColor = prefs.getString(KEY_TEXT_COLOR, "SYSTEM") ?: "SYSTEM",
            opacityPct = prefs.getInt(KEY_OPACITY, 85),
            numColumns = prefs.getInt(KEY_COLUMNS, 2),
            iconSizeDp = prefs.getInt(KEY_ICON_SIZE, 48),
            cornerRadiusDp = prefs.getInt(KEY_CORNER_RADIUS, 18)
        )
    }

    fun isDeleteMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DELETE_MODE, false)
    }

    fun setDeleteMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DELETE_MODE, enabled).apply()
    }

    fun saveConfig(context: Context, config: WidgetConfig) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME, config.theme)
            .putString(KEY_TEXT_COLOR, config.textColor)
            .putInt(KEY_OPACITY, config.opacityPct)
            .putInt(KEY_COLUMNS, config.numColumns)
            .putInt(KEY_ICON_SIZE, config.iconSizeDp)
            .putInt(KEY_CORNER_RADIUS, config.cornerRadiusDp)
            .apply()
    }
}
