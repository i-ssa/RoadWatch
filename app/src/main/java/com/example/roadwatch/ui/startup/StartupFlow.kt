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

/** Auth state must be resolved before calling this entry point.
 * Returning signed-in users wait for real initialization; first-time users see the demo.
 * The caller supplies signup/dashboard destinations and persists onboarding/auth state.
 */
@Composable
fun RoadWatchStartupFlow(
    @DrawableRes logoRes: Int,
    isSignedIn: Boolean,
    isAppReady: Boolean,
    onSignUp: () -> Unit,
    onDashboard: () -> Unit
) {
    var showDemo by rememberSaveable { mutableStateOf(false) }
    val latestDashboard by rememberUpdatedState(onDashboard)
    LaunchedEffect(isSignedIn, isAppReady) {
        if (isSignedIn && isAppReady) latestDashboard()
    }
    if (isSignedIn) {
        RoadWatchLandingScreen(logoRes = logoRes, onGetStarted = {},
            buttonLabel = "Next", showButton = false)
    } else if (showDemo) {
        RoadWatchSplashScreen(logoRes = logoRes, status = "Learn how RoadWatch works",
            onDemoComplete = onSignUp)
    } else {
        RoadWatchLandingScreen(logoRes = logoRes, onGetStarted = { showDemo = true },
            buttonLabel = "Next")
    }
}
