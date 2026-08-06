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

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB6F2C2),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF558B2F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5F2B0),
    onSecondaryContainer = Color(0xFF142800),
    tertiary = Color(0xFF0077B6),
    surface = Color(0xFFFCFDF7),
    surfaceVariant = Color(0xFFDEE5DC),
    background = Color(0xFFFCFDF7),
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

@Composable
fun NobgTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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
