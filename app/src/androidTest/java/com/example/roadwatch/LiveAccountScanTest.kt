package com.example.roadwatch

import android.Manifest
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.data.auth.RoadWatchBackend
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.data.reports.ReportSyncWorker
import com.example.roadwatch.ui.scan.ScanScreen
import io.github.jan.supabase.auth.auth
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in acceptance checks. Creates one real emulator camera scan in the signed-in account. */
class LiveAccountScanTest {
    @get:Rule val compose=createComposeRule()
    private val arguments get()=InstrumentationRegistry.getArguments()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun recordOfflineScan() = runBlocking {
        assumeTrue(arguments.getString("liveAccount")=="true")
        RoadWatchBackend.restore()
        val owner=checkNotNull(RoadWatchBackend.client?.auth?.currentUserOrNull()?.id) { "Sign in to RoadWatch first" }
        val store=ReportStore.get(context)
        val previous=store.reports(owner).map { it.id }.toSet()
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.CAMERA)
        var finished=false
        compose.setContent { ScanScreen(owner) { finished=true } }
        compose.waitUntil(45000) { store.reports(owner).any { it.id !in previous } }
        // Allow the first inference to complete before stopping the camera.
        compose.waitUntil(45000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Looking for possible potholes") or androidx.compose.ui.test.hasText("Possible potholes saved for review")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Stop & review").performClick()
        compose.waitUntil(15000) { finished }
        val report=store.reports(owner).single { it.id !in previous }
        assertEquals("draft",report.status)
        println("LIVE_REPORT_ID=${report.id}")
        println("LIVE_CAPTURE_COUNT=${report.detections}")
    }
    @Test fun syncRecordedScan() = runBlocking {
        assumeTrue(arguments.getString("liveAccount")=="true")
        val id=checkNotNull(arguments.getString("reportId"))
        RoadWatchBackend.restore()
        val client=checkNotNull(RoadWatchBackend.client)
        val owner=checkNotNull(client.auth.currentUserOrNull()?.id)
        val store=ReportStore.get(context)
        val localReport=store.reports(owner).single { it.id==id }
        when(localReport.status) {
            "draft" -> assertTrue(store.submit(id,owner))
            "pending", "synced" -> Unit // Resume the same acceptance run after interruption.
            else -> error("Stop and review this scan before testing submission")
        }
        val localCaptures=store.captures(id).associateBy { it.id }
        ReportSyncWorker.schedule(context,owner)
        compose.waitUntil(120000) { store.reports(owner).any { it.id==id && it.status=="synced" } }
        val response=client.httpClient.get("${BuildConfig.SUPABASE_URL}/rest/v1/road_reports") {
            header("apikey",BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            bearerAuth(checkNotNull(client.auth.currentAccessTokenOrNull()))
            parameter("id","eq.$id"); parameter("select","id,captures")
        }
        assertEquals(200,response.status.value)
        val rows=JSONArray(response.bodyAsText())
        assertEquals(1,rows.length())
        assertEquals(id,rows.getJSONObject(0).getString("id"))
        val captures=rows.getJSONObject(0).getJSONArray("captures")
        assertEquals(localCaptures.size,captures.length())
        val uploadedIds=(0 until captures.length()).map { captures.getJSONObject(it).getString("id") }
        assertEquals(uploadedIds.size,uploadedIds.toSet().size)
        assertEquals(localCaptures.keys,uploadedIds.toSet())
        for(index in 0 until captures.length()) {
            val remoteCapture=captures.getJSONObject(index)
            val captureId=remoteCapture.getString("id")
            val local=localCaptures.getValue(captureId)
            val path=remoteCapture.getString("image_path")
            assertEquals("$owner/$id/$captureId.jpg",path)
            assertEquals(local.capturedAt,remoteCapture.getLong("captured_at_ms"))
            assertEquals(JSONArray(local.boxes).toString(),remoteCapture.getJSONArray("boxes").toString())
            if(local.latitude==null) assertTrue(remoteCapture.isNull("latitude"))
            else assertEquals(local.latitude!!,remoteCapture.getDouble("latitude"),0.0000001)
            if(local.longitude==null) assertTrue(remoteCapture.isNull("longitude"))
            else assertEquals(local.longitude!!,remoteCapture.getDouble("longitude"),0.0000001)
            val image=client.httpClient.get("${BuildConfig.SUPABASE_URL}/storage/v1/object/authenticated/roadwatch-captures/$path") {
                header("apikey",BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                bearerAuth(checkNotNull(client.auth.currentAccessTokenOrNull()))
            }
            assertEquals(200,image.status.value)
            val digest=java.security.MessageDigest.getInstance("SHA-256")
            assertArrayEquals(digest.digest(java.io.File(local.path).readBytes()),digest.digest(image.body<ByteArray>()))
        }
        println("LIVE_SYNC_VERIFIED=true; CAPTURES=${captures.length()}")
    }
}
