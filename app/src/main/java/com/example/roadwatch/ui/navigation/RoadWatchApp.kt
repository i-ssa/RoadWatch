package com.example.roadwatch.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.roadwatch.R
import com.example.roadwatch.ui.auth.SignUpScreen
import com.example.roadwatch.ui.dashboard.DashboardScreen
import com.example.roadwatch.ui.startup.RoadWatchStartupFlow

private enum class Destination { Startup, SignUp, Dashboard }

@Composable
fun RoadWatchApp() {
    var destination by rememberSaveable { mutableStateOf(Destination.Startup) }
    when (destination) {
        Destination.Startup -> RoadWatchStartupFlow(
            logoRes = R.drawable.roadwatch_logo,
            // Connect the real restored authentication session here when implemented.
            isSignedIn = false,
            isAppReady = true,
            onSignUp = { destination = Destination.SignUp },
            onDashboard = { destination = Destination.Dashboard }
        )
        Destination.SignUp -> SignUpScreen(onBack = { destination = Destination.Startup })
        Destination.Dashboard -> DashboardScreen(onBack = { destination = Destination.Startup })
    }
}
