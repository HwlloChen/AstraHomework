package com.etaris.astrahomework.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF00504D),
    onPrimaryContainer = TealContainer,
    secondary = NavyLight,
    onSecondary = Color(0xFF123149),
    secondaryContainer = Navy,
    onSecondaryContainer = Color(0xFFD0E5FF),
    tertiary = StarLight,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF594300),
    onTertiaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF0F151C),
    onBackground = Color(0xFFDFE7EE),
    surface = Color(0xFF0F151C),
    onSurface = Color(0xFFDFE7EE),
    surfaceVariant = Color(0xFF3F494B),
    onSurfaceVariant = Color(0xFFBEC9CB),
    surfaceContainerLowest = Color(0xFF0A1016),
    surfaceContainerLow = Color(0xFF171E25),
    surfaceContainer = Color(0xFF1B2229),
    surfaceContainerHigh = Color(0xFF252C33),
    surfaceContainerHighest = Color(0xFF30373E),
    outline = Color(0xFF889395),
    outlineVariant = Color(0xFF3F494B),
)

private val LightColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Color(0xFF002F2D),
    secondary = Color(0xFF3F607A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E5FF),
    onSecondaryContainer = Navy,
    tertiary = Star,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF2B2000),
    background = Color(0xFFF5FAFC),
    onBackground = Color(0xFF17212B),
    surface = Color(0xFFF5FAFC),
    onSurface = Color(0xFF17212B),
    surfaceVariant = Color(0xFFDAE5E7),
    onSurfaceVariant = Color(0xFF3F494B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEDF4F7),
    surfaceContainer = Color(0xFFE7EFF3),
    surfaceContainerHigh = Color(0xFFE1E9ED),
    surfaceContainerHighest = Color(0xFFDBE3E7),
    outline = Color(0xFF6F797B),
    outlineVariant = Color(0xFFBEC9CB),
)

private val AstraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun AstraHomeworkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // The resolved Material3 1.4.0 keeps MaterialExpressiveTheme/MotionScheme internal.
    // Use the public theme API until the BOM exposes those APIs; never suppress visibility.
    MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = AstraShapes, content = content)
}
