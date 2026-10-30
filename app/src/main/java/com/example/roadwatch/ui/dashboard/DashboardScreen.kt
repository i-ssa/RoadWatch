package com.example.roadwatch.ui.dashboard

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.roadwatch.R
import kotlinx.coroutines.delay

/** Native UI backed by local demonstration data. Authentication remains owned by the host. */
@Composable
fun DashboardScreen(onBack: () -> Unit, isSigningOut: Boolean = false) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("roadwatch_ui", Context.MODE_PRIVATE) }
    var appearance by rememberSaveable { mutableStateOf(preferences.getString("appearance", "system") ?: "system") }
    val dark = appearance == "dark" || (appearance == "system" && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(primary = Color(0xFF9FD0B1), onPrimary = Color(0xFF10271A),
        background = Color(0xFF131A16), surface = Color(0xFF131A16), surfaceVariant = Color(0xFF1C2620), onSurface = Color(0xFFE6EEE8), onSurfaceVariant = Color(0xFFA2B0A6), outlineVariant = Color(0xFF2A352E), secondaryContainer = Color(0xFF263C2E))
    else lightColorScheme(primary = Color(0xFF0D472F), onPrimary = Color.White,
        background = Color.White, surface = Color.White, surfaceVariant = Color(0xFFF4F6F4), onSurface = Color(0xFF17251E), onSurfaceVariant = Color(0xFF69756E), outlineVariant = Color(0xFFE9EDEA), secondaryContainer = Color(0xFFE8F0EB))
    var route by rememberSaveable { mutableStateOf("Home") }
    var onboarding by rememberSaveable { mutableStateOf(!preferences.getBoolean("intro_v1", false)) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var captured by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRoute by rememberSaveable { mutableStateOf("Detections") }
    fun completeIntro() { preferences.edit().putBoolean("intro_v1", true).apply(); onboarding = false; step = 0 }
    fun theme(value: String) { appearance = value; preferences.edit().putString("appearance", value).apply() }
    fun navigate(next: String) {
        if (scanning) { pendingRoute = next; dialog = "stop" } else route = next
    }
    LaunchedEffect(scanning) {
        if (scanning) { delay(2500); captured = true }
    }
    BackHandler(enabled = onboarding || route != "Home" || scanning) {
        if (onboarding) { if (step > 0) step-- else completeIntro() }
        else if (scanning) { pendingRoute = "Detections"; dialog = "stop" }
        else route = "Home"
    }
    MaterialTheme(colorScheme = colors, typography = PreviewTypography) {
        Scaffold(containerColor = colors.background, topBar = {
            Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.widthIn(max = 430.dp).fillMaxWidth().height(104.dp).padding(horizontal = 24.dp)) {
                    val tint = colors.primary
                    val matrix = ColorMatrix(floatArrayOf(0f,0f,0f,0f,tint.red*255,
                        0f,0f,0f,0f,tint.green*255,0f,0f,0f,0f,tint.blue*255,
                        -.2126f,-.7152f,-.0722f,0f,255f))
                    Image(painterResource(R.drawable.roadwatch_logo), "RoadWatch AI",
                        Modifier.size(112.dp, 80.dp).align(Alignment.Center), colorFilter = if (dark) ColorFilter.colorMatrix(matrix) else null)
                    TextButton(onClick = { if (onboarding) completeIntro() else dialog = "help" },
                        modifier = Modifier.align(Alignment.CenterEnd).semantics { contentDescription = if (onboarding) "Skip introduction" else "Help" }) {
                        if (onboarding) Text("Skip", fontSize = 12.sp) else AppIcon(5)
                    }
                    HorizontalDivider(Modifier.align(Alignment.BottomCenter))
                }
            }
        }, bottomBar = {
            if (!onboarding) Column(Modifier.fillMaxWidth().background(colors.background).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.widthIn(max = 430.dp).fillMaxWidth().padding(horizontal = 16.dp)
                    .padding(vertical = 10.dp)) {
                    listOf("Home", "Detections", "Map", "Profile").forEachIndexed { i, tab ->
                        val active = route == tab || (route == "Scan" && tab == "Home")
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .selectable(selected = active, role = Role.Tab, onClick = { navigate(tab) }).padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(48.dp, 28.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (active) colors.secondaryContainer else Color.Transparent), contentAlignment = Alignment.Center) {
                                AppIcon(if (i == 0) 6 else i, tint = if (active) colors.primary else colors.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(tab, fontSize = 10.sp, lineHeight = 15.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (active) colors.primary else colors.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider()
            }
        }) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 430.dp).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (onboarding) 26.dp else 24.dp, vertical = if (onboarding) 48.dp else 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (onboarding) {
                    Spacer(Modifier.height(32.dp)); IconBadge(if (step == 0) 4 else if (step == 1) 0 else 1)
                    Spacer(Modifier.height(24.dp)); Caption("${step + 1} / 3")
                    Heading(listOf("Mount your phone.\nKeep your focus.", "Start a scan.\nLet it look.", "Take a look.\nThen decide.")[step])
                    Body(listOf("Secure your phone with a clear view of the road ahead.",
                        "RoadWatch will look for possible road defects along your route.",
                        "Review captured defects before preparing a report.")[step])
                    Spacer(Modifier.height(48.dp))
                    Body(listOf("Set up while safely parked. Let a passenger handle changes during a journey.",
                        "This version uses sample scans. No camera or location access is requested.",
                        "Check the suggested details and location. Nothing is submitted automatically.")[step])
                    Spacer(Modifier.height(24.dp))
                    Action(if (step == 2) "Open RoadWatch" else "Next") { if (step == 2) completeIntro() else step++ }
                    if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
                } else when (route) {
                    "Home" -> {
                        IconBadge(0); Heading("Welcome to RoadWatch.")
                        Body("Your next journey can help make roads better. Start a scan when you’re ready.")
                        Spacer(Modifier.height(24.dp)); Action("Start scan") { dialog = "setup" }
                        Spacer(Modifier.height(32.dp)); Text("Recent activity", style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider(Modifier.padding(vertical = 20.dp))
                        if (captured) {
                            SectionTitle("1 sample detection")
                            Body("You’ve finished your sample scan. Take a look at what was captured.")
                            TextButton(onClick = { route = "Detections" }) { Text("Review detection") }
                        } else { AppIcon(1); SectionTitle("Your first scan starts here"); Body("Once you’ve taken a scan, you’ll find it here. Take your time getting started.") }
                        Spacer(Modifier.height(24.dp)); Caption("Get set up while parked. We’ll guide you through the rest.")
                    }
                    "Scan" -> {
                        Heading("Scanning your route"); Caption("Sample scan · camera not connected")
                        SampleRoad(captured)
                        Body(if (captured) "1 possible defect captured" else "Looking at the sample route…")
                        Action("Stop scan & review") { scanning = false; route = "Detections" }
                    }
                    "Detections" -> {
                        Heading("Detections"); Body("Let’s check what was spotted. You decide what goes into a report.")
                        if (captured) {
                            SampleRoad(true); Text("Possible pothole", style = MaterialTheme.typography.titleLarge)
                            Caption("Sample route · location not confirmed\nSeverity not assessed")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = selected, onCheckedChange = { selected = it }); Text("Include in report")
                            }
                            TextButton(onClick = { dialog = "inspect" }) { Text("Inspect details") }
                            Button(onClick = { dialog = "report" }, enabled = selected,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(8.dp)) { Text("Prepare report (1)") }
                        } else { Spacer(Modifier.height(40.dp)); AppIcon(1, Modifier.size(40.dp)); Heading("Nothing to review just yet")
                            Body("After your first scan, any possible road defects will show up here.")
                            Action("Start scan") { dialog = "setup" } }
                    }
                    "Map" -> {
                        Heading("Road hazard map"); Body("See what’s been reported along the roads around you.")
                        val line = colors.outlineVariant
                        Canvas(Modifier.fillMaxWidth().height(250.dp).padding(vertical = 24.dp)
                            .semantics { contentDescription = "Map illustration, no live hazard data" }) {
                            drawRect(line.copy(alpha = .2f)); drawLine(line, Offset(0f,size.height),Offset(size.width,0f),24f)
                            drawLine(line,Offset(size.width*.2f,0f),Offset(size.width*.8f,size.height),24f)
                        }
                        Caption("Illustration · no live data")
                        Heading("A little local knowledge"); Body("Once the map is connected, you’ll be able to explore an area or choose to use your current location.")
                    }
                    "Profile" -> {
                        IconBadge(3); Heading("Your space."); Body("A few things to make RoadWatch yours.")
                        Spacer(Modifier.height(24.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Dark mode"); Caption(if (appearance == "system") "Following device" else if (dark) "On" else "Off") }
                            Switch(checked = dark, onCheckedChange = { theme(if (it) "dark" else "light") })
                        }
                        Setting("Use device appearance") { theme("system") }
                        Setting("Notifications") { dialog = "notifications" }
                        Setting("Privacy & permissions") { dialog = "privacy" }
                        Setting("Getting started") { step = 0; onboarding = true }
                        Setting("About RoadWatch") { dialog = "about" }
                        TextButton(onClick = { dialog = "signout" }, enabled = !isSigningOut) { Text(if (isSigningOut) "Signing out…" else "Sign out") }
                    }
                }
                Spacer(Modifier.height(24.dp)); Caption("Development preview · sample scanning and reporting")
            }
            }
        }
        dialog?.let { kind ->
            val title = when(kind) { "setup" -> "Before we head out"; "stop" -> "End this sample scan?"; "report" -> "Prepare your report"; "signout" -> "Sign out?"; "inspect" -> "Check before including"; "privacy" -> "Privacy & permissions"; "notifications" -> "Notifications"; "draft" -> "Sample draft prepared"; "about" -> "RoadWatch AI"; else -> "Let’s get you started" }
            val body = when(kind) {
                "setup" -> "Secure your phone while parked. This sample does not access your camera or location. Real scanning is not connected yet."
                "stop" -> "Stop scanning before moving to another screen. You can review any sample capture afterwards."
                "report" -> "Possible pothole · sample location unconfirmed. No recipient is configured. This version can prepare a sample draft but cannot send a report."
                "draft" -> "Your sample draft is ready for review in this session. Nothing has been sent."
                "signout" -> "You can sign in again whenever you’re ready."
                "inspect" -> "This is an illustrated sample. No real defect, depth, or severity has been measured."
                "privacy" -> "No camera or location permissions are requested by this dashboard. Appearance and onboarding choices stay on this device. Your account is managed by the existing authentication service."
                "notifications" -> "Report updates and hazard alerts are not connected yet."
                "about" -> "Smarter roads. Safer journeys. Native Android interface with sample scan data."
                else -> "Start a sample scan, review your captures, then choose what to include in a report. Replay the introduction from Profile."
            }
            AlertDialog(onDismissRequest = { dialog = null }, shape = RoundedCornerShape(16.dp), containerColor = colors.background, tonalElevation = 0.dp, title = { Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }, text = { Text(body, textAlign = TextAlign.Center) },
                confirmButton = { TextButton(onClick = {
                    dialog = null
                    when(kind) {
                        "setup" -> { captured = false; selected = false; route = "Scan"; scanning = true }
                        "stop" -> { scanning = false; route = pendingRoute }
                        "report" -> dialog = "draft"
                        "signout" -> onBack()
                    }
                }) { Text(when(kind) { "setup" -> "Try sample scan"; "stop" -> "Stop scan"; "report" -> "Prepare sample draft"; "signout" -> "Sign out"; else -> "Got it" }) } },
                dismissButton = { if (kind in listOf("setup", "stop", "report", "signout")) TextButton(onClick = { dialog = null }) { Text("Cancel") } })
        }
    }
}

@Composable private fun Heading(text: String) { Text(text, Modifier.padding(top = 20.dp, bottom = 12.dp), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center) }
@Composable private fun Body(text: String) { Text(text, Modifier.widthIn(max = 280.dp).padding(bottom = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
@Composable private fun Caption(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
@Composable private fun Action(text: String, click: () -> Unit) { Button(onClick = click, modifier = Modifier.widthIn(max = 310.dp).fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(8.dp)) { if (text == "Start scan") { AppIcon(0, tint = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(10.dp)) }; Text(text) } }
@Composable private fun Setting(text: String, click: () -> Unit) { TextButton(onClick = click, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(text); Text("›") } }; HorizontalDivider() }
@Composable private fun AppIcon(type: Int, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    val color = tint
    Canvas(modifier.size(22.dp)) {
        val sx = size.width/24; val sy = size.height/24
        fun line(x:Float,y:Float,a:Float,b:Float) = drawLine(color,Offset(x*sx,y*sy),Offset(a*sx,b*sy),1.7f*sx)
        when(type) {
            0 -> { line(3f,9f,3f,3f);line(3f,3f,9f,3f);line(15f,3f,21f,3f);line(21f,3f,21f,9f);line(21f,15f,21f,21f);line(21f,21f,15f,21f);line(9f,21f,3f,21f);line(3f,21f,3f,15f);drawCircle(color,4*sx,style=Stroke(1.7f*sx)) }
            1 -> { line(5f,3f,19f,3f);line(19f,3f,19f,21f);line(19f,21f,5f,21f);line(5f,21f,5f,3f);line(9f,9f,15f,9f);line(9f,14f,15f,14f) }
            2 -> { line(3f,5f,9f,3f);line(9f,3f,15f,5f);line(15f,5f,21f,3f);line(3f,5f,3f,21f);line(9f,3f,9f,19f);line(15f,5f,15f,21f);line(21f,3f,21f,19f);line(3f,21f,9f,19f);line(9f,19f,15f,21f);line(15f,21f,21f,19f) }
            3 -> { drawCircle(color,4*sx,Offset(12*sx,7*sy),style=Stroke(1.7f*sx));drawArc(color,180f,180f,false,Offset(4*sx,14*sy), androidx.compose.ui.geometry.Size(16*sx,14*sy),style=Stroke(1.7f*sx)) }
            5 -> { drawCircle(color,9*sx,style=Stroke(1.7f*sx));line(12f,10f,12f,14f);drawCircle(color, .8f*sx,Offset(12*sx,7*sy)) }
            6 -> { line(3f,10f,12f,3f);line(12f,3f,21f,10f);line(21f,10f,21f,21f);line(21f,21f,3f,21f);line(3f,21f,3f,10f);line(9f,21f,9f,13f);line(9f,13f,15f,13f);line(15f,13f,15f,21f) }
            else -> { line(7f,2f,17f,2f);line(17f,2f,17f,22f);line(17f,22f,7f,22f);line(7f,22f,7f,2f);line(3f,8f,3f,16f);line(21f,8f,21f,16f) }
        }
    }
}
@Composable private fun SampleRoad(detected: Boolean) {
    Canvas(Modifier.fillMaxWidth().height(250.dp).padding(vertical = 16.dp).semantics { contentDescription = "Illustrated sample road${if (detected) " with possible pothole" else ""}" }) {
        drawRect(Color(0xFF9EB7A6)); val w=size.width;val h=size.height
        val road=Path().apply { moveTo(w*.4f,0f);lineTo(w*.6f,0f);lineTo(w,h);lineTo(0f,h);close() };drawPath(road,Color(0xFF4B554F))
        for(i in 0..5) drawLine(Color.White,Offset(w/2,h*i/6),Offset(w/2,h*i/6+h*.07f),3.dp.toPx())
        if(detected) { drawOval(Color(0xFF26332A),Offset(w*.36f,h*.61f),androidx.compose.ui.geometry.Size(w*.28f,h*.12f));drawRect(Color(0xFFE65100),Offset(w*.3f,h*.54f),androidx.compose.ui.geometry.Size(w*.4f,h*.26f),style=Stroke(2.dp.toPx())) }
    }
}

private val PreviewTypography = Typography(
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-.8).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 23.sp))
@Composable private fun SectionTitle(text: String) { Text(text, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
@Composable private fun IconBadge(type: Int) {
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { AppIcon(type, Modifier.size(28.dp)) }
}
