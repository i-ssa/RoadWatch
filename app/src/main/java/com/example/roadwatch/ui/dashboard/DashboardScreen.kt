package com.example.roadwatch.ui.dashboard

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.roadwatch.data.reports.ReportStore
import com.example.roadwatch.ui.map.HazardMapScreen
import com.example.roadwatch.ui.scan.JourneyButton
import com.example.roadwatch.ui.scan.JourneyGlyph
import com.example.roadwatch.ui.scan.JourneySystemBars
import com.example.roadwatch.ui.scan.ReportsScreen
import com.example.roadwatch.ui.scan.ScanScreen
import com.example.roadwatch.ui.theme.RoadWatchTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Destination(val label: String, val icon: String) {
    Home("Home", "home"), Reports("Reports", "report"), Map("Map", "map"),
    Profile("Profile", "profile"), Scan("Scan", "scan")
}

@Composable
fun DashboardScreen(onBack: () -> Unit, owner: String, isSigningOut: Boolean = false) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("roadwatch_ui", Context.MODE_PRIVATE) }
    var appearance by rememberSaveable { mutableStateOf(preferences.getString("appearance", "system") ?: "system") }
    var route by rememberSaveable(owner) { mutableStateOf(Destination.Home) }
    var showGuide by rememberSaveable { mutableStateOf(!preferences.getBoolean("intro_v1", false)) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val dark = appearance == "dark" || (appearance == "system" && isSystemInDarkTheme())

    fun closeGuide() {
        preferences.edit().putBoolean("intro_v1", true).apply()
        showGuide = false
    }

    BackHandler(route != Destination.Home && route != Destination.Scan) { route = Destination.Home }
    RoadWatchTheme(darkTheme = dark) {
        when (route) {
            Destination.Map -> HazardMapScreen(
                owner, onBack = { route = Destination.Home },
                onStartScan = { dialog = "scan" }, onReports = { route = Destination.Reports }
            )
            Destination.Scan -> ScanScreen(owner) { route = Destination.Reports }
            else -> {
                JourneySystemBars(darkIcons = !dark)
                val scroll = rememberSaveable(route, saver = ScrollState.Saver) { ScrollState(0) }
                Scaffold(
                    bottomBar = { DashboardNavigation(route) { route = it } }
                ) { insets ->
                    Box(
                        Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(scroll)
                                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (route == Destination.Home) "RoadWatch" else route.label,
                                    Modifier.weight(1f).semantics { heading() },
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                JourneyButton("How RoadWatch works", "info", { showGuide = true }, dark = dark)
                            }
                            when (route) {
                                Destination.Home -> ScanHome(
                                    owner, onScan = { dialog = "scan" },
                                    onReports = { route = Destination.Reports },
                                    onMap = { route = Destination.Map }
                                )
                                Destination.Reports -> ReportsScreen(owner, onStartScan = { dialog = "scan" })
                                Destination.Profile -> Profile(
                                    appearance, isSigningOut,
                                    onAppearance = {
                                        appearance = it
                                        preferences.edit().putString("appearance", it).apply()
                                    },
                                    onPrivacy = { dialog = "privacy" },
                                    onSignOut = { dialog = "signout" }
                                )
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }

        if (showGuide) {
            AlertDialog(
                onDismissRequest = ::closeGuide,
                title = { Text("How RoadWatch works") },
                text = {
                    Text("Mount your phone while parked. Scan the road with the camera, then review the saved captures in Reports. You choose which reports to submit.")
                },
                confirmButton = { TextButton(onClick = ::closeGuide) { Text("Got it") } }
            )
        }
        when (dialog) {
            "scan" -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Start a road scan?") },
                text = { Text("Mount your phone while parked. Scanning uses the camera and saves a draft for you to review.") },
                confirmButton = {
                    TextButton(onClick = { dialog = null; route = Destination.Scan }) { Text("Start camera scan") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
            )
            "privacy" -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Privacy & permissions") },
                text = {
                    Text("The camera runs during scans. Location is optional and used for captures and map centering. Drafts stay on this device until you submit them. Signed-in users can see submitted hazard locations. Photos remain private.")
                },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } }
            )
            "signout" -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Sign out?") },
                text = { Text("Drafts stay on this device. Uploads resume when you sign in again.") },
                confirmButton = { TextButton(onClick = { dialog = null; onBack() }) { Text("Sign out") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun ScanHome(owner: String, onScan: () -> Unit, onReports: () -> Unit, onMap: () -> Unit) {
    val context = LocalContext.current
    val drafts by produceState<Int?>(null, owner) {
        value = withContext(Dispatchers.IO) { ReportStore.get(context).reports(owner).count { it.status == "draft" } }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            JourneyGlyph("scan", MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Scan the road", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Capture possible potholes. Review them before submitting a report.")
            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("Start scan") }
        }
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column {
            DashboardLink("Review reports", when (val count = drafts) {
                null -> "Open saved scans"
                0 -> "No drafts to review"
                1 -> "1 draft to review"
                else -> "$count drafts to review"
            }, "report", onReports)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            DashboardLink("Open road map", "See shared hazards and your drafts", "map", onMap)
        }
    }
}

@Composable
private fun DashboardLink(title: String, detail: String, icon: String, onClick: () -> Unit) {
    Surface(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JourneyGlyph(icon, MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            JourneyGlyph("forward", MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardNavigation(route: Destination, onNavigate: (Destination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        listOf(Destination.Home, Destination.Reports, Destination.Map, Destination.Profile).forEach { tab ->
            val selected = route == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(tab) },
                label = { Text(tab.label) },
                icon = {
                    JourneyGlyph(tab.icon, if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )
        }
    }
}

@Composable
private fun Profile(appearance: String, signingOut: Boolean, onAppearance: (String) -> Unit,
                    onPrivacy: () -> Unit, onSignOut: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("system" to "Device", "light" to "Light", "dark" to "Dark").forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = appearance == value,
                        onClick = { onAppearance(value) },
                        shape = SegmentedButtonDefaults.itemShape(index, 3)
                    ) { Text(label) }
                }
            }
        }
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        DashboardLink("Privacy & permissions", "Camera, location and shared reports", "info", onPrivacy)
    }
    OutlinedButton(onClick = onSignOut, enabled = !signingOut, shape = MaterialTheme.shapes.medium) {
        Text(if (signingOut) "Signing out…" else "Sign out")
    }
}
