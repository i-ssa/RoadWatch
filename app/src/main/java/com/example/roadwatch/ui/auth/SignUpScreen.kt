package com.example.roadwatch.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Placeholder until the auth feature is implemented. */
@Composable
fun SignUpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Sign up", style = MaterialTheme.typography.headlineMedium)
        Text("This screen will be built next.")
        TextButton(onClick = onBack) { Text("Back to welcome") }
    }
}
