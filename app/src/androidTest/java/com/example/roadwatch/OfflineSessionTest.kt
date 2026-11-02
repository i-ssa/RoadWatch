package com.example.roadwatch

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlin.time.ExperimentalTime::class)
class OfflineSessionTest {
    @Test fun expiredSavedSessionRestoresWithoutNetworkAndLogoutRemovesIt() = runBlocking {
        fun client() = createSupabaseClient("https://offline-session-test.invalid","public-test-key") {
            install(Auth) {
                autoLoadFromStorage=false
                alwaysAutoRefresh=false
                autoSaveToStorage=true
            }
        }
        val original=client()
        original.auth.awaitInitialization()
        original.auth.clearSession()
        // Expired access token + intentionally unreachable host: restore must never refresh here.
        original.auth.importSession(UserSession(accessToken="test-access",refreshToken="test-refresh",
            expiresIn=-3600,tokenType="bearer",user=UserInfo(id="offline-account",aud="authenticated")),autoRefresh=false)
        original.close()
        val reopened=client()
        try {
            reopened.auth.awaitInitialization()
            assertTrue(reopened.auth.loadFromStorage(autoRefresh=false))
            assertEquals("offline-account",reopened.auth.currentUserOrNull()?.id)
            reopened.auth.clearSession()
            assertNull(reopened.auth.sessionManager.loadSession())
            assertNull(reopened.auth.currentUserOrNull())
        } finally { reopened.auth.clearSession(); reopened.close() }
    }
}
