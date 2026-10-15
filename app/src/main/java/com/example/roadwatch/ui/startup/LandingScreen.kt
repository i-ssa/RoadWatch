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

/** A welcome option. The caller decides where Get started navigates. */
@Composable
fun RoadWatchLandingScreen(@DrawableRes logoRes: Int, onGetStarted: () -> Unit,
    buttonLabel: String = "Next", showButton: Boolean = true) {
    Column(
        Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(logoRes), contentDescription = "RoadWatch logo",
            modifier = Modifier.size(240.dp))
        Spacer(Modifier.height(28.dp))
        Text("Detect defects  •  Pin locations  •  Report hazards", color = Forest,
            fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        if (showButton) Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
            Text(buttonLabel, fontSize = 16.sp)
        }
    }
}

