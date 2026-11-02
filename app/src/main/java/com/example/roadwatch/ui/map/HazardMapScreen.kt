package com.example.roadwatch.ui.map

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.layout.*
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
fun HazardMapScreen(owner: String) {
    val context=LocalContext.current
    val repository=remember(owner) { HazardRepository(context,owner) }
    val scope=rememberCoroutineScope()
    var data by remember(owner) { mutableStateOf("[]") }
    var count by remember(owner) { mutableIntStateOf(0) }
    var message by remember(owner) { mutableStateOf("Loading saved locations…") }
    var refreshing by remember(owner) { mutableStateOf(false) }
    var ready by remember(owner) { mutableStateOf(false) }
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
    Text("Your hazard map",style=MaterialTheme.typography.headlineSmall)
    Text("Possible potholes from your account. Orange: draft. Blue: submitted. Locations are phone estimates, not verified defects.")
    Text(message)
    TextButton(enabled=!refreshing,onClick={refresh()}) { Text(if(refreshing) "Refreshing…" else "Refresh cloud reports") }
    Text("$count located captures · latest 500 cloud reports")
    if(count==0) Text("No locations yet. Enable location during a scan and wait for a GPS fix. Older captures have no coordinates.")
    AndroidView(modifier=Modifier.fillMaxWidth().height(360.dp),factory={
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
    Text("© OpenStreetMap contributors · basemap requires internet. Reports remain available offline in Detections.")
    DisposableEffect(owner) { onDispose { browser?.apply { stopLoading(); destroy() }; browser=null } }
}

private fun mapHtml(css: String, js: String)="""
<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src https://tile.openstreetmap.org data:;">
<style>$css html,body,#map{height:100%;margin:0}#status{position:absolute;top:8px;right:8px;background:white;padding:6px;z-index:1000;font:12px sans-serif;max-width:60%}</style>
</head><body><div id="map" role="img" aria-label="Map of your possible pothole captures"></div><div id="status">Pan and zoom to explore</div><script>$js</script><script>
const map=L.map('map').setView([0,0],2), markers=L.layerGroup().addTo(map);let centered=false;
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).on('tileerror',()=>{document.getElementById('status').textContent='Map tiles unavailable · saved markers still shown'}).addTo(map);
function updatePoints(points){markers.clearLayers();const bounds=[];for(const p of points){if(!Number.isFinite(p.lat)||!Number.isFinite(p.lon))continue;
L.circleMarker([p.lat,p.lon],{radius:7,color:p.status==='draft'?'#e65100':'#1565c0',fillOpacity:0.8}).bindPopup(p.status==='draft'?'Draft · possible pothole':'Submitted · possible pothole').addTo(markers);bounds.push([p.lat,p.lon]);}
if(bounds.length&&!centered){map.fitBounds(bounds,{padding:[30,30],maxZoom:16});centered=true;}}
</script></body></html>
""".trimIndent()
