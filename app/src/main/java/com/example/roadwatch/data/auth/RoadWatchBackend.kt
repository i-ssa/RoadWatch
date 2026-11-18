package com.example.roadwatch.data.auth

import com.example.roadwatch.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One session for the UI and background sync. Loading storage never requires a network. */
object RoadWatchBackend {
    val generation=java.util.concurrent.atomic.AtomicLong(0)
    val sessionLock = Mutex()
    val client = if (BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            install(Auth) {
                scheme = "roadwatch"
                host = "auth"
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                autoSaveToStorage = true
            }
        }
    } else null
    private var restored = false

    suspend fun restore() = sessionLock.withLock {
        if (!restored) {
            client?.auth?.awaitInitialization()
            client?.auth?.loadFromStorage(autoRefresh = false)
            restored = true
        }
    }
}
