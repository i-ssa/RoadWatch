package com.example.roadwatch.ui.dashboard

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class DashboardScreenTest {
    @get:Rule val compose = createComposeRule()
    private val preferences get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("roadwatch_ui", Context.MODE_PRIVATE)
    @Before fun reset() { preferences.edit().clear().commit() }
    @Test fun introductionCompletesAndNavigationWorks() {
        compose.setContent { DashboardScreen(onBack = {}) }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Open RoadWatch").performClick()
        compose.onNodeWithText("Welcome to RoadWatch.").assertIsDisplayed()
        compose.onNode(hasText("Profile") and hasClickAction()).performClick()
        compose.onNodeWithText("Your space.").assertIsDisplayed()
        compose.onNodeWithText("Use device appearance").performClick()
        compose.runOnIdle { assertEquals("system", preferences.getString("appearance", null)) }
    }
    @Test fun sampleScanProducesReviewableDetection() {
        preferences.edit().putBoolean("intro_v1", true).commit()
        compose.setContent { DashboardScreen(onBack = {}) }
        compose.onNodeWithText("Start scan").performClick()
        compose.onNodeWithText("Try sample scan").performClick()
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithText("Stop scan & review").performClick()
        compose.onNodeWithText("Possible pothole").assertIsDisplayed()
        compose.onNode(hasText("Profile") and hasClickAction()).performClick()
        compose.onNodeWithText("Your space.").assertIsDisplayed()
    }
}
