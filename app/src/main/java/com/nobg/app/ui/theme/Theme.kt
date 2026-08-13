package com.nobg.app.ui.theme

import android.app.Activity
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Color(0xFF70B7FF),
    onPrimary = Color(0xFF002F52),
    primaryContainer = Color(0xFF173B5D),
    onPrimaryContainer = Color(0xFFD5E9FF),
    secondary = Color(0xFF63D5AF),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF173F34),
    onSecondaryContainer = Color(0xFFC9F5E5),
    tertiary = Color(0xFFB9AEFF),
    onTertiary = Color(0xFF29205E),
    tertiaryContainer = Color(0xFF383164),
    onTertiaryContainer = Color(0xFFE5DFFF),
    background = Color(0xFF111214),
    onBackground = Color(0xFFF1F1F3),
    surface = Color(0xFF1C1D20),
    onSurface = Color(0xFFF1F1F3),
    surfaceVariant = Color(0xFF292A2E),
    onSurfaceVariant = Color(0xFFAAAAB0),
    surfaceContainerLowest = Color(0xFF1C1D20),
    surfaceContainerLow = Color(0xFF1C1D20),
    surfaceContainer = Color(0xFF1C1D20),
    surfaceContainerHigh = Color(0xFF242529),
    surfaceContainerHighest = Color(0xFF2A2B30),
    outline = Color(0xFF77787E),
    outlineVariant = Color(0xFF34353A),
    error = Color(0xFFFF8F9D),
    errorContainer = Color(0xFF4D2329),
    onErrorContainer = Color(0xFFFFD9DE)
)

// Premium system-like canvas: gray-white background + pure white grouped surfaces.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2D8CF0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAF4FF),
    onPrimaryContainer = Color(0xFF164B78),
    secondary = Color(0xFF35AE9D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9F8F4),
    onSecondaryContainer = Color(0xFF245E55),
    tertiary = Color(0xFF7065DA),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0EEFF),
    onTertiaryContainer = Color(0xFF4B438F),
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF202124),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFF0F0F3),
    onSurfaceVariant = Color(0xFF8A8A90),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF2F2F5),
    surfaceContainerHighest = Color(0xFFEDEDF1),
    outline = Color(0xFFA8A8AD),
    outlineVariant = Color(0xFFECECEF),
    error = Color(0xFFDA526B),
    errorContainer = Color(0xFFFFEDF0),
    onErrorContainer = Color(0xFF8F263A)
)

private val NobgTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp
        ),
        titleLarge = base.titleLarge.copy(
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp
        ),
        titleMedium = base.titleMedium.copy(
            fontSize = 18.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium
        ),
        titleSmall = base.titleSmall.copy(
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium
        ),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
        bodySmall = base.bodySmall.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
        labelLarge = base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    )
}

private val NobgShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            window.navigationBarColor = colorScheme.background.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NobgTypography,
        shapes = NobgShapes,
        content = content
    )
}
