package com.example.roadwatch.ui.dashboard

import android.content.Context
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
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
        compose.setContent { DashboardScreen(onBack = {}, owner = "layout-test") }
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("RoadWatch").assertIsDisplayed()
        compose.onNode(hasText("Profile") and hasClickAction()).performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Device").performClick()
        compose.runOnIdle { assertEquals("system", preferences.getString("appearance", null)) }
    }
    @Test fun mapFillsViewportAndBackReturnsHome() {
        preferences.edit().putBoolean("intro_v1",true).commit()
        compose.setContent { DashboardScreen(onBack={},owner="layout-test") }
        compose.onNode(hasText("Map") and hasClickAction()).performClick()
        compose.onNodeWithTag("journey-map").assertIsDisplayed()
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        val map=compose.onNodeWithTag("journey-map").fetchSemanticsNode().boundsInRoot
        assertEquals(root.height,map.height,1f)
        assertEquals(root.width,map.width,1f)
        compose.onNodeWithContentDescription("RoadWatch AI").assertDoesNotExist()
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val screenshot=instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"qa-map-dashboard.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithContentDescription("Back to Home").performClick()
        compose.onNodeWithText("RoadWatch").assertIsDisplayed()
    }
    @Test fun homeOpensScanSetupAndReports() {
        preferences.edit().putBoolean("intro_v1", true).commit()
        compose.setContent { DashboardScreen(onBack = {}, owner = "layout-test") }
        compose.onNodeWithText("Start scan").performClick()
        compose.onNodeWithText("Start camera scan").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Review reports").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("No reports yet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No reports yet").assertIsDisplayed()
    }
}
