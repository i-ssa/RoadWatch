package com.example.roadwatch

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.ui.scan.ScanScreen
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class CameraScanTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun cameraScanCreatesDurableDraftWithoutSubmission() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.CAMERA)
        val owner="camera-test-${System.nanoTime()}"
        val store=ReportStore.get(context)
        var finished=false
        try {
            compose.setContent { ScanScreen(owner) { finished=true } }
            compose.waitUntil(timeoutMillis=45000) { store.reports(owner).isNotEmpty() }
            compose.waitUntil(timeoutMillis=45000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Looking for possible potholes") or androidx.compose.ui.test.hasText("Possible potholes saved for review")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            val screenshot=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null),"qa-scan.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            compose.onNodeWithText("Stop & review").performClick()
            compose.waitUntil(timeoutMillis=15000) { finished }
            assertEquals("draft",store.reports(owner).single().status)
        } finally {
            store.reports(owner).forEach { report ->
                store.captures(report.id).forEach { java.io.File(it.path).delete() }
                store.writableDatabase.delete("captures","report_id=?",arrayOf(report.id))
            }
            store.writableDatabase.delete("reports","owner=?",arrayOf(owner))
        }
    }
    @Test fun backgroundingCameraFinishesTheScanAsADraft() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.CAMERA)
        val owner="camera-background-${System.nanoTime()}"
        val store=ReportStore.get(context)
        val finished=java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            compose.setContent { ScanScreen(owner) { finished.set(true) } }
            compose.waitUntil(45000) { store.reports(owner).isNotEmpty() }
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            compose.waitUntil(30000) { finished.get() }
            val report=store.reports(owner).single()
            assertEquals("draft",report.status)
            assertTrue(store.captures(report.id).all { java.io.File(it.path).exists() })
            assertFalse(store.reports(owner).any { it.status=="pending" || it.status=="recording" })
        } finally {
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            store.reports(owner).forEach { report ->
                store.captures(report.id).forEach { java.io.File(it.path).delete() }
                store.writableDatabase.delete("captures","report_id=?",arrayOf(report.id))
                java.io.File(context.filesDir,"scans/${report.id}").delete()
            }
            store.writableDatabase.delete("reports","owner=?",arrayOf(owner))
        }
    }

}
