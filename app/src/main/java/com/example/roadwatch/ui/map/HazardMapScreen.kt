package com.example.roadwatch.ui.map

import android.annotation.SuppressLint
import android.webkit.*
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
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.text.DateFormat
import java.util.Date

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun HazardMapScreen(owner: String, onBack: () -> Unit = {}, onStartScan: () -> Unit = {}, onReports: () -> Unit = {}) {
    val context=LocalContext.current
    val repository=remember(owner) { HazardRepository(context,owner) }
    val scope=rememberCoroutineScope()
    var data by remember(owner) { mutableStateOf("[]") }
    var count by remember(owner) { mutableIntStateOf(0) }
    var message by remember(owner) { mutableStateOf("Loading saved locations…") }
    var refreshing by remember(owner) { mutableStateOf(false) }
    var ready by remember(owner) { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    var browser by remember(owner) { mutableStateOf<WebView?>(null) }
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
        refresh()
        while(true) {
            val points=withContext(Dispatchers.IO) { repository.points() }
            data=points.toString(); count=points.length()
            delay(3000)
        }
    }
    LaunchedEffect(data,ready) { if(ready) browser?.evaluateJavascript("updatePoints($data)",null) }
    JourneySystemBars(darkIcons=true)
    fun mapAction(script: String) { if(ready) browser?.evaluateJavascript(script,null) }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("journey-map")) {
        val compact=maxHeight<500.dp
    AndroidView(modifier=Modifier.fillMaxSize(),factory={
        WebView(it).apply {
            browser=this
            settings.javaScriptEnabled=true
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString="RoadWatch/1.0 (+https://github.com/i-ssa/RoadWatch)"
            webViewClient=object: WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { ready=true }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest)=true
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    return if(request.url.scheme=="https" && request.url.host=="tile.openstreetmap.org") null
                    else WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(byteArrayOf()))
                }
            }
            setOnTouchListener { view,_ -> view.parent.requestDisallowInterceptTouchEvent(true); false }
            val css=context.assets.open("map/leaflet.css").bufferedReader().use { reader -> reader.readText() }
            val js=context.assets.open("map/leaflet.js").bufferedReader().use { reader -> reader.readText() }
            loadDataWithBaseURL("https://roadwatch.local/",mapHtml(css,js),"text/html","UTF-8",null)
        }
    })
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            JourneyButton("Back to Home","back",onBack,dark=dark)
            JourneyPill("Your road map",dark=dark)
            JourneyButton("Map information","info",{showInfo=true},dark=dark)
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            JourneyButton("Refresh cloud reports","refresh",{refresh()},enabled=!refreshing,dark=dark)
            JourneyButton("Zoom in","plus",{mapAction("map.zoomIn()")},enabled=ready,dark=dark)
            JourneyButton("Zoom out","minus",{mapAction("map.zoomOut()")},enabled=ready,dark=dark)
            JourneyButton("Show all captures","locate",{mapAction("fitCaptures()")},enabled=ready && count>0,dark=dark)
        }
        Surface(Modifier.align(if(compact) Alignment.BottomStart else Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).widthIn(max=if(compact) 360.dp else 480.dp).fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.98f),contentColor=MaterialTheme.colorScheme.onSurface,shadowElevation=12.dp) {
            Column(Modifier.padding(if(compact) 18.dp else 22.dp),verticalArrangement=Arrangement.spacedBy(if(compact) 10.dp else 14.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Road insights",fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                        Text(if(count==0) "Your first mapped scan starts here" else "$count located captures",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    JourneyPill(if(refreshing) "Updating" else if(message.startsWith("Cloud unavailable")) "Saved data" else "Your account",dark=dark)
                }
                if(count==0 && !compact) Text("Enable location during a scan to place captures on your map.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                else if(count>0) Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(Color(0xFFE65100),CircleShape));Spacer(Modifier.width(7.dp));Text("Draft",fontSize=12.sp) }
                    Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(Color(0xFF1565C0),CircleShape));Spacer(Modifier.width(7.dp));Text("Submitted",fontSize=12.sp) }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(onClick=onStartScan,modifier=Modifier.weight(1f).heightIn(min=54.dp),shape=RoundedCornerShape(17.dp)) {
                        JourneyGlyph("scan",MaterialTheme.colorScheme.onPrimary);Spacer(Modifier.width(8.dp));Text("Scan")
                    }
                    OutlinedButton(onClick=onReports,modifier=Modifier.heightIn(min=54.dp),shape=RoundedCornerShape(17.dp)) { Text("Reports") }
                }
                Text("© OpenStreetMap contributors",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if(showInfo) AlertDialog(onDismissRequest={showInfo=false},title={Text("Your hazard map")},text={Text("$message\n\nPossible potholes from your private account. Orange markers are drafts; blue markers are submitted. Locations are phone estimates, not verified defects. Refresh includes the latest 500 cloud reports. Map tiles need internet; saved reports remain available offline.")},confirmButton={TextButton(onClick={showInfo=false}) { Text("Got it") }})
    DisposableEffect(owner) { onDispose { browser?.apply { stopLoading(); destroy() }; browser=null } }
}

private fun mapHtml(css: String, js: String)="""
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src https://tile.openstreetmap.org data:;">
<style>$css html,body,#map{height:100%;margin:0}#status{position:absolute;top:112px;left:20px;background:#fff;padding:0 10px;border-radius:20px;z-index:1000;font:12px/32px sans-serif;max-width:65%} .leaflet-container{background:#e9eee9} .leaflet-popup-content-wrapper{border-radius:16px}</style>
</head><body><div id="map" role="img" aria-label="Map of your possible pothole captures"></div><div id="status"></div><script>$js</script><script>
const map=L.map('map',{zoomControl:false,attributionControl:false}).setView([0,0],2), markers=L.layerGroup().addTo(map);let centered=false;let captureBounds=[];
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).on('tileerror',()=>{document.getElementById('status').textContent='Map tiles unavailable · saved markers shown'}).addTo(map);
function updatePoints(points){markers.clearLayers();const bounds=[];for(const p of points){if(!Number.isFinite(p.lat)||!Number.isFinite(p.lon))continue;
L.circleMarker([p.lat,p.lon],{radius:7,color:p.status==='draft'?'#e65100':'#1565c0',fillOpacity:0.8}).bindPopup(p.status==='draft'?'Draft · possible pothole':'Submitted · possible pothole').addTo(markers);bounds.push([p.lat,p.lon]);}
captureBounds=bounds;if(bounds.length&&!centered){fitCaptures();centered=true;}}
function fitCaptures(){if(captureBounds.length){map.invalidateSize();const wide=window.innerWidth>window.innerHeight;map.fitBounds(captureBounds,{paddingTopLeft:wide?[380,100]:[40,110],paddingBottomRight:wide?[90,40]:[90,Math.min(310,map.getSize().y*.42)],maxZoom:16});}}
window.addEventListener('resize',()=>map.invalidateSize());
</script></body></html>
""".trimIndent()
