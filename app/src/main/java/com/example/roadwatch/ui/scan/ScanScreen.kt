package com.example.roadwatch.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.roadwatch.data.reports.ReportStore
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScanScreen(owner: String, onFinished: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission=it }
    var locationEnabled by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) }
    val locationRequest=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationEnabled=it.values.any { granted -> granted }
    }
    val locations=remember { ScanLocation(context) }
    DisposableEffect(locationEnabled) {
        if(locationEnabled) locations.start()
        onDispose { locations.close() }
    }
    var latency by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf("Loading pothole detector…") }
    var count by remember { mutableIntStateOf(0) }
    var stopping by remember { mutableStateOf(false) }
    var stop by remember { mutableStateOf<(() -> Unit)?>(null) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var showInfo by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val paused=message.startsWith("Scanning paused") || message.startsWith("Camera unavailable")
    JourneySystemBars(darkIcons=false)
    LaunchedEffect(permission,stopping,paused) {
        if(permission && !stopping && !paused) while(true) { delay(1000); elapsed++ }
    }
    val preview = remember { PreviewView(context).apply { scaleType=PreviewView.ScaleType.FILL_CENTER } }
    BackHandler { if(!stopping) { if(stop != null) { stopping=true; stop?.invoke() } else onFinished() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF101813))) {
        val compact=maxHeight<500.dp
        if(permission) AndroidView(factory={preview},modifier=Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.55f),Color.Transparent,Color.Transparent,Color.Black.copy(alpha=.6f)))))
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            JourneyButton("End scan and review","back",{ if(!stopping) { if(stop!=null) { stopping=true;stop?.invoke() } else onFinished() } },enabled=!stopping,dark=true)
            JourneyPill(if(!permission) "Road scan" else if(stopping) "Saving…" else if(paused) "Paused" else "%02d:%02d · Scanning".format(elapsed/60,elapsed%60),dark=true,indicator=if(paused) Color(0xFFFFC76F) else if(permission) Color(0xFFA9DFB8) else null)
            JourneyButton("Scan information","info",{showInfo=true},dark=true)
        }
        if(permission) Column(Modifier.align(if(compact) Alignment.CenterEnd else Alignment.TopEnd).then(if(compact) Modifier.padding(end=20.dp) else Modifier.statusBarsPadding().padding(top=100.dp,end=20.dp)),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            JourneyButton(if(locationEnabled) "Capture location information" else "Enable capture location","locate",{if(locationEnabled) showInfo=true else locationRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},dark=true)
            if(camera?.cameraInfo?.hasFlashUnit()==true) JourneyButton(if(torch) "Turn torch off" else "Turn torch on","flash",{torch=!torch;camera?.cameraControl?.enableTorch(torch)},dark=true,active=torch)
        }
        Surface(Modifier.align(if(compact) Alignment.BottomStart else Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).widthIn(max=if(compact) 360.dp else 480.dp).fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=Color(0xF51B2921),contentColor=Color.White) {
            Column(Modifier.padding(if(compact) 18.dp else 22.dp),verticalArrangement=Arrangement.spacedBy(if(compact) 12.dp else 16.dp)) {
                if(permission) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if(paused) "Scan paused" else "Capturing road hazards",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                            Text(if(message.startsWith("Scanning paused") || message.startsWith("Camera unavailable")) message else if(count>0) "Possible potholes saved for review" else if(latency==0L) "Connecting camera…" else "Looking for possible potholes",fontSize=13.sp,color=Color(0xFFB9C8BD))
                        }
                        Column(horizontalAlignment=Alignment.End) {
                            Text("$count",fontSize=28.sp,fontWeight=FontWeight.SemiBold)
                            Text(if(count==1) "capture" else "captures",fontSize=11.sp,color=Color(0xFFB9C8BD))
                        }
                    }
                    Button(onClick={stopping=true;stop?.invoke()},enabled=!stopping && stop!=null,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFDBF0DF),contentColor=Color(0xFF133721))) {
                        JourneyGlyph("stop",Color(0xFF133721));Spacer(Modifier.width(10.dp));Text(if(stopping) "Saving…" else "Stop & review")
                    }
                    if(!compact) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("Saved as a private draft",fontSize=11.sp,color=Color(0xFFB9C8BD))
                        Text(if(locationEnabled) "Location enabled" else "Location off",fontSize=11.sp,color=Color(0xFFB9C8BD))
                    }
                } else {
                    Text("Camera access needed",fontSize=24.sp,fontWeight=FontWeight.SemiBold)
                    Text("Allow the camera to scan for possible potholes. Set up your phone while parked.",color=Color(0xFFB9C8BD))
                    Button(onClick={request.launch(Manifest.permission.CAMERA)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFDBF0DF),contentColor=Color(0xFF133721))) { Text("Allow camera") }
                }
            }
        }
    }
    if(showInfo) AlertDialog(onDismissRequest={showInfo=false},title={Text("About this scan")},text={Text("Mount your phone while parked. Scans work offline and save as private drafts. Check captures before submitting. Enable location if you want to attach it to new captures.")},confirmButton={TextButton(onClick={showInfo=false}) { Text("Got it") }})
    if(permission) DisposableEffect(owner,lifecycle) {
        val executor = Executors.newSingleThreadExecutor()
        val active = AtomicBoolean(true)
        val completed = AtomicBoolean(false)
        val store = ReportStore.get(context)
        var reportId: String? = null
        var detector: PotholeDetector? = null
        var provider: ProcessCameraProvider? = null
        val main = ContextCompat.getMainExecutor(context)
        fun finish(navigate: Boolean) {
            if(completed.compareAndSet(false,true)) {
                active.set(false)
                provider?.unbindAll()
                executor.execute {
                    reportId?.let { store.finish(it) }
                    detector?.close()
                    main.execute { if(navigate) onFinished() }
                }
                executor.shutdown()
            }
        }
        stop = { finish(true) }
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_STOP) finish(true)
        }
        lifecycle.lifecycle.addObserver(observer)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if(active.get()) try {
                provider=future.get()
                val cameraPreview = Preview.Builder().build().apply { surfaceProvider=preview.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                var lastFrame=0L
                analysis.setAnalyzer(executor) { image ->
                    try {
                        if(active.get() && android.os.SystemClock.elapsedRealtime()-lastFrame>=2000) {
                            lastFrame=android.os.SystemClock.elapsedRealtime()
                            if(detector==null) detector=PotholeDetector(context)
                            if(reportId==null) reportId=store.start(owner)
                            val raw=image.toBitmap()
                            val matrix=Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                            val bitmap=Bitmap.createBitmap(raw,0,0,raw.width,raw.height,matrix,true)
                            try {
                                val location=locations.current()
                                val started=android.os.SystemClock.elapsedRealtime()
                                val boxes=detector!!.detect(bitmap)
                                val elapsed=android.os.SystemClock.elapsedRealtime()-started
                                main.execute { latency=elapsed }
                                if(boxes.isNotEmpty()) {
                                    val bytes=ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,85,it); it.toByteArray() }
                                    store.addCapture(reportId!!,bytes,PotholeDetector.json(boxes),location)
                                    main.execute { count++; message="Possible pothole detected · saved offline" }
                                } else main.execute { message="Scanning · no pothole in the latest frame" }
                            } finally { if(bitmap!==raw) bitmap.recycle(); raw.recycle() }
                        }
                    } catch(e: Exception) {
                        active.set(false)
                        main.execute { message="Scanning paused. Camera or model could not be processed. Stop to save this report." }
                    } finally { image.close() }
                }
                provider!!.unbindAll()
                camera=provider!!.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,cameraPreview,analysis)
            } catch(e: Exception) { message="Camera unavailable. Stop to return to your reports." }
        },main)
        onDispose { lifecycle.lifecycle.removeObserver(observer); stop=null; finish(false) }
    }
}
