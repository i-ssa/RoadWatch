package com.example.roadwatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D472F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDECE2),
    onPrimaryContainer = Color(0xFF113725),
    background = Color(0xFFF4F7F4),
    onBackground = Color(0xFF17251E),
    surface = Color.White,
    onSurface = Color(0xFF17251E),
    surfaceVariant = Color(0xFFE9EFEA),
    onSurfaceVariant = Color(0xFF526158),
    outline = Color(0xFF78867D),
    outlineVariant = Color(0xFFD8E0DA),
    secondaryContainer = Color(0xFFDDECE2),
    onSecondaryContainer = Color(0xFF113725)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA4D5B5),
    onPrimary = Color(0xFF10271A),
    primaryContainer = Color(0xFF233D2D),
    onPrimaryContainer = Color(0xFFD7EFDD),
    background = Color(0xFF101713),
    onBackground = Color(0xFFE6EEE8),
    surface = Color(0xFF19221C),
    onSurface = Color(0xFFE6EEE8),
    surfaceVariant = Color(0xFF26332B),
    onSurfaceVariant = Color(0xFFB4C3B9),
    outline = Color(0xFF85988B),
    outlineVariant = Color(0xFF36473C),
    secondaryContainer = Color(0xFF233D2D),
    onSecondaryContainer = Color(0xFFD7EFDD)
)

private val RoadWatchShapes = Shapes(
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RoadWatchTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = RoadWatchShapes,
        typography = Typography,
        content = content
    )
}
