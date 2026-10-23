package com.example.roadwatch.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Placeholder until the dashboard feature is implemented. */
@Composable
fun DashboardScreen(onBack: () -> Unit, isSigningOut: Boolean = false) {
    Column(Modifier.fillMaxSize().safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text("This screen will be built next.")
        TextButton(onClick = onBack, enabled = !isSigningOut) { Text(if (isSigningOut) "Signing out…" else "Sign out") }
    }
}
