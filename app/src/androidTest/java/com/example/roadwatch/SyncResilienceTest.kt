package com.example.roadwatch

import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.data.reports.ReportSyncEngine
import com.example.roadwatch.data.reports.SyncFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.IOException

/** Real SQLite/files with a fake remote: no customer accounts or cloud data are touched. */
class SyncResilienceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var name: String
    private lateinit var store: ReportStore
    @Before fun createStore() {
        name="sync-resilience-${System.nanoTime()}.db"
        store=ReportStore(context,name)
    }
    @After fun cleanStore() {
        for(owner in listOf("owner-a","owner-b")) for(report in store.reports(owner)) {
            store.captures(report.id).forEach { File(it.path).delete() }
            File(context.filesDir,"scans/${report.id}").delete()
        }
        store.close(); context.deleteDatabase(name)
    }
    private fun report(owner: String="owner-a", submitted: Boolean=true, captures: Int=2): String {
        val id=store.start(owner)
        repeat(captures) { store.addCapture(id,byteArrayOf(it.toByte(),2,3),JSONArray("[{\"confidence\":0.9}]")) }
        store.finish(id)
        if(submitted) assertTrue(store.submit(id,owner))
        return id
    }
    private fun engine(remote: FakeRemote, guard: () -> Unit = {}) =
        ReportSyncEngine(store,guard,remote::image,remote::report)
    private fun status(id: String, owner: String="owner-a")=store.reports(owner).single { it.id==id }
    private fun assertImagesRemain(id: String) { assertTrue(store.captures(id).all { File(it.path).exists() }) }

    @Test fun draftsAndOtherAccountsAreNeverUploaded() = runBlocking {
        val draft=report(submitted=false)
        val other=report("owner-b")
        val remote=FakeRemote()
        assertTrue(engine(remote).sync("owner-a"))
        assertEquals(0,remote.imageAttempts)
        assertTrue(remote.rows.isEmpty())
        assertEquals("draft",status(draft).status)
        assertEquals("pending",status(other,"owner-b").status)
    }
    @Test fun offlineFailurePreservesDraftSubmissionAndAllImages() = runBlocking {
        val id=report()
        val remote=FakeRemote().apply { offline=true }
        assertFalse(engine(remote).sync("owner-a"))
        assertEquals("pending",status(id).status)
        assertNotNull(status(id).syncError)
        assertImagesRemain(id)
        assertTrue(remote.rows.isEmpty())
        assertTrue(remote.images.isEmpty())
        remote.offline=false
        assertTrue(engine(remote).sync("owner-a"))
        assertEquals("synced",status(id).status)
        assertNull(status(id).syncError)
        assertImagesRemain(id)
    }
    @Test fun lostReportAcknowledgementRetriesSameIdsAfterProcessReopen() = runBlocking {
        val id=report()
        val remote=FakeRemote().apply { loseReportResponseOnce=true }
        assertFalse(engine(remote).sync("owner-a"))
        assertEquals(1,remote.rows.size) // The server accepted it before the response was lost.
        assertEquals("pending",status(id).status)
        val firstPayload=remote.rows.getValue(id).toString()
        assertImagesRemain(id)
        store.close(); store=ReportStore(context,name)
        assertTrue(engine(remote).sync("owner-a"))
        assertEquals("synced",status(id).status)
        assertEquals(1,remote.rows.size)
        assertEquals(2,remote.images.size)
        assertEquals(2,remote.reportAttempts)
        assertEquals(firstPayload,remote.rows.getValue(id).toString())
        assertFalse(store.submit(id,"owner-a"))
        val requests=remote.imageAttempts+remote.reportAttempts
        assertTrue(engine(remote).sync("owner-a"))
        assertEquals(requests,remote.imageAttempts+remote.reportAttempts)
    }
    @Test fun interruptedImageUploadRetriesStableObjectPaths() = runBlocking {
        val id=report()
        val remote=FakeRemote().apply { loseImageResponseAt=2 }
        assertFalse(engine(remote).sync("owner-a"))
        assertEquals("pending",status(id).status)
        assertTrue(remote.rows.isEmpty())
        assertImagesRemain(id)
        val originalPaths=remote.images.keys.toSet()
        assertTrue(engine(remote).sync("owner-a"))
        assertEquals(originalPaths,remote.images.keys)
        assertEquals(2,remote.images.size)
        assertEquals(1,remote.rows.size)
        val captures=remote.rows.getValue(id).getJSONArray("captures")
        assertEquals(2,captures.length())
        repeat(captures.length()) { index ->
            assertTrue(remote.images.containsKey(captures.getJSONObject(index).getString("image_path")))
        }
        assertEquals("synced",status(id).status)
    }
    @Test fun accountChangeAfterUploadStopsAllFurtherRequests() = runBlocking {
        val id=report()
        val other=report("owner-b")
        var account="owner-a"
        var generation=1
        val remote=FakeRemote().apply { afterImage={ account="owner-b"; generation++ } }
        val guard={ if(account!="owner-a" || generation!=1) throw CancellationException("Account changed") }
        try { engine(remote,guard).sync("owner-a"); fail("Expected account cancellation") }
        catch(_: CancellationException) { }
        assertEquals(1,remote.imageAttempts)
        assertTrue(remote.rows.isEmpty())
        assertEquals("pending",status(id).status)
        assertEquals("pending",status(other,"owner-b").status)
        assertNull(status(id).syncError)
        assertTrue(remote.images.keys.all { it.startsWith("owner-a/$id/") })
        assertImagesRemain(id)
    }
    @Test fun signOutAndBackIntoSameAccountStillCancelsOldWork() = runBlocking {
        val id=report()
        var generation=4
        val remote=FakeRemote().apply { afterImage={ generation=6 } }
        try {
            engine(remote) { if(generation!=4) throw CancellationException("Session replaced") }.sync("owner-a")
            fail("Expected session cancellation")
        } catch(_: CancellationException) { }
        assertEquals(1,remote.imageAttempts)
        assertTrue(remote.rows.isEmpty())
        assertEquals("pending",status(id).status)
    }
    @Test fun failedReportDoesNotBlockHealthyReportsOrSetTheirErrors() = runBlocking {
        report(); report()
        val first=store.reports("owner-a").first().id
        val healthy=store.reports("owner-a").last().id
        val other=report("owner-b")
        val remote=FakeRemote().apply { deniedReports.add(first) }
        assertFalse(engine(remote).sync("owner-a"))
        assertEquals("pending",status(first).status)
        assertTrue(status(first).syncError!!.contains("denied"))
        assertEquals("synced",status(healthy).status)
        assertNull(status(healthy).syncError)
        assertEquals(setOf(healthy),remote.rows.keys)
        assertEquals("pending",status(other,"owner-b").status)
        assertNull(status(other,"owner-b").syncError)
    }
    @Test fun missingCaptureDoesNotBlockAnotherReport() = runBlocking {
        report(); report()
        val first=store.reports("owner-a").first().id
        val healthy=store.reports("owner-a").last().id
        assertTrue(File(store.captures(first).first().path).delete())
        val remote=FakeRemote()
        assertFalse(engine(remote).sync("owner-a"))
        assertEquals("pending",status(first).status)
        assertTrue(status(first).syncError!!.contains("missing"))
        assertEquals("synced",status(healthy).status)
        assertEquals(setOf(healthy),remote.rows.keys)
    }
    @Test fun uploadTimeoutIsRetryableAndDoesNotCancelRemainingReports() = runBlocking {
        report(); report()
        val first=store.reports("owner-a").first().id
        val healthy=store.reports("owner-a").last().id
        val remote=FakeRemote().apply { timeoutReport=first }
        assertFalse(engine(remote).sync("owner-a"))
        assertEquals("pending",status(first).status)
        assertTrue(status(first).syncError!!.startsWith("Upload timed out"))
        assertEquals("synced",status(healthy).status)
    }
    @Test fun workerCancellationNeverAcknowledgesAnUnconfirmedReport() = runBlocking {
        val id=report()
        val remote=FakeRemote().apply { cancelOnReport=true }
        try { engine(remote).sync("owner-a"); fail("Expected worker cancellation") }
        catch(_: CancellationException) { }
        assertEquals("pending",status(id).status)
        assertNull(status(id).syncError)
        assertImagesRemain(id)
    }
    @Test fun syncAcknowledgementCannotChangeDraftOrAnotherOwnersReport() {
        val draft=report(submitted=false)
        val other=report("owner-b")
        store.markSynced(draft,"owner-a")
        store.markSynced(other,"owner-a")
        assertEquals("draft",status(draft).status)
        assertEquals("pending",status(other,"owner-b").status)
    }

    /** Models storage upserts and the database UUID primary key, including lost responses. */
    private class FakeRemote {
        val images=linkedMapOf<String,ByteArray>()
        val rows=linkedMapOf<String,JSONObject>()
        val deniedReports=mutableSetOf<String>()
        var imageAttempts=0
        var reportAttempts=0
        var offline=false
        var loseImageResponseAt:Int?=null
        var loseReportResponseOnce=false
        var cancelOnReport=false
        var timeoutReport:String?=null
        var afterImage:(() -> Unit)?=null
        suspend fun image(path: String, bytes: ByteArray) {
            imageAttempts++
            if(offline) throw IOException("Offline")
            if(path.split('/')[1]==timeoutReport) withTimeout(1) { delay(100) }
            images[path]=bytes.copyOf()
            afterImage?.invoke()
            if(imageAttempts==loseImageResponseAt) throw IOException("Image response lost")
        }
        suspend fun report(payload: String) {
            reportAttempts++
            if(cancelOnReport) throw CancellationException("Work stopped")
            if(offline) throw IOException("Offline")
            val row=JSONObject(payload)
            val id=row.getString("id")
            if(id in deniedReports) throw SyncFailure(403)
            rows[id]=row
            if(loseReportResponseOnce) { loseReportResponseOnce=false; throw IOException("Report response lost") }
        }
    }
}
