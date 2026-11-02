package com.example.roadwatch

import android.Manifest
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.ui.scan.ScanScreen
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class CameraScanTest {
    @get:Rule val compose=createComposeRule()
    @Test fun cameraScanCreatesDurableReportAndStopQueuesIt() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.CAMERA)
        val owner="camera-test-${System.nanoTime()}"
        val store=ReportStore.get(context)
        var finished=false
        try {
            compose.setContent { ScanScreen(owner) { finished=true } }
            compose.waitUntil(timeoutMillis=45000) { store.reports(owner).isNotEmpty() }
            compose.onNodeWithText("Stop scan & save report").performClick()
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
}
