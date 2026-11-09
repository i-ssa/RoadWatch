package com.example.roadwatch

import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.ui.scan.PotholeDetector
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
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
            assertEquals("draft",restored.reports("account-a").single().status)
            assertTrue(restored.reports("account-b").isEmpty())
            assertEquals(1,restored.captures(id).size)
            assertFalse(restored.submit(id,"account-b"))
            assertEquals("draft",restored.reports("account-a").single().status)
            assertTrue(restored.submit(id,"account-a"))
            assertEquals("pending",restored.reports("account-a").single().status)
            assertFalse(restored.submit(id,"account-a"))
            restored.markSynced(id,"account-a")
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
            assertEquals("draft",store.reports("account-a").single().status)
            assertEquals("recording",store.reports("account-b").single().status)
        } finally { store.close(); context.deleteDatabase(name) }
    }
    @Test fun upgradingReturnsLegacyPendingReportsToDrafts() {
        val name="migration-test-${System.nanoTime()}.db"
        val legacy=context.openOrCreateDatabase(name,0,null)
        legacy.execSQL("CREATE TABLE reports(id TEXT PRIMARY KEY, owner TEXT NOT NULL, started INTEGER NOT NULL, status TEXT NOT NULL, sync_error TEXT)")
        legacy.execSQL("CREATE TABLE captures(id TEXT PRIMARY KEY, report_id TEXT NOT NULL, path TEXT NOT NULL, boxes TEXT NOT NULL)")
        for(status in listOf("pending","synced","recording")) {
            legacy.execSQL("INSERT INTO reports VALUES(?, 'account-a', 0, ?, NULL)",arrayOf(status,status))
        }
        legacy.version=1
        legacy.close()
        val upgraded=ReportStore(context,name)
        try {
            val reports=upgraded.reports("account-a").associateBy { it.id }
            assertEquals("draft",reports.getValue("pending").status)
            assertEquals("synced",reports.getValue("synced").status)
            assertEquals("recording",reports.getValue("recording").status)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
    @Test fun partiallyMigratedDatabasePreservesExistingCoordinates() {
        val name="partial-migration-${System.nanoTime()}.db"
        val legacy=context.openOrCreateDatabase(name,0,null)
        legacy.execSQL("CREATE TABLE reports(id TEXT PRIMARY KEY, owner TEXT NOT NULL, started INTEGER NOT NULL, status TEXT NOT NULL, sync_error TEXT)")
        legacy.execSQL("CREATE TABLE captures(id TEXT PRIMARY KEY, report_id TEXT NOT NULL, path TEXT NOT NULL, boxes TEXT NOT NULL, latitude REAL)")
        legacy.execSQL("INSERT INTO reports VALUES('report','account-a',123,'pending',NULL)")
        legacy.execSQL("INSERT INTO captures VALUES('capture','report','/saved.jpg','[]',-1.28)")
        legacy.version=2; legacy.close()
        val upgraded=ReportStore(context,name)
        try {
            assertEquals("pending",upgraded.reports("account-a").single().status)
            val capture=upgraded.captures("report").single()
            assertEquals(-1.28,capture.latitude!!,0.0)
            assertNull(capture.longitude)
            assertEquals(0L,capture.capturedAt)
        } finally { upgraded.close(); context.deleteDatabase(name) }
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
        val confidence = context.assets.open("pothole-model.json").bufferedReader().use {
            JSONObject(it.readText()).getDouble("confidence").toFloat()
        }
        PotholeDetector(context).use { detector ->
            val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
            try {
                val boxes=detector.detect(bitmap)
                assertTrue(boxes.all { it.confidence>=confidence && it.x>=0 && it.y>=0 && it.x+it.width<=1.001f && it.y+it.height<=1.001f })
            } finally { bitmap.recycle() }
        }
    }
}
