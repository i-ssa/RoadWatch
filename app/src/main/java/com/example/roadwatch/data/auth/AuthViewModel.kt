package com.example.roadwatch.data.auth

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.roadwatch.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.event.AuthEvent
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class AuthUiState(
    val restoring: Boolean = true,
    val signedIn: Boolean = false,
    val recovering: Boolean = false,
    val loading: Boolean = false,
    val resendSeconds: Int = 0,
    val error: String? = null,
    val message: String? = null,
)

@OptIn(SupabaseExperimental::class)
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("auth_flow", 0)
    private val mutableState = MutableStateFlow(AuthUiState(recovering = preferences.getBoolean("recovering", false)))
    val state = mutableState.asStateFlow()
    private val client = if (BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            install(Auth) { scheme = "roadwatch"; host = "auth" }
        }
    } else null

    init {
        if (client == null) mutableState.update { it.copy(restoring = false) }
        else {
            viewModelScope.launch {
                client.auth.events.collect { event ->
                    if (event is AuthEvent.OtpError) {
                        mutableState.update { it.copy(error = "This email link is invalid or has expired. Request a new link.") }
                    }
                }
            }
            viewModelScope.launch {
                client.auth.sessionStatus.collect { status ->
                    mutableState.update { old ->
                        when (status) {
                            SessionStatus.Initializing -> old.copy(restoring = true)
                            is SessionStatus.Authenticated -> old.copy(restoring = false, signedIn = true)
                            is SessionStatus.NotAuthenticated -> old.copy(restoring = false, signedIn = false)
                            is SessionStatus.RefreshFailure -> old.copy(restoring = false, signedIn = false,
                                error = "Unable to restore your session. Check your connection and sign in again.")
                        }
                    }
                }
            }
        }
    }

    fun clearFeedback() { mutableState.update { it.copy(error = null, message = null) } }

    private fun request(block: suspend () -> Unit) {
        if (state.value.loading) return
        if (client == null) {
            mutableState.update { it.copy(error = "Add the Supabase project URL and public key to local.properties, then rebuild the app.") }
            return
        }
        mutableState.update { it.copy(loading = true, error = null, message = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // Never display raw response bodies, URLs, or session tokens.
                val detail = e.message.orEmpty().lowercase()
                val message = when {
                    "invalid_credentials" in detail || "invalid login" in detail -> "Email or password is incorrect."
                    "email_not_confirmed" in detail || "email not confirmed" in detail -> "Confirm your email before signing in."
                    "weak_password" in detail -> "Choose a stronger password."
                    "rate" in detail || "429" in detail -> "Too many attempts. Please wait and try again."
                    "expired" in detail || "otp" in detail -> "This link has expired. Request a new reset email."
                    else -> "We couldn't complete that request. Check your connection and try again."
                }
                mutableState.update { it.copy(error = message) }
            } finally { mutableState.update { it.copy(loading = false) } }
        }
    }

    fun login(email: String, password: String) = request {
        client!!.auth.signInWith(Email) { this.email = email; this.password = password }
        setRecovery(false)
    }

    fun signUp(name: String, email: String, password: String) = request {
        client!!.auth.signUpWith(Email, redirectUrl = "roadwatch://auth") {
            this.email = email; this.password = password
            data = buildJsonObject { put("full_name", name) }
        }
        startResendCooldown()
        mutableState.update { it.copy(message = "Check your email for a confirmation link, then sign in.") }
    }

    private fun startResendCooldown() {
        if (state.value.resendSeconds > 0) return
        mutableState.update { it.copy(resendSeconds = 60) }
        viewModelScope.launch {
            while (state.value.resendSeconds > 0) {
                delay(1000)
                mutableState.update { it.copy(resendSeconds = (it.resendSeconds - 1).coerceAtLeast(0)) }
            }
        }
    }

    fun resendConfirmation(email: String) {
        if (state.value.resendSeconds > 0) return
        request {
            // SDK 3.2.6 resendEmail has no redirect parameter; supply it explicitly here.
            val response = client!!.httpClient.request("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/resend") {
                method = HttpMethod.Post
                url { parameters.append("redirect_to", "roadwatch://auth") }
                contentType(ContentType.Application.Json)
                header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setBody(buildJsonObject {
                    put("type", "signup")
                    put("email", email.trim())
                }.toString())
            }
            if (response.status.value == 429) {
                startResendCooldown()
                error("429")
            }
            check(response.status.value in 200..299) { "Confirmation resend failed" }
            startResendCooldown()
            mutableState.update { it.copy(message = "If this email has an unconfirmed account, a new confirmation link has been sent. Open the newest email on this device.") }
        }
    }

    fun resetPassword(email: String) = request {
        client!!.auth.resetPasswordForEmail(email, redirectUrl = "roadwatch://auth")
        mutableState.update { it.copy(message = "If an account exists for this email, you'll receive a password reset link.") }
    }

    fun updatePassword(password: String) = request {
        check(state.value.recovering && state.value.signedIn)
        client!!.auth.updateUser { this.password = password }
        client.auth.signOut()
        setRecovery(false)
        mutableState.update { it.copy(signedIn = false, message = "Password updated. Sign in with your new password.") }
    }

    fun signOut() = request {
        client!!.auth.signOut()
        setRecovery(false)
        mutableState.update { it.copy(signedIn = false) }
    }

    private fun setRecovery(value: Boolean) {
        preferences.edit().putBoolean("recovering", value).apply()
        mutableState.update { it.copy(recovering = value) }
    }

    fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme != "roadwatch" || uri.host != "auth") return
        val fragment = Uri.parse("https://callback.invalid/?${uri.fragment.orEmpty()}")
        if (fragment.getQueryParameter("error") != null || uri.getQueryParameter("error") != null) {
            mutableState.update { it.copy(error = "This email link is invalid or has expired. Request a new link.") }
            intent.data = null
            return
        }
        if (fragment.getQueryParameter("type") == "recovery" || uri.getQueryParameter("type") == "recovery") {
            setRecovery(true)
        }
        client?.handleDeeplinks(intent)
        intent.data = null
    }
}
