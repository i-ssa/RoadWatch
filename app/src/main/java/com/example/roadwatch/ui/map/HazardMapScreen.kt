package com.example.roadwatch.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.example.roadwatch.ui.scan.JourneyButton
import com.example.roadwatch.ui.scan.JourneyPill
import com.example.roadwatch.ui.scan.JourneyGlyph
import com.example.roadwatch.ui.scan.JourneySystemBars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import com.example.roadwatch.BuildConfig
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.roadwatch.ui.scan.ScanLocation
import com.google.maps.android.compose.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import org.json.JSONArray

// No map ID: uses the standard native Maps SDK configuration.
internal fun roadWatchMapOptions() = GoogleMapOptions().compassEnabled(false).mapToolbarEnabled(false)

internal data class HazardMarker(val id: String, val position: LatLng, val draft: Boolean, val reporterCount: Int = 0)
internal fun hazardMarkers(points: JSONArray): List<HazardMarker> = buildList {
    for (index in 0 until points.length()) {
        val point = points.optJSONObject(index) ?: continue
        val lat = point.optDouble("lat", Double.NaN)
        val lon = point.optDouble("lon", Double.NaN)
        if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) {
            add(HazardMarker(point.optString("id", index.toString()), LatLng(lat, lon), point.optString("status") == "draft", point.optInt("reporter_count",0)))
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun HazardMapScreen(owner: String, onBack: () -> Unit = {}, onStartScan: () -> Unit = {}, onReports: () -> Unit = {}) {
    val context=LocalContext.current
    val repository=remember(owner) { HazardRepository(context,owner) }
    val scope=rememberCoroutineScope()
    var points by remember(owner) { mutableStateOf(emptyList<HazardMarker>()) }
    val count=points.size
    var message by remember(owner) { mutableStateOf("Loading saved locations…") }
    var refreshing by remember(owner) { mutableStateOf(false) }
    var ready by remember(owner) { mutableStateOf(false) }
    var tilesLoaded by remember(owner) { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    val camera=rememberCameraPositionState { position=CameraPosition.fromLatLngZoom(LatLng(-1.286,36.817),6f) }
    var panelHeight by remember { mutableIntStateOf(0) }
    val density=LocalDensity.current
    val lifecycleOwner=LocalLifecycleOwner.current
    var locationRequest by remember { mutableIntStateOf(0) }
    var locating by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf("Tap My current location to center the map.") }
    var currentPosition by remember { mutableStateOf<LatLng?>(null) }
    fun hasLocationPermission() = listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED
    }
    val permissionRequest=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(hasLocationPermission()) locationRequest++
        else locationMessage="Location access is off. Tap My current location to retry, or allow access in Settings."
    }
    fun locate() {
        if(hasLocationPermission()) locationRequest++
        else permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(ready) {
        if(ready && hasLocationPermission()) locate()
    }
    LaunchedEffect(locationRequest,ready) {
        if(locationRequest==0 || !ready || !hasLocationPermission()) return@LaunchedEffect
        var completed=false
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if(completed) return@repeatOnLifecycle
            locating=true
            locationMessage="Finding your location…"
            val tracker=ScanLocation(context)
            try {
                tracker.start()
                var fix: android.location.Location?=null
                repeat(60) { if(fix==null) { fix=tracker.current(); if(fix==null) delay(500) } }
                completed=true
                fix?.let {
                    val position=LatLng(it.latitude,it.longitude)
                    currentPosition=position
                    locationMessage="Showing your last location fix"
                    camera.animate(CameraUpdateFactory.newLatLngZoom(position,17f))
                    locationMessage="Centered on your location"
                } ?: run { locationMessage="Couldn’t find your location. Check device location and tap My current location to retry." }
            } finally { tracker.close(); locating=false }
        }
    }
    fun zoomBy(amount: Float) { if(ready) camera.move(CameraUpdateFactory.zoomBy(amount)) }
    fun refresh() {
        if(refreshing) return
        refreshing=true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repository.refresh() }
                message="Cloud reports updated · "+DateFormat.getTimeInstance().format(Date())
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { message="Cloud unavailable. Showing saved locations; map tiles need internet." }
            finally { refreshing=false }
        }
    }
    LaunchedEffect(owner) {
        val updated=withContext(Dispatchers.IO) { repository.updatedAt() }
        message=if(updated>0) "Cached cloud reports · "+DateFormat.getDateTimeInstance().format(Date(updated)) else "Showing locations saved on this device"
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastRefresh=0L
            while(true) {
                if(android.os.SystemClock.elapsedRealtime()-lastRefresh>=15_000 || lastRefresh==0L) {
                    refresh()
                    lastRefresh=android.os.SystemClock.elapsedRealtime()
                }
                points=withContext(Dispatchers.IO) { hazardMarkers(repository.points()) }
                delay(3000)
            }
        }
    }

    JourneySystemBars(darkIcons=true)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("journey-map")) {
        val compact=maxHeight<500.dp
        if(BuildConfig.MAPS_CONFIGURED) {
            GoogleMap(
                modifier=Modifier.fillMaxSize().testTag("native-google-map").semantics { stateDescription=if(tilesLoaded) "Map loaded" else "Map loading" },
                cameraPositionState=camera,
                googleMapOptionsFactory=::roadWatchMapOptions,
                contentDescription=if(tilesLoaded) "Road hazard map, loaded" else "Road hazard map, loading",
                onMapLoaded={ tilesLoaded=true },
                properties=MapProperties(minZoomPreference=2f,maxZoomPreference=20f),
                uiSettings=MapUiSettings(zoomControlsEnabled=false,compassEnabled=false,mapToolbarEnabled=false,rotationGesturesEnabled=false,tiltGesturesEnabled=false),
                contentPadding=PaddingValues(
                    start=if(compact) 380.dp else 20.dp,
                    top=100.dp,end=84.dp,
                    bottom=if(compact) 32.dp else with(density) { panelHeight.toDp() }+48.dp
                )
            ) {
                MapEffect(owner) { ready=true }
                currentPosition?.let { position ->
                    Marker(state=rememberUpdatedMarkerState(position=position),title="Your location (last fix)",
                        icon=BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                }
                points.forEach { point ->
                    key(point.id) {
                        Marker(
                            state=rememberUpdatedMarkerState(position=point.position),
                            title=if(point.draft) "Draft · possible pothole" else "Submitted · possible pothole",
                            snippet=if(point.draft) "Visible only to you" else "Reported by ${point.reporterCount} user(s) · unverified",
                            icon=BitmapDescriptorFactory.defaultMarker(if(point.draft) BitmapDescriptorFactory.HUE_ORANGE else BitmapDescriptorFactory.HUE_AZURE)
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal=32.dp).padding(top=170.dp,bottom=360.dp),contentAlignment=Alignment.Center) {
                Text("The map is not available in this build. Your saved reports are still available.",modifier=Modifier.testTag("map-setup-required"),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            JourneyButton("Back to Home","back",onBack,dark=dark)
            JourneyPill("Community road map",dark=dark)
            JourneyButton("Map information","info",{showInfo=true},dark=dark)
        }
        Column(Modifier.align(if(compact) Alignment.CenterEnd else Alignment.TopEnd).then(if(compact) Modifier.padding(end=20.dp) else Modifier.statusBarsPadding().padding(top=100.dp,end=20.dp)),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            JourneyButton("Zoom in","plus",{zoomBy(1f)},enabled=ready,dark=dark)
            JourneyButton("Zoom out","minus",{zoomBy(-1f)},enabled=ready,dark=dark)
            JourneyButton("Refresh cloud reports","refresh",{refresh()},enabled=!refreshing,dark=dark)
            JourneyButton("My current location","locate",{locate()},enabled=ready && !locating,dark=dark)
        }
        Surface(Modifier.onSizeChanged { panelHeight=it.height }.align(if(compact) Alignment.BottomStart else Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).widthIn(max=if(compact) 360.dp else 480.dp).fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.98f),contentColor=MaterialTheme.colorScheme.onSurface,shadowElevation=12.dp) {
            Column(Modifier.padding(if(compact) 18.dp else 22.dp),verticalArrangement=Arrangement.spacedBy(if(compact) 10.dp else 14.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Road insights",fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                        Text(when(count) {
                            0 -> "No mapped reports yet"
                            1 -> "1 mapped location"
                            else -> "$count mapped locations"
                        },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    JourneyPill(if(refreshing) "Updating" else if(message.startsWith("Cloud unavailable")) "Saved data" else "Community",dark=dark)
                }
                if(ready) {
                    Text(locationMessage,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(count>0) Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(Color(0xFFE65100),CircleShape));Spacer(Modifier.width(7.dp));Text("Draft",fontSize=12.sp) }
                    Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(Color(0xFF1565C0),CircleShape));Spacer(Modifier.width(7.dp));Text("Submitted",fontSize=12.sp) }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(onClick=onStartScan,modifier=Modifier.weight(1f).heightIn(min=54.dp),shape=RoundedCornerShape(17.dp)) {
                        JourneyGlyph("scan",MaterialTheme.colorScheme.onPrimary);Spacer(Modifier.width(8.dp));Text("Scan")
                    }
                    OutlinedButton(onClick=onReports,modifier=Modifier.heightIn(min=54.dp),shape=RoundedCornerShape(17.dp)) { Text("Reports") }
                }
            }
        }
    }
    if(showInfo) AlertDialog(onDismissRequest={showInfo=false},title={Text("Community hazard map")},text={Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(ready) Text(locationMessage)
        Text("$message\n\nOrange markers show your private drafts and pending uploads. Blue markers show possible potholes shared with signed-in users. Green marks your last location fix. Marker positions are estimates.\n\nPinch to zoom. Google Maps needs an internet connection and may show local place names. The latest 500 shared hazards refresh every 15 seconds while the map is open.")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled=ready,onClick={zoomBy(-1f)}) { Text("Zoom out") }
            OutlinedButton(enabled=ready,onClick={zoomBy(1f)}) { Text("Zoom in") }
        }
    }},confirmButton={TextButton(onClick={showInfo=false}) { Text("Got it") }})
}
