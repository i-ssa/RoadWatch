package com.example.roadwatch.data.reports

import android.content.Context
import androidx.work.*
import com.example.roadwatch.BuildConfig
import com.example.roadwatch.data.auth.RoadWatchBackend
import io.github.jan.supabase.auth.auth
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ReportSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.failure()
        val store = ReportStore.get(applicationContext)
        if (store.reports(owner).none { it.status == "pending" }) return Result.success()
        val client = RoadWatchBackend.client ?: run {
            store.syncError(owner, "Cloud sync needs project configuration. Your report is safe on this device.")
            return Result.failure()
        }
        return try {
            RoadWatchBackend.restore()
            RoadWatchBackend.sessionLock.withLock {
                // Never upload account A's reports using account B's session.
                if (client.auth.currentUserOrNull()?.id != owner) {
                    store.syncError(owner, "Sign in to this account to sync its reports.")
                    return@withLock Result.success()
                }
                client.auth.refreshCurrentSession()
                val token = client.auth.currentAccessTokenOrNull() ?: return@withLock Result.retry()
                suspend fun send(path: String, body: Any, type: ContentType, upsert: Boolean = false) {
                    val response = client.httpClient.request("${BuildConfig.SUPABASE_URL.trimEnd('/')}/$path") {
                        method = HttpMethod.Post
                        header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                        bearerAuth(token)
                        contentType(type)
                        if (upsert) header("x-upsert", "true")
                        if (path.startsWith("rest/")) header("Prefer", "resolution=merge-duplicates,return=minimal")
                        setBody(body)
                    }
                    if (response.status.value !in 200..299) throw SyncFailure(response.status.value)
                }
                for (report in store.reports(owner).filter { it.status == "pending" }) {
                    val captures = JSONArray()
                    for (capture in store.captures(report.id)) {
                        val remotePath = "$owner/${report.id}/${capture.id}.jpg"
                        send("storage/v1/object/roadwatch-captures/$remotePath", File(capture.path).readBytes(), ContentType.Image.JPEG, true)
                        captures.put(JSONObject().put("id",capture.id).put("image_path",remotePath).put("boxes",JSONArray(capture.boxes)))
                    }
                    // Stable UUID + upsert makes a retry after a lost response safe.
                    val payload = JSONObject().put("id",report.id).put("user_id",owner)
                        .put("started_at_ms",report.started).put("captures",captures).put("model_version","cleaned-v3-yolo11s")
                    send("rest/v1/road_reports?on_conflict=id", payload.toString(), ContentType.Application.Json)
                    store.markSynced(report.id)
                }
                Result.success()
            }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            store.syncError(owner, when {
                e is SyncFailure && e.code in listOf(401,403) -> "Cloud access was denied. Check your sign-in and project permissions."
                e is SyncFailure && e.code in listOf(400,404) -> "Cloud storage needs setup. Your report is safe on this device."
                else -> "Sync could not finish. Your report is saved and will retry automatically."
            })
            Result.retry()
        } // Keep all local data until confirmed by the server.
    }
    private class SyncFailure(val code: Int) : Exception()
    companion object {
        fun schedule(context: Context, owner: String) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val data = workDataOf("owner" to owner)
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniqueWork("report-sync-$owner", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<ReportSyncWorker>().setInputData(data).setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
            manager.enqueueUniquePeriodicWork("report-recovery-$owner", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReportSyncWorker>(15, TimeUnit.MINUTES).setInputData(data).setConstraints(constraints).build())
        }
    }
}
