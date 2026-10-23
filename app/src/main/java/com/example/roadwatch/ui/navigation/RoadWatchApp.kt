package com.example.roadwatch.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.roadwatch.R
import com.example.roadwatch.data.auth.AuthViewModel
import com.example.roadwatch.ui.auth.RoadWatchAuthFlow
import com.example.roadwatch.ui.auth.UpdatePasswordScreen
import com.example.roadwatch.ui.dashboard.DashboardScreen
import com.example.roadwatch.ui.startup.RoadWatchStartupFlow

@Composable
fun RoadWatchApp(auth: AuthViewModel) {
    val state by auth.state.collectAsState()
    var showAuth by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.signedIn) { if (state.signedIn) showAuth = true }
    BackHandler(state.loading || state.recovering) { /* Finish the request or explicitly cancel recovery. */ }
    when {
        state.restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.recovering && state.signedIn -> UpdatePasswordScreen(
            loading = state.loading, error = state.error,
            onSave = auth::updatePassword, onCancel = auth::signOut
        )
        state.signedIn -> Column(Modifier.fillMaxSize()) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Box(Modifier.weight(1f)) {
                DashboardScreen(onBack = auth::signOut, isSigningOut = state.loading)
            }
        }
        showAuth || state.recovering || state.error != null -> RoadWatchAuthFlow(
            logoRes = R.drawable.roadwatch_logo,
            isLoading = state.loading,
            error = state.error,
            message = state.message,
            onLogin = auth::login,
            onSignUp = auth::signUp,
            onResetPassword = auth::resetPassword,
            onResendConfirmation = auth::resendConfirmation,
            resendSeconds = state.resendSeconds,
            onRouteChanged = auth::clearFeedback,
            onBack = { auth.clearFeedback(); showAuth = false }
        )
        else -> RoadWatchStartupFlow(
            logoRes = R.drawable.roadwatch_logo,
            isSignedIn = false,
            isAppReady = true,
            onSignUp = { showAuth = true },
            onDashboard = { } // Authenticated sessions select the dashboard above.
        )
    }
}
