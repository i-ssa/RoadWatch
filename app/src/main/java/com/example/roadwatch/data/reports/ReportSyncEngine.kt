package com.example.roadwatch.data.reports

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

internal class SyncFailure(val code: Int) : Exception()

internal fun syncFailureMessage(error: Exception): String = when {
    error is TimeoutCancellationException -> "Upload timed out. Your report is saved and will retry automatically."
    error is FileNotFoundException -> "A capture image is missing on this device. This report could not be uploaded."
    error is SyncFailure && error.code in listOf(401,403) -> "Cloud access was denied. Check your sign-in and project permissions."
    error is SyncFailure && error.code in listOf(400,404) -> "Cloud storage needs setup. Your report is safe on this device."
    else -> "Sync could not finish. Your report is saved and will retry automatically."
}

/** The worker supplies authenticated requests; tests can exercise failures without cloud writes. */
internal class ReportSyncEngine(
    private val store: ReportStore,
    private val checkAccount: () -> Unit,
    private val uploadCapture: suspend (path: String, bytes: ByteArray) -> Unit,
    private val uploadReport: suspend (payload: String) -> Unit
) {
    private suspend fun checkActiveAccount() {
        currentCoroutineContext().ensureActive()
        checkAccount()
    }

    private suspend fun send(request: suspend () -> Unit) {
        checkActiveAccount()
        withTimeout(60_000) { request() }
        checkActiveAccount()
    }

    suspend fun sync(owner: String): Boolean {
        checkActiveAccount()
        var complete=true
        for (report in store.reports(owner).filter { it.status=="pending" }) {
            try {
                checkActiveAccount()
                val captures=JSONArray()
                for (capture in store.captures(report.id)) {
                    // IDs and object paths remain unchanged after retries and process restarts.
                    val remotePath="$owner/${report.id}/${capture.id}.jpg"
                    val bytes=File(capture.path).readBytes()
                    send { uploadCapture(remotePath,bytes) }
                    captures.put(JSONObject().put("id",capture.id).put("image_path",remotePath)
                        .put("boxes",JSONArray(capture.boxes))
                        .put("latitude",capture.latitude ?: JSONObject.NULL)
                        .put("longitude",capture.longitude ?: JSONObject.NULL)
                        .put("accuracy_m",capture.accuracy ?: JSONObject.NULL)
                        .put("captured_at_ms",capture.capturedAt))
                }
                val payload=JSONObject().put("id",report.id).put("user_id",owner)
                    .put("started_at_ms",report.started).put("captures",captures)
                    .put("model_version","cleaned-v3-yolo11s")
                send { uploadReport(payload.toString()) }
                // Only a confirmed response permits the local transition to synced.
                store.markSynced(report.id,owner)
            } catch(error: TimeoutCancellationException) {
                // A request timeout is retryable; cancellation of this worker still propagates.
                checkActiveAccount()
                store.syncError(report.id,owner,syncFailureMessage(error))
                complete=false
            } catch(error: CancellationException) {
                throw error
            } catch(error: Exception) {
                checkActiveAccount()
                store.syncError(report.id,owner,syncFailureMessage(error))
                complete=false
            }
        }
        return complete
    }
}
