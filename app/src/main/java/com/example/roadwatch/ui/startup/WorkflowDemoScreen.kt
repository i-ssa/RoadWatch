package com.example.roadwatch.ui.startup

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Brand(@DrawableRes logoRes: Int, size: Int) {
    Image(painterResource(logoRes), contentDescription = "RoadWatch logo", modifier = Modifier.size(size.dp))
}

/** Loading option: pass actual progress, or null for indeterminate loading.
 * No timer or navigation is hidden here. The app controls readiness and navigation.
 */
@Composable
fun RoadWatchSplashScreen(@DrawableRes logoRes: Int, progress: Float? = null,
    status: String = "Preparing RoadWatch…", onDemoComplete: (() -> Unit)? = null) {
    // Illustrative stages only; readiness still comes from the app's actual state.
    var phase by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (phase < 5) { delay(1800); phase++ }
    }
    val labels = listOf("Scanning the clear road…", "Road defect approaching…", "Pothole detected", "Marking the hazard…",
        "Preparing defect report…", "Report ready")
    val red = Color(0xFFB42332)
    val transition = rememberInfiniteTransition(label = "Road scan")
    val scanY by transition.animateFloat(0.1f, 0.9f,
        infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse), label = "Scan position")
    val roadMotion by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Restart), label = "Moving road")
    val potholeApproach by animateFloatAsState(if (phase >= 1) 1f else 0f,
        tween(1400), label = "Approaching pothole")
    Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Brand(logoRes, 180)
        Spacer(Modifier.height(38.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Scan", "Detect", "Mark", "Report").forEachIndexed { index, text ->
                Text(text, fontSize = 12.sp,
                    color = if (index == (phase - 1).coerceIn(0, 3)) Color.White else Forest,
                    modifier = Modifier.background(
                        if (index == (phase - 1).coerceIn(0, 3)) Forest else Color(0xFFEDF4EF),
                        RoundedCornerShape(8.dp)).padding(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFEDF4EF))) {
            val w = size.width
            val h = size.height
            // Stylized road illustration, not a live camera or detection result.
            val road = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * .40f, 0f); lineTo(w * .60f, 0f)
                lineTo(w * .90f, h); lineTo(w * .10f, h); close()
            }
            drawPath(road, Forest.copy(alpha = .85f))
            for (i in -1..5) {
                val y = h * (i + if (phase < 2) roadMotion else 0f) / 5f
                drawLine(Color.White.copy(alpha = .75f), Offset(w / 2, y), Offset(w / 2, y + h * .09f), 4.dp.toPx())
            }
            val target = Offset(w * .30f, h * .38f)
            val box = Size(w * .40f, h * .28f)
            if (phase >= 1) {
                val scale = .25f + .75f * potholeApproach
                drawOval(Color(0xFF172C23),
                    Offset(w * .5f - w * .12f * scale, h * (.12f + .33f * potholeApproach)),
                    Size(w * .24f * scale, h * .15f * scale))
            }
            if (phase >= 2) {
                val marker = if (phase >= 3) red else ScanGreen
                drawRect(marker.copy(alpha = .18f), target, box)
                drawRect(marker, target, box, style = Stroke(2.dp.toPx()))
            }
            if (phase < 3) {
                drawLine(ScanGreen, Offset(0f, h * scanY), Offset(w, h * scanY), 3.dp.toPx())
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Illustrated workflow · demo", color = SecondaryText, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
        if (phase >= 3) {
            Text("Pothole detected", color = red, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFFF2F6F3), RoundedCornerShape(12.dp)).padding(16.dp)) {
            Text("Road defect report", color = Forest, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(when (phase) {
                4 -> "Defect: Pothole\nAdding the marked road image…"
                5 -> "Defect: Pothole · Road surface damage. Marked image attached. Ready for review."
                else -> "Waiting for the road scan…"
            }, color = SecondaryText, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("Smarter roads. Safer journeys.", color = Forest, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        Text(labels[phase], color = Forest, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(status, color = SecondaryText, textAlign = TextAlign.Center, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        if (onDemoComplete != null) {
            if (phase == 5) {
                Button(onClick = onDemoComplete, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Get started") }
            }
        } else if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ScanGreen,
                trackColor = Color(0xFFE5EEE8))
        } else {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = ScanGreen, trackColor = Color(0xFFE5EEE8))
        }
    }
}

