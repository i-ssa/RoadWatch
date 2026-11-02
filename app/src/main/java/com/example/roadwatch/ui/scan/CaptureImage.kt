package com.example.roadwatch.ui.scan

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.roadwatch.data.reports.Capture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun CaptureImage(capture: Capture) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,capture.path) {
        value=withContext(Dispatchers.IO) { BitmapFactory.decodeFile(capture.path,BitmapFactory.Options().apply { inSampleSize=2 }) }
    }
    val boxes=remember(capture.boxes) { runCatching { JSONArray(capture.boxes) }.getOrDefault(JSONArray()) }
    bitmap?.let { image ->
        Box(Modifier.fillMaxWidth().aspectRatio(image.width.toFloat()/image.height)) {
            Image(image.asImageBitmap(),"Road image with ${boxes.length()} possible potholes",Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                for(index in 0 until boxes.length()) {
                    val box=boxes.getJSONObject(index)
                    drawRect(Color(0xFFFF6D00),Offset(box.getDouble("x").toFloat()*size.width,box.getDouble("y").toFloat()*size.height),
                        Size(box.getDouble("width").toFloat()*size.width,box.getDouble("height").toFloat()*size.height),style=Stroke(3.dp.toPx()))
                }
            }
        }
    } ?: Text("Image unavailable on this device")
    for(index in 0 until boxes.length()) {
        Text("Possible pothole ${index+1} · ${(boxes.getJSONObject(index).optDouble("confidence",0.0)*100).toInt()}% confidence")
    }
    Text(if(capture.latitude!=null && capture.longitude!=null) "Location: %.5f, %.5f · accuracy ±%.0f m".format(capture.latitude,capture.longitude,capture.accuracy ?: 0f) else "Location unavailable for this capture")
    Text("Model suggestion · severity has not been measured")
}
