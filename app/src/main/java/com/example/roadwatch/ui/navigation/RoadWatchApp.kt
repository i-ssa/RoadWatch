package com.example.roadwatch.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.roadwatch.data.auth.AuthViewModel
import com.example.roadwatch.ui.auth.RoadWatchAuthFlow
import com.example.roadwatch.ui.auth.UpdatePasswordScreen
import com.example.roadwatch.ui.dashboard.DashboardScreen
import com.example.roadwatch.ui.startup.RoadWatchLandingScreen

@Composable
fun RoadWatchApp(auth: AuthViewModel) {
    val state by auth.state.collectAsState()
    var showAuth by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.signedIn) { if (state.signedIn) showAuth = true }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.userId) {
        state.userId?.let { owner ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.roadwatch.data.reports.ReportStore.get(context).recoverOncePerProcess(owner)
                com.example.roadwatch.data.reports.ReportSyncWorker.schedule(context, owner)
            }
        }
    }
    BackHandler(state.loading || state.recovering) { /* Finish the request or explicitly cancel recovery. */ }
    when {
        state.restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.recovering && state.signedIn -> UpdatePasswordScreen(
            loading = state.loading, error = state.error,
            onSave = auth::updatePassword, onCancel = auth::signOut
        )
        state.signedIn && state.userId == null -> Column(Modifier.fillMaxSize(), horizontalAlignment=Alignment.CenterHorizontally) {
            Text("Your saved account is incomplete. Sign out and sign in again to use the camera scanner.")
            TextButton(onClick=auth::signOut,enabled=!state.loading) { Text("Sign out") }
        }
        state.signedIn -> Column(Modifier.fillMaxSize()) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Box(Modifier.weight(1f)) {
                DashboardScreen(onBack = auth::signOut, isSigningOut = state.loading, owner = checkNotNull(state.userId))
            }
        }
        showAuth || state.recovering || state.error != null -> RoadWatchAuthFlow(
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
        else -> RoadWatchLandingScreen(onGetStarted = { showAuth = true })
    }
}
