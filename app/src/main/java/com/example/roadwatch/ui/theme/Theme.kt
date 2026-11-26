package com.example.roadwatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFACDABA),
    onPrimary = Color(0xFF143622),
    background = Color(0xFF131A16),
    onBackground = Color(0xFFE6EEE8),
    surface = Color(0xFF131A16),
    onSurface = Color(0xFFE6EEE8),
    surfaceVariant = Color(0xFF253329),
    onSurfaceVariant = Color(0xFFB8C7BD),
    surfaceContainerHigh = Color(0xFF243128),
    outline = Color(0xFF899D8E),
    outlineVariant = Color(0xFF44534A),
    secondaryContainer = Color(0xFF263C2E),
    onSecondaryContainer = Color(0xFFDBEDE0),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF194C35),
    onPrimary = Color.White,
    background = Color(0xFFFBFCF8),
    onBackground = Color(0xFF192921),
    surface = Color(0xFFFBFCF8),
    onSurface = Color(0xFF192921),
    surfaceVariant = Color(0xFFE8F0E9),
    onSurfaceVariant = Color(0xFF44584C),
    surfaceContainerHigh = Color(0xFFE8F0E9),
    outline = Color(0xFF64786A),
    outlineVariant = Color(0xFFD5DFD6),
    secondaryContainer = Color(0xFFE8F0E9),
    onSecondaryContainer = Color(0xFF26392D),
)

internal fun roadWatchColors(dark: Boolean) = if (dark) DarkColorScheme else LightColorScheme

@Composable
fun RoadWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = roadWatchColors(darkTheme), typography = Typography, content = content)
}
