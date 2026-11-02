package com.example.roadwatch.ui.map

import android.content.Context
import com.example.roadwatch.BuildConfig
import com.example.roadwatch.data.auth.RoadWatchBackend
import com.example.roadwatch.data.reports.ReportStore
import io.github.jan.supabase.auth.auth
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Cache is account-specific and private. RLS restricts cloud reads to the signed-in owner. */
class HazardRepository(private val context: Context, private val owner: String) {
    private val file=File(context.filesDir,"map-"+MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString("") { "%02x".format(it) }+".json")
    fun updatedAt(): Long=file.takeIf { it.exists() }?.lastModified() ?: 0
    fun points(): JSONArray {
        val points=linkedMapOf<String,JSONObject>()
        val cached=runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
        for(i in 0 until cached.length()) { val point=cached.getJSONObject(i); points[point.getString("id")]=point }
        val store=ReportStore.get(context)
        for(report in store.reports(owner)) for(capture in store.captures(report.id)) {
            point(capture.id,capture.latitude,capture.longitude,report.status)?.let { points[capture.id]=it }
        }
        return JSONArray().apply { points.values.forEach { put(it) } }
    }
    suspend fun refresh() {
        val client=checkNotNull(RoadWatchBackend.client)
        RoadWatchBackend.restore()
        val generation=RoadWatchBackend.generation.get()
        val token=withTimeout(15000) { RoadWatchBackend.sessionLock.withLock {
            check(client.auth.currentUserOrNull()?.id==owner)
            client.auth.refreshCurrentSession()
            checkNotNull(client.auth.currentAccessTokenOrNull())
        } }
        val response=client.httpClient.get("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/road_reports") {
            header("apikey",BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            bearerAuth(token)
            parameter("select","id,captures"); parameter("user_id","eq.$owner")
            parameter("order","started_at_ms.desc"); parameter("limit",500)
        }
        check(response.status.value==200)
        check(generation==RoadWatchBackend.generation.get() && client.auth.currentUserOrNull()?.id==owner)
        val rows=JSONArray(response.bodyAsText())
        val points=JSONArray()
        for(i in 0 until rows.length()) {
            val captures=rows.getJSONObject(i).optJSONArray("captures") ?: continue
            for(j in 0 until captures.length()) {
                val capture=captures.getJSONObject(j)
                val lat=capture.optDouble("latitude",Double.NaN)
                val lon=capture.optDouble("longitude",Double.NaN)
                point(capture.getString("id"),lat,lon,"synced")?.let { points.put(it) }
            }
        }
        val atomic=android.util.AtomicFile(file)
        val stream=atomic.startWrite()
        try { stream.write(points.toString().toByteArray()); atomic.finishWrite(stream) }
        catch(e: Exception) { atomic.failWrite(stream); throw e }
    }
    companion object {
        fun point(id: String, lat: Double?, lon: Double?, status: String): JSONObject? {
            if(lat==null || lon==null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return JSONObject().put("id",id).put("lat",lat).put("lon",lon).put("status",status)
        }
    }
}
