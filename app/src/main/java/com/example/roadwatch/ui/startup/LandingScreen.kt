package com.example.roadwatch.ui.startup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roadwatch.ui.scan.JourneyGlyph

@Composable
fun RoadWatchLandingScreen(onGetStarted: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text("RoadWatch", style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text("Scan for potholes, review your captures, and share hazard locations.", style = MaterialTheme.typography.bodyLarge)
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        LandingStep("Scan", "The camera works offline.", "scan")
                        LandingStep("Review", "Check captures before you submit.", "report")
                        LandingStep("Map", "See hazards shared by other drivers.", "map")
                    }
                }
                Button(onClick = onGetStarted, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = MaterialTheme.shapes.medium) {
                    Text("Get started")
                }
            }
        }
    }
}

@Composable
private fun LandingStep(title: String, detail: String, icon: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        JourneyGlyph(icon, MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
