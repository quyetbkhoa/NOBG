package com.nobg.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FCB9F),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF2E6B47),
    onPrimaryContainer = Color(0xFFA8F5C5),
    secondary = Color(0xFF9CCC65),
    onSecondary = Color(0xFF1C3A00),
    secondaryContainer = Color(0xFF3A5A22),
    onSecondaryContainer = Color(0xFFD3F5A0),
    tertiary = Color(0xFF80D8FF),
    surface = Color(0xFF101411),
    surfaceVariant = Color(0xFF253129),
    background = Color(0xFF101411),
    error = Color(0xFFFFB4AB)
)

// Giao diện Trắng - Xanh (White-Blue Light Theme)
private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF0277BD),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCBE6FF),
    onSecondaryContainer = Color(0xFF001D33),
    tertiary = Color(0xFF0091EA),
    surface = Color(0xFFFAFCFF),
    surfaceVariant = Color(0xFFE1E8F0),
    background = Color(0xFFF3F8FF),
    error = Color(0xFFBA1A1A)
)

private val NobgTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Normal)
    )
}

private val NobgShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * @param darkTheme       Nền tối hay sáng
 * @param useDynamicColor Chỉ dùng dynamic color (Material You) khi chọn "Theo hệ thống";
 *                        nếu người dùng chọn cụ thể Sáng/Tối thì dùng bảng màu tĩnh để giữ đúng
 *                        giao diện Trắng-Xanh hoặc Tối đã chọn.
 */
@Composable
fun NobgTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NobgTypography,
        shapes = NobgShapes,
        content = content
    )
}
