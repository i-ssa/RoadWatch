package com.example.roadwatch

import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.MapView
import com.example.roadwatch.ui.map.hazardMarkers
import com.example.roadwatch.ui.map.roadWatchMapOptions
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
    @Test fun mapConfigurationUsesNoMapId() {
        assertNull(roadWatchMapOptions().mapId)
    }
    @Test fun mapLanguageUsesEnglishWithoutChangingDeviceConfiguration() {
        val configuration=android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(java.util.Locale("ar"))
        val arabic=context.createConfigurationContext(configuration)
        val english=arabic.englishContext()
        assertEquals("en",english.resources.configuration.locales[0].language)
        assertEquals("ar",arabic.resources.configuration.locales[0].language)
    }
    @Test fun invalidCaptureCoordinatesAreExcludedFromNativeMarkers() {
        val markers=hazardMarkers(JSONArray("[{\"id\":\"valid\",\"lat\":-1.28,\"lon\":36.82,\"status\":\"draft\"},{\"lat\":91,\"lon\":36},{\"lat\":0,\"lon\":181},{}]"))
        assertEquals(1,markers.size)
        assertEquals("valid",markers.single().id)
        assertTrue(markers.single().draft)
        assertEquals(-1.28,markers.single().position.latitude,0.0)
    }
    @Test fun missingKeyKeepsReportsAndScanAccessible() {
        org.junit.Assume.assumeFalse(BuildConfig.MAPS_CONFIGURED)
        compose.setContent { com.example.roadwatch.ui.map.HazardMapScreen(owner="map-setup-test") }
        compose.onNodeWithTag("map-setup-required").assertExists()
        compose.onNodeWithText("Scan").assertHasClickAction()
        compose.onNodeWithText("Reports").assertHasClickAction()
        compose.onNodeWithContentDescription("Zoom in").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Zoom out").assertIsNotEnabled()
    }
    @Test fun openingCentersOnFreshLocationAndTargetReturnsAfterBrowsing() {
        org.junit.Assume.assumeTrue(BuildConfig.MAPS_CONFIGURED)
        org.junit.Assume.assumeTrue(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName,android.Manifest.permission.ACCESS_FINE_LOCATION)
        automation.grantRuntimePermission(context.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("appops set ${context.packageName} android:mock_location allow")).bufferedReader().use { it.readText() }
        val manager=context.getSystemService(android.location.LocationManager::class.java)
        val executor=java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        val map=AtomicReference<com.google.android.gms.maps.GoogleMap?>(null)
        val longitude=java.util.concurrent.atomic.AtomicReference(36.817223)
        try {
            @Suppress("DEPRECATION")
            manager.addTestProvider("gps",false,false,false,false,true,true,true,1,1)
            manager.setTestProviderEnabled("gps",true)
            executor.scheduleAtFixedRate({
                manager.setTestProviderLocation("gps",Location("gps").apply {
                    latitude=-1.286389; this.longitude=longitude.get(); accuracy=5f
                    time=System.currentTimeMillis(); elapsedRealtimeNanos=android.os.SystemClock.elapsedRealtimeNanos()
                })
            },0,500,java.util.concurrent.TimeUnit.MILLISECONDS)
            compose.setContent { com.example.roadwatch.ui.map.HazardMapScreen(owner="location-map-test") }
            compose.waitUntil(30000) {
                compose.runOnIdle { findMapView(compose.activity.window.decorView)?.getMapAsync { map.set(it) } }
                map.get()!=null
            }
            fun centeredAt(lon: Double): Boolean {
                var centered=false
                compose.runOnIdle { map.get()?.cameraPosition?.let {
                    centered=kotlin.math.abs(it.target.latitude+1.286389)<0.0001 && kotlin.math.abs(it.target.longitude-lon)<0.0001 && it.zoom>=16.5f
                } }
                return centered
            }
            compose.waitUntil(40000) { centeredAt(36.817223) }
            compose.runOnIdle { map.get()!!.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(com.google.android.gms.maps.model.LatLng(-4.0,39.0),8f)) }
            // Move the fresh fix: recenter must request the new position rather than reuse the old one.
            longitude.set(36.827223)
            compose.onNodeWithContentDescription("My current location").performClick()
            compose.waitUntil(40000) { centeredAt(36.827223) }
            saveScreenshot("qa-location-recenter.png")
        } finally {
            executor.shutdownNow()
            runCatching { manager.removeTestProvider("gps") }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("appops set ${context.packageName} android:mock_location default")).bufferedReader().use { it.readText() }
        }
    }
    @Test fun locatedDraftAppearsOnNativeMapAndZoomWorks() {
        // Live map authentication requires the owner's restricted Google Maps key.
        org.junit.Assume.assumeTrue("Set MAPS_API_KEY before live Google Maps verification",BuildConfig.MAPS_CONFIGURED)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName,android.Manifest.permission.ACCESS_FINE_LOCATION)
        automation.grantRuntimePermission(context.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val owner="map-test-${System.nanoTime()}"
        val store=ReportStore.get(context)
        val id=store.start(owner)
        store.addCapture(id,byteArrayOf(1),JSONArray(),Location("test").apply { latitude=-1.28; longitude=36.82; accuracy=10f })
        store.finish(id)
        val map=AtomicReference<com.google.android.gms.maps.GoogleMap?>(null)
        try {
            compose.setContent { com.example.roadwatch.ui.map.HazardMapScreen(owner=owner) }
            compose.waitUntil(15000) { compose.onAllNodesWithText("1 mapped location").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(60000) { compose.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Map loaded")).fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(30000) {
                compose.runOnIdle { findMapView(compose.activity.window.decorView)?.getMapAsync { map.set(it) } }
                map.get()!=null
            }
            var zoom=0f
            compose.runOnIdle { zoom=map.get()!!.cameraPosition.zoom }
            compose.onNodeWithContentDescription("Zoom in").performClick()
            compose.runOnIdle { assertEquals(zoom+1f,map.get()!!.cameraPosition.zoom,0.01f) }
            compose.onNodeWithContentDescription("Zoom out").performClick()
            compose.runOnIdle { assertEquals(zoom,map.get()!!.cameraPosition.zoom,0.01f) }
            val geometry=IntArray(4)
            compose.runOnIdle {
                val view=requireNotNull(findMapView(compose.activity.window.decorView))
                view.getLocationOnScreen(geometry);geometry[2]=view.width;geometry[3]=view.height
            }
            injectMapPinch(geometry)
            compose.waitUntil(5000) {
                var enlarged=false
                compose.runOnIdle { enlarged=map.get()!!.cameraPosition.zoom>zoom+.1f }
                enlarged
            }
            saveScreenshot("qa-google-map.png")
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
    private fun injectMapPinch(bounds: IntArray) {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val down=android.os.SystemClock.uptimeMillis()
        val properties=Array(2) { index -> android.view.MotionEvent.PointerProperties().apply {
            id=index; toolType=android.view.MotionEvent.TOOL_TYPE_FINGER
        } }
        fun send(action: Int, fraction: Float, count: Int=2) {
            val coords=Array(count) { index -> android.view.MotionEvent.PointerCoords().apply {
                x=bounds[0]+bounds[2]*(if(index==0) .4f-.15f*fraction else .6f+.15f*fraction)
                y=bounds[1]+bounds[3]*.32f
                pressure=1f; size=1f
            } }
            val event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,count,properties,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
            try { check(automation.injectInputEvent(event,true)) } finally { event.recycle() }
        }
        send(android.view.MotionEvent.ACTION_DOWN,0f,1)
        send(android.view.MotionEvent.ACTION_POINTER_DOWN or (1 shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT),0f)
        for(step in 1..30) {
            android.os.SystemClock.sleep(20)
            send(android.view.MotionEvent.ACTION_MOVE,step/30f)
        }
        send(android.view.MotionEvent.ACTION_POINTER_UP or (1 shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT),1f)
        send(android.view.MotionEvent.ACTION_UP,1f,1)
    }
    private fun findMapView(view: View): MapView? {
        if(view is MapView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) findMapView(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
