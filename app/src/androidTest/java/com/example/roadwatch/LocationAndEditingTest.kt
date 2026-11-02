package com.example.roadwatch

import android.location.Location
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.ui.scan.ScanLocation
import com.example.roadwatch.ui.map.HazardRepository
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class LocationAndEditingTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun captureCoordinatesSurviveReopeningAndDraftEditingEnforcesOwnership() {
        val name="geo-edit-${System.nanoTime()}.db"
        var store=ReportStore(context,name)
        val report=store.start("owner-a")
        val fix=Location("gps").apply { latitude=-1.286389; longitude=36.817223; accuracy=8f; elapsedRealtimeNanos=1000000000L }
        store.addCapture(report,byteArrayOf(1,2,3),JSONArray(),fix)
        store.finish(report)
        store.close()
        store=ReportStore(context,name)
        try {
            val capture=store.captures(report).single()
            assertEquals(fix.latitude,capture.latitude!!,0.0000001)
            assertEquals(fix.longitude,capture.longitude!!,0.0000001)
            assertEquals(8f,capture.accuracy!!,0f)
            assertTrue(capture.capturedAt>0)
            assertFalse(store.removeCapture(capture.id,report,"owner-b"))
            assertFalse(store.deleteLocalReport(report,"owner-b"))
            assertTrue(java.io.File(capture.path).exists())
            assertTrue(store.removeCapture(capture.id,report,"owner-a"))
            assertFalse(java.io.File(capture.path).exists())
            assertTrue(store.captures(report).isEmpty())
            assertTrue(store.deleteLocalReport(report,"owner-a"))
            assertTrue(store.reports("owner-a").isEmpty())
        } finally {
            store.captures(report).forEach { java.io.File(it.path).delete() }
            store.close(); context.deleteDatabase(name)
        }
    }
    @Test fun submittedReportsCannotBeEditedOrDeletedDuringSync() {
        val name="queued-edit-${System.nanoTime()}.db"
        val store=ReportStore(context,name)
        val report=store.start("owner-a")
        store.addCapture(report,byteArrayOf(1),JSONArray())
        store.finish(report)
        try {
            val capture=store.captures(report).single()
            assertTrue(store.submit(report,"owner-a"))
            assertFalse(store.removeCapture(capture.id,report,"owner-a"))
            assertFalse(store.deleteLocalReport(report,"owner-a"))
            assertTrue(java.io.File(capture.path).exists())
            store.markSynced(report)
            assertTrue(store.deleteLocalReport(report,"owner-a"))
            assertFalse(java.io.File(capture.path).exists())
        } finally {
            store.captures(report).forEach { java.io.File(it.path).delete() }
            store.close(); context.deleteDatabase(name)
        }
    }
    @Test fun staleInaccurateAndInvalidFixesAreRejectedButZeroCoordinatesAreValid() {
        val fix=Location("gps").apply { latitude=0.0; longitude=0.0; accuracy=10f; elapsedRealtimeNanos=1000000000L }
        assertTrue(ScanLocation.isFresh(fix,2000000000L))
        assertFalse(ScanLocation.isFresh(fix,32000000000L))
        assertFalse(ScanLocation.isFresh(fix,0L))
        fix.accuracy=101f
        assertFalse(ScanLocation.isFresh(fix,2000000000L))
        assertNotNull(HazardRepository.point("id",0.0,0.0,"draft"))
        assertNull(HazardRepository.point("id",null,null,"draft"))
        assertNull(HazardRepository.point("id",Double.NaN,36.0,"draft"))
        assertNull(HazardRepository.point("id",91.0,36.0,"draft"))
    }
    @Test fun oldUnlocatedCapturesRemainUnlocatedAfterMigration() {
        val name="geo-migration-${System.nanoTime()}.db"
        val old=context.openOrCreateDatabase(name,0,null)
        old.execSQL("CREATE TABLE reports(id TEXT PRIMARY KEY, owner TEXT NOT NULL, started INTEGER NOT NULL, status TEXT NOT NULL, sync_error TEXT)")
        old.execSQL("CREATE TABLE captures(id TEXT PRIMARY KEY, report_id TEXT NOT NULL, path TEXT NOT NULL, boxes TEXT NOT NULL)")
        old.execSQL("INSERT INTO captures VALUES('capture','report','/missing','[]')")
        old.version=2; old.close()
        val store=ReportStore(context,name)
        try {
            val capture=store.captures("report").single()
            assertNull(capture.latitude); assertNull(capture.longitude); assertNull(capture.accuracy)
            assertEquals(0L,capture.capturedAt)
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
