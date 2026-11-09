package com.example.roadwatch.ui.auth

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.roadwatch.ui.scan.JourneyButton

/** Service results belong to the host's ViewModel. Navigate only after a real session is established.
 * Never persist passwords in saved state. Clear backend error/message when the user changes routes.
 * Registration may require email verification; show that through message, not a fake signed-in state.
 */
@Composable
fun RoadWatchAuthFlow(
    isLoading: Boolean,
    error: String? = null,
    message: String? = null,
    onLogin: (email: String, password: String) -> Unit,
    onSignUp: (name: String, email: String, password: String) -> Unit,
    onResetPassword: (email: String) -> Unit,
    onResendConfirmation: (email: String) -> Unit,
    resendSeconds: Int = 0,
    onRouteChanged: () -> Unit = {},
    onBack: () -> Unit,
) {
    var route by rememberSaveable { mutableStateOf("login") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var emailAttempted by remember { mutableStateOf(false) }
    fun navigate(next: String) {
        route = next; password = ""; confirm = ""; visible = false; attempted = false; emailAttempted = false
        onRouteChanged()
    }
    fun back() { if (route == "login") onBack() else navigate("login") }
    BackHandler(enabled = !isLoading) { back() }
    val signup = route == "signup"
    val reset = route == "reset"
    val emailError = !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordError = if (signup) password.length < 8 else password.isEmpty()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val dark = androidx.compose.foundation.isSystemInDarkTheme()
                JourneyButton("Back", "back", ::back, enabled = !isLoading, dark = dark)
                Spacer(Modifier.height(12.dp))
                Text(if (reset) "Reset password" else if (signup) "Create account" else "Log in",
                    style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                if (reset) Text("We’ll email you a link to reset your password.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                if (signup) {
                    OutlinedTextField(name, { name = it }, label = { Text("Full name") },
                        singleLine = true, enabled = !isLoading, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                        isError = attempted && name.isBlank(),
                        supportingText = { if (attempted && name.isBlank()) Text("Enter your name.") })
                }
                OutlinedTextField(email, { email = it }, label = { Text("Email address") },
                    singleLine = true, enabled = !isLoading, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = (attempted || emailAttempted) && emailError,
                    supportingText = { if ((attempted || emailAttempted) && emailError) Text("Enter a valid email address.") })
                if (!reset) {
                    OutlinedTextField(password, { password = it }, label = { Text("Password") },
                        singleLine = true, enabled = !isLoading, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") } },
                        isError = attempted && passwordError,
                        supportingText = { if (signup) Text("Use at least 8 characters.")
                            else if (attempted && passwordError) Text("Enter your password.") })
                    if (signup) OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm password") },
                        singleLine = true, enabled = !isLoading, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = attempted && confirm != password,
                        supportingText = { if (attempted && confirm != password) Text("Passwords must match.") })
                    else TextButton(onClick = { navigate("reset") }, enabled = !isLoading,
                        modifier = Modifier.align(Alignment.End)) { Text("Forgot password?") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp)) }
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    attempted = true
                    if (!emailError && (reset || !passwordError) && (!signup || (name.isNotBlank() && confirm == password))) {
                        if (reset) onResetPassword(email.trim())
                        else if (signup) onSignUp(name.trim(), email.trim(), password)
                        else onLogin(email.trim(), password)
                    }
                }, enabled = !isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium) {
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp)); Text("Please wait…")
                    } else Text(if (reset) "Send reset link" else if (signup) "Create account" else "Log in")
                }
                if (!reset) TextButton(onClick = { navigate(if (signup) "login" else "signup") },
                    enabled = !isLoading, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (signup) "Already have an account? Log in" else "Create an account")
                }
                if (!reset) TextButton(
                    onClick = {
                        emailAttempted = true
                        if (!emailError) onResendConfirmation(email.trim())
                    },
                    enabled = !isLoading && resendSeconds == 0,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (resendSeconds > 0) "Resend confirmation in ${resendSeconds}s" else "Resend confirmation email")
                }
            }
        }
    }
}
