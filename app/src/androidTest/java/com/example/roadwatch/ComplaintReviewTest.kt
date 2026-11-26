package com.example.roadwatch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.ui.scan.ReportsScreen
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComplaintReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureComplaintPrefillsContactAndLeavesRoadWatchDraftPrivate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = "complaint-test-${System.nanoTime()}"
        val store = ReportStore.get(context)
        val id = store.start(owner)
        store.addCapture(id, byteArrayOf(), JSONArray())
        store.finish(id)
        try {
            compose.setContent {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReportsScreen(owner, "reporter@example.com", "Road Reporter")
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText("Review captures")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Review captures").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText("Report pothole to authority")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Report pothole to authority").performScrollTo().performClick()
            compose.onNodeWithText("This capture has no GPS fix. Enter the road and a nearby landmark.").assertExists()
            compose.onNodeWithText("Road Reporter").assertExists()
            compose.onNodeWithText("reporter@example.com").assertExists()
            compose.onNodeWithText("KeNHA").performScrollTo().performClick()
            compose.onNodeWithText("Open official form").assertIsNotEnabled()
            compose.onNodeWithText("Road or landmark").performScrollTo().performTextInput("Thika Superhighway")
            compose.onNodeWithText("Back").performClick()
            assertEquals("draft", store.reports(owner).single().status)
        } finally {
            WorkManager.getInstance(context).cancelUniqueWork("report-sync-$owner")
            WorkManager.getInstance(context).cancelUniqueWork("report-recovery-$owner")
            store.deleteLocalReport(id, owner)
        }
    }
}
