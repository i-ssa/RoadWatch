package com.example.roadwatch

import androidx.test.platform.app.InstrumentationRegistry
import com.example.roadwatch.data.auth.RoadWatchBackend
import com.example.roadwatch.ui.map.HazardRepository
import io.github.jan.supabase.auth.auth
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in, read-only verification against the deployed feed using the device's saved account. */
class LiveCommunityMapTest {
    @Test fun signedInDeviceCanRefreshDeployedFeed() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveAccount")=="true")
        withTimeout(60_000) {
            RoadWatchBackend.restore()
            val client=checkNotNull(RoadWatchBackend.client)
            val owner=checkNotNull(client.auth.currentUserOrNull()?.id) { "Sign in on this test device first" }
            val repository=HazardRepository(InstrumentationRegistry.getInstrumentation().targetContext,owner)
            repository.refresh()
            assertTrue(repository.updatedAt()>0)
            val response=client.httpClient.get("${BuildConfig.SUPABASE_URL}/rest/v1/map_hazards") {
                header("apikey",BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                bearerAuth(checkNotNull(client.auth.currentAccessTokenOrNull()))
                parameter("select","id,latitude,longitude,reporter_count")
                parameter("limit",500)
            }
            assertEquals(200,response.status.value)
            val rows=JSONArray(response.bodyAsText())
            for(i in 0 until rows.length()) {
                val row=rows.getJSONObject(i)
                assertNotNull(HazardRepository.point(row.getString("id"),row.getDouble("latitude"),row.getDouble("longitude"),"synced"))
                assertTrue(row.getInt("reporter_count")>0)
            }
            val anonymous=client.httpClient.get("${BuildConfig.SUPABASE_URL}/rest/v1/map_hazards?select=id&limit=1") {
                header("apikey",BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            }
            assertTrue(anonymous.status.value in listOf(401,403))
            println("LIVE_COMMUNITY_FEED_VERIFIED=true; REMOTE_HAZARDS=${rows.length()}")
        }
    }
}
