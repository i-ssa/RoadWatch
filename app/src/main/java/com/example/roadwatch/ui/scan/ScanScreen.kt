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
    val preview = remember { PreviewView(context) }
    BackHandler { if(!stopping) { if(stop != null) { stopping=true; stop?.invoke() } else onFinished() } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text("Road scan",style=MaterialTheme.typography.headlineSmall)
        Text("Keep the phone mounted. Set up while parked.")
        if(!permission) {
            Text("Camera access is needed to detect potholes. Scanning works without internet.")
            Button(onClick={request.launch(Manifest.permission.CAMERA)}) { Text("Allow camera") }
            TextButton(onClick=onFinished) { Text("Back") }
        } else {
            AndroidView(factory={preview},modifier=Modifier.weight(1f).fillMaxWidth())
            Text(message)
            Text("$count captures saved on this device")
            Text("Stopping saves a draft. Review and submit it from Detections.")
            if(!locationEnabled) TextButton(onClick={locationRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}) { Text("Add location to captures") }
            else Text("Location enabled · captures use a fresh fix when available")
            if(latency>0) Text("Last analysis: $latency ms")
            Button(onClick={stopping=true; stop?.invoke()},enabled=!stopping && stop!=null,modifier=Modifier.fillMaxWidth()) { Text(if(stopping) "Saving…" else "Stop scan & save report") }
        }
    }
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
                provider!!.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,cameraPreview,analysis)
            } catch(e: Exception) { message="Camera unavailable. Stop to return to your reports." }
        },main)
        onDispose { lifecycle.lifecycle.removeObserver(observer); stop=null; finish(false) }
    }
}
