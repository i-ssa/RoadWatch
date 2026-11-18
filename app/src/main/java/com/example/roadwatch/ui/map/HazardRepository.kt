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

/** Shared coordinates only; drafts and cache files remain account-specific. */
class HazardRepository(private val context: Context, private val owner: String) {
    private val file=File(context.filesDir,"community-map-v1-"+MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString("") { "%02x".format(it) }+".json")
    fun updatedAt(): Long=file.takeIf { it.exists() }?.lastModified() ?: 0
    fun points(): JSONArray {
        val points=linkedMapOf<String,JSONObject>()
        val cached=runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
        for(i in 0 until cached.length()) { val point=cached.getJSONObject(i); points[point.getString("id")]=point }
        val store=ReportStore.get(context)
        for(report in store.reports(owner).filter { it.status != "synced" }) for(capture in store.captures(report.id)) {
            point(capture.id,capture.latitude,capture.longitude,"draft")?.let { points[capture.id]=it }
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
        val response=client.httpClient.get("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/map_hazards") {
            header("apikey",BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            bearerAuth(token)
            parameter("select","id,latitude,longitude,reporter_count")
            parameter("order","last_seen.desc"); parameter("limit",500)
        }
        check(response.status.value==200)
        check(generation==RoadWatchBackend.generation.get() && client.auth.currentUserOrNull()?.id==owner)
        val rows=JSONArray(response.bodyAsText())
        val points=JSONArray()
        for(i in 0 until rows.length()) {
            val hazard=rows.getJSONObject(i)
            point(hazard.getString("id"),hazard.optDouble("latitude",Double.NaN),hazard.optDouble("longitude",Double.NaN),"synced")?.let { points.put(it.put("reporter_count",hazard.optInt("reporter_count",1))) }
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
