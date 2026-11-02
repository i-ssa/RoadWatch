package com.example.roadwatch

import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.ui.scan.PotholeDetector
import android.graphics.Bitmap
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class OfflineReportsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun reportsSurviveReopeningAndStayWithTheirOwner() {
        val name="offline-test-${System.nanoTime()}.db"
        val first=ReportStore(context,name)
        val id=first.start("account-a")
        first.addCapture(id,byteArrayOf(1,2,3),JSONArray("[{\"confidence\":0.9}]"))
        first.finish(id)
        first.close()
        val restored=ReportStore(context,name)
        try {
            assertEquals("pending",restored.reports("account-a").single().status)
            assertTrue(restored.reports("account-b").isEmpty())
            assertEquals(1,restored.captures(id).size)
            restored.markSynced(id)
            assertEquals("synced",restored.reports("account-a").single().status)
        } finally {
            restored.captures(id).forEach { java.io.File(it.path).delete() }
            restored.close(); context.deleteDatabase(name)
        }
    }
    @Test fun interruptedScanIsRecoveredWithoutChangingAnotherAccount() {
        val name="recovery-test-${System.nanoTime()}.db"
        val store=ReportStore(context,name)
        try {
            store.start("account-a"); store.start("account-b")
            store.recoverInterrupted("account-a")
            assertEquals("pending",store.reports("account-a").single().status)
            assertEquals("recording",store.reports("account-b").single().status)
        } finally { store.close(); context.deleteDatabase(name) }
    }
    @Test fun recreatingScreenDoesNotFinalizeAnActiveScan() {
        val name="active-test-${System.nanoTime()}.db"
        val store=ReportStore(context,name)
        try {
            store.recoverOncePerProcess("account-a")
            store.start("account-a")
            store.recoverOncePerProcess("account-a")
            assertEquals("recording",store.reports("account-a").single().status)
        } finally { store.close(); context.deleteDatabase(name) }
    }
    @Test fun bundledModelRunsWithoutNetworkAndReturnsValidBoxes() {
        PotholeDetector(context).use { detector ->
            val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
            try {
                val boxes=detector.detect(bitmap)
                assertTrue(boxes.all { it.confidence>=0.25f && it.x>=0 && it.y>=0 && it.x+it.width<=1.001f && it.y+it.height<=1.001f })
            } finally { bitmap.recycle() }
        }
    }
}
