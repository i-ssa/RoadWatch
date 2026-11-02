package com.example.roadwatch

import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.ui.map.HazardMapScreen
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
            compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { HazardMapScreen(owner) } }
            compose.waitUntil(15000) { compose.onAllNodes(hasText("1 located captures · latest 500 cloud reports")).fetchSemanticsNodes().isNotEmpty() }
            val markerCount=AtomicReference("null")
            compose.waitUntil(20000) {
                compose.runOnIdle {
                    findWebView(compose.activity.window.decorView)?.evaluateJavascript("typeof markers!=='undefined'?markers.getLayers().length:-1") { markerCount.set(it) }
                }
                markerCount.get()=="1"
            }
            assertEquals("1",markerCount.get())
            val painted=java.util.concurrent.atomic.AtomicBoolean(false)
            compose.runOnIdle {
                findWebView(compose.activity.window.decorView)?.let { web ->
                    web.evaluateJavascript("JSON.stringify({size:map.getSize(),rect:document.getElementById('map').getBoundingClientRect().toJSON(),tiles:document.querySelectorAll('.leaflet-tile-loaded').length})") { println("MAP_RENDER=$it") }
                    web.postVisualStateCallback(1,object:WebView.VisualStateCallback() {
                        override fun onComplete(id: Long) { painted.set(true) }
                    })
                }
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
