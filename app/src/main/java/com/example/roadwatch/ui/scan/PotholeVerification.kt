package com.example.roadwatch.ui.scan

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import com.example.roadwatch.BuildConfig
import com.example.roadwatch.data.auth.RoadWatchBackend
import io.github.jan.supabase.auth.auth
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal enum class Verdict { CONFIRMED, REJECTED, UNCERTAIN }

internal data class RetainedDetection(val box: Detection, val verified: Boolean)

internal fun retainDetections(boxes: List<Detection>, verdicts: List<Verdict>): List<RetainedDetection> =
    boxes.mapIndexedNotNull { index, box ->
        when (verdicts.getOrNull(index)) {
            Verdict.REJECTED -> null
            Verdict.CONFIRMED -> RetainedDetection(box, true)
            else -> RetainedDetection(box, false)
        }
    }

/** Called serially on the camera analysis executor, only for positive YOLO frames. */
internal class PotholeVerification(private val owner: String) {
    private var nextAttempt = 0L
    private var refreshSession = false

    fun verify(frame: Bitmap, boxes: List<Detection>): List<Verdict> {
        if (boxes.isEmpty() || SystemClock.elapsedRealtime() < nextAttempt) return emptyList()
        val client = RoadWatchBackend.client ?: return emptyList()
        if (client.auth.currentUserOrNull()?.id != owner) return emptyList()

        nextAttempt = SystemClock.elapsedRealtime() + 12_000
        return try {
            val candidates = boxes.take(3)
            val payload = JSONObject().put("frame", jpeg(frame, 640)).put("candidates", JSONArray().apply {
                for (box in candidates) {
                    put(JSONObject()
                        .put("box", JSONArray(listOf(box.x, box.y, box.width, box.height)))
                        .put("crop", paddedCrop(frame, box)))
                }
            })
            runBlocking {
                withTimeout(5_500) {
                    if (refreshSession) RoadWatchBackend.sessionLock.withLock {
                        if (client.auth.currentUserOrNull()?.id == owner) {
                            client.auth.refreshCurrentSession()
                            refreshSession = false
                        }
                    }
                    val session = client.auth.currentSessionOrNull() ?: return@withTimeout emptyList()
                    if (session.user?.id != owner) return@withTimeout emptyList()
                    val response = client.httpClient.post(
                        "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/verify-potholes"
                    ) {
                        expectSuccess = false
                        header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                        bearerAuth(session.accessToken)
                        contentType(ContentType.Application.Json)
                        setBody(payload.toString())
                    }
                    when (response.status.value) {
                        200 -> parseVerdicts(response.bodyAsText(), candidates.size)
                        401 -> {
                            refreshSession = true
                            emptyList()
                        }
                        429 -> {
                            val seconds = response.headers["Retry-After"]?.toLongOrNull()?.coerceIn(12, 3600) ?: 60
                            nextAttempt = SystemClock.elapsedRealtime() + seconds * 1000
                            emptyList()
                        }
                        else -> {
                            nextAttempt = SystemClock.elapsedRealtime() + 30_000
                            emptyList()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            nextAttempt = SystemClock.elapsedRealtime() + 30_000
            emptyList()
        }
    }

    private fun parseVerdicts(body: String, count: Int): List<Verdict> {
        val values = JSONObject(body).getJSONArray("verdicts")
        check(values.length() == count)
        return List(count) { index ->
            when (values.getString(index)) {
                "confirmed" -> Verdict.CONFIRMED
                "rejected" -> Verdict.REJECTED
                "uncertain" -> Verdict.UNCERTAIN
                else -> error("Invalid verification verdict")
            }
        }
    }

    private fun paddedCrop(frame: Bitmap, box: Detection): String {
        val left = floor((box.x - box.width / 2) * frame.width).toInt().coerceIn(0, frame.width - 1)
        val top = floor((box.y - box.height / 2) * frame.height).toInt().coerceIn(0, frame.height - 1)
        val right = ceil((box.x + 1.5f * box.width) * frame.width).toInt().coerceIn(left + 1, frame.width)
        val bottom = ceil((box.y + 1.5f * box.height) * frame.height).toInt().coerceIn(top + 1, frame.height)
        val crop = Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
        return try {
            jpeg(crop, 384)
        } finally {
            if (crop !== frame) crop.recycle()
        }
    }

    private fun jpeg(source: Bitmap, maxSide: Int): String {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(source.width, source.height))
        val image = Bitmap.createScaledBitmap(source, (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1), true)
        return try {
            ByteArrayOutputStream().use { output ->
                check(image.compress(Bitmap.CompressFormat.JPEG, 75, output))
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            }
        } finally {
            if (image !== source) image.recycle()
        }
    }
}
