package com.example.roadwatch

import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.ui.dashboard.DashboardScreen
import com.example.roadwatch.ui.scan.CaptureImage
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class MapAndOverlayTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun locatedDraftAppearsAsRealMapMarker() {
        val owner="map-test-${System.nanoTime()}"
        val store=ReportStore.get(context)
        val id=store.start(owner)
        store.addCapture(id,byteArrayOf(1),JSONArray(),Location("test").apply { latitude=-1.28; longitude=36.82; accuracy=10f })
        store.finish(id)
        try {
            context.getSharedPreferences("roadwatch_ui",0).edit().putBoolean("intro_v1",true).commit()
            compose.setContent { DashboardScreen(onBack={},owner=owner) }
            compose.onNode(hasText("Map") and hasClickAction()).performClick()
            compose.waitUntil(15000) { compose.onAllNodes(hasText("1 located captures")).fetchSemanticsNodes().isNotEmpty() }
            val markerCount=AtomicReference("null")
            compose.waitUntil(20000) {
                compose.runOnIdle {
                    findWebView(compose.activity.window.decorView)?.evaluateJavascript("typeof markers!=='undefined'?markers.getLayers().length:-1") { markerCount.set(it) }
                }
                markerCount.get()=="1"
            }
            assertEquals("1",markerCount.get())
            compose.runOnIdle { findWebView(compose.activity.window.decorView)?.let { println("NATIVE_MAP=${it.width}x${it.height}") } }
            val viewport=AtomicReference("null")
            try { compose.waitUntil(15000) {
                compose.runOnIdle {
                    findWebView(compose.activity.window.decorView)?.let { web ->
                        web.evaluateJavascript("JSON.stringify({height:document.getElementById('map').getBoundingClientRect().height,viewport:innerHeight,tiles:document.querySelectorAll('.leaflet-tile-loaded').length})") { viewport.set(it) }
                    }
                }
                val decoded=runCatching { org.json.JSONTokener(viewport.get()).nextValue() as? String }.getOrNull()
                decoded!=null && runCatching { org.json.JSONObject(decoded).getDouble("height")>300 }.getOrDefault(false)
            }
            } finally { println("MAP_RENDER="+viewport.get()) }
            // Wait for tiles only for visual QA; lack of internet must not fail offline marker checks.
            val tilesReady=AtomicReference("false")
            try { compose.waitUntil(10000) {
                compose.runOnIdle {
                    findWebView(compose.activity.window.decorView)?.evaluateJavascript("document.querySelectorAll('.leaflet-tile').length>0 && Array.from(document.querySelectorAll('.leaflet-tile')).every(image=>image.complete && image.naturalWidth>0)") { tilesReady.set(it) }
                }
                tilesReady.get()=="true"
            } } catch(_: ComposeTimeoutException) { /* Offline or delayed basemap. */ }
            val painted=java.util.concurrent.atomic.AtomicBoolean(false)
            compose.runOnIdle {
                findWebView(compose.activity.window.decorView)?.postVisualStateCallback(1,object:WebView.VisualStateCallback() {
                    override fun onComplete(id: Long) { painted.set(true) }
                })
            }
            compose.waitUntil(10000) { painted.get() }
            saveScreenshot("qa-map.png")
        } finally {
            store.captures(id).forEach { File(it.path).delete() }
            store.writableDatabase.delete("captures","report_id=?",arrayOf(id))
            store.writableDatabase.delete("reports","id=?",arrayOf(id))
        }
    }
    @Test fun captureReviewShowsConfidenceAndLocationWithOverlay() {
        val store=ReportStore.get(context)
        val owner="overlay-test-${System.nanoTime()}"
        val id=store.start(owner)
        val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(65,75,70)) }
        val bytes=ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it); it.toByteArray() }
        bitmap.recycle()
        store.addCapture(id,bytes,JSONArray("[{\"x\":0.2,\"y\":0.3,\"width\":0.4,\"height\":0.3,\"confidence\":0.9}]"),Location("test").apply { latitude=-1.28; longitude=36.82; accuracy=8f })
        try {
            val capture=store.captures(id).single()
            compose.setContent { Column { CaptureImage(capture) } }
            compose.onNodeWithText("Possible pothole 1 · 90% confidence").assertExists()
            compose.waitUntil(10000) { compose.onAllNodes(hasContentDescription("Road image with 1 possible potholes")).fetchSemanticsNodes().isNotEmpty() }
            saveScreenshot("qa-overlay.png")
        } finally {
            store.captures(id).forEach { File(it.path).delete() }
            store.writableDatabase.delete("captures","report_id=?",arrayOf(id))
            store.writableDatabase.delete("reports","id=?",arrayOf(id))
        }
    }
    private fun findWebView(view: View): WebView? {
        if(view is WebView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
