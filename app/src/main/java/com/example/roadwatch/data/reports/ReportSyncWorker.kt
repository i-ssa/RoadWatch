package com.example.roadwatch.data.reports

import android.content.Context
import androidx.work.*
import com.example.roadwatch.BuildConfig
import com.example.roadwatch.data.auth.RoadWatchBackend
import io.github.jan.supabase.auth.auth
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.withLock
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
            val generation=RoadWatchBackend.generation.get()
            val token=withTimeout(15000) { RoadWatchBackend.sessionLock.withLock {
                if(client.auth.currentUserOrNull()?.id!=owner || generation!=RoadWatchBackend.generation.get()) throw CancellationException("Account changed")
                client.auth.refreshCurrentSession()
                client.auth.currentAccessTokenOrNull()
            } } ?: return Result.retry()
            fun checkAccount() {
                if(client.auth.currentUserOrNull()?.id!=owner || generation!=RoadWatchBackend.generation.get()) throw CancellationException("Account changed")
            }
            suspend fun send(path: String, body: Any, type: ContentType, upsert: Boolean = false) {
                checkAccount()
                val response = client.httpClient.request("${BuildConfig.SUPABASE_URL.trimEnd('/')}/$path") {
                    method = HttpMethod.Post
                    header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    bearerAuth(token)
                    contentType(type)
                    if (upsert) header("x-upsert", "true")
                    if (path.startsWith("rest/")) header("Prefer", "resolution=merge-duplicates,return=minimal")
                    setBody(body)
                }
                checkAccount()
                if (response.status.value !in 200..299) throw SyncFailure(response.status.value)
            }
            val engine=ReportSyncEngine(store,::checkAccount,
                uploadCapture={ path,bytes -> send("storage/v1/object/roadwatch-captures/$path",bytes,ContentType.Image.JPEG,true) },
                uploadReport={ payload -> send("rest/v1/road_reports?on_conflict=id",payload,ContentType.Application.Json) })
            if(engine.sync(owner)) Result.success() else Result.retry()
        } catch(e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            store.syncError(owner,"Sign-in refresh timed out. Your report is saved and will retry.")
            Result.retry()
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            store.syncError(owner,syncFailureMessage(e))
            Result.retry()
        } // Keep all local data until confirmed by the server.
    }
    companion object {
        fun cancel(context: Context, owner: String) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork("report-sync-$owner")
                cancelUniqueWork("report-recovery-$owner")
            }
        }
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
