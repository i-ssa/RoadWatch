package com.example.roadwatch

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.ui.auth.RoadWatchAuthFlow
import com.example.roadwatch.ui.theme.RoadWatchTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class AuthAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emailEntryInLightMode() = checkEmailEntry(dark = false)
    @Test fun emailEntryInDarkMode() = checkEmailEntry(dark = true)

    private fun checkEmailEntry(dark: Boolean) {
        compose.setContent {
            RoadWatchTheme(darkTheme = dark) {
                RoadWatchAuthFlow(
                    logoRes = R.drawable.roadwatch_logo,
                    isLoading = false,
                    onLogin = { _, _ -> },
                    onSignUp = { _, _, _ -> },
                    onResetPassword = {},
                    onResendConfirmation = {},
                    onBack = {},
                )
            }
        }
        val email = "demo@example.com"
        compose.onNodeWithText("Email address").performTextInput(email)
        compose.onNodeWithText(email).assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val mode = if (dark) "dark" else "light"
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "qa-auth-$mode.png")
            .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
