package com.example.roadwatch

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.roadwatch.ui.theme.roadWatchColors
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun commonTextAndControlColorsHaveContrastInBothModes() {
        for (dark in listOf(false, true)) {
            val colors = roadWatchColors(dark)
            assertContrast("field text", colors.onSurface, colors.surface, 4.5f)
            assertContrast("field label", colors.onSurfaceVariant, colors.surface, 4.5f)
            assertContrast("focused label", colors.primary, colors.surface, 4.5f)
            assertContrast("error text", colors.error, colors.surface, 4.5f)
            assertContrast("field outline", colors.outline, colors.surface, 3f)
            assertContrast("button text", colors.onPrimary, colors.primary, 4.5f)
            assertContrast("information card", colors.onSecondaryContainer, colors.secondaryContainer, 4.5f)
            assertContrast("screen text", colors.onBackground, colors.background, 4.5f)
        }
    }

    private fun assertContrast(role: String, foreground: Color, background: Color, minimum: Float) {
        val first = foreground.luminance()
        val second = background.luminance()
        val ratio = (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
        assertTrue("$role contrast was $ratio, expected $minimum", ratio >= minimum)
    }
}
