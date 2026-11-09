package com.example.roadwatch

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.ui.scan.ReportsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReportReviewTest {
    @get:Rule val compose=createComposeRule()
    @Test fun draftRequiresReviewAndExplicitSubmission() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val owner="review-test-${System.nanoTime()}"
        val store=ReportStore.get(context)
        val id=store.start(owner)
        store.finish(id)
        try {
            compose.setContent { Column { ReportsScreen(owner) } }
            compose.waitUntil(10000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Review captures")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Submit report").assertDoesNotExist()
            compose.onNodeWithText("Retry sync").assertDoesNotExist()
            assertEquals("draft",store.reports(owner).single().status)
            compose.onNodeWithText("Review captures").performClick()
            compose.onNodeWithText("Submit report").performClick()
            compose.waitUntil(10000) { store.reports(owner).single().status=="pending" }
        } finally {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("report-sync-$owner")
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("report-recovery-$owner")
            store.writableDatabase.delete("reports","id=?",arrayOf(id))
        }
    }
}
