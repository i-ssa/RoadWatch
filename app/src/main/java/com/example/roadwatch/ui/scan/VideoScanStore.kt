package com.example.roadwatch.ui.scan

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

internal data class SavedVideoScan(
    val directory: File,
    val candidates: List<VideoCandidate>,
    val verdicts: Map<Int, VideoVerdict> = emptyMap(),
    val draftSaved: Boolean = false,
    val coordinates: VideoCoordinates? = null,
    val fileName: String? = null,
    val durationMs: Long = 0,
    val isPhoto: Boolean = false,
)

/** Keeps the latest completed media scan for this account in app-private storage. */
internal class VideoScanStore(context: Context, owner: String) {
    private val root = File(context.filesDir, "video-scans/${UUID.nameUUIDFromBytes(owner.toByteArray())}")
    private val index = AtomicFile(File(root, "latest.json"))

    fun newScanDirectory(): File {
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdirs())
        return directory
    }

    fun restore(): SavedVideoScan? {
        val data = try {
            JSONObject(index.readFully().decodeToString())
        } catch (_: FileNotFoundException) {
            return null
        }
        val directory = File(root, UUID.fromString(data.getString("directory")).toString())
        val rows = data.getJSONArray("candidates")
        val verdicts = mutableMapOf<Int, VideoVerdict>()
        val candidates = List(rows.length()) { position ->
            val row = rows.getJSONObject(position)
            val timeMs = row.getLong("time")
            val box = row.getJSONObject("box")
            row.optJSONObject("verification")?.let {
                verdicts[position] = VideoVerdict(it.getString("status"), it.getString("reason"))
            }
            VideoCandidate(timeMs, Detection(
                box.getDouble("x").toFloat(), box.getDouble("y").toFloat(),
                box.getDouble("width").toFloat(), box.getDouble("height").toFloat(),
                box.getDouble("confidence").toFloat(),
            ), File(directory, "$timeMs.jpg"))
        }
        val coordinates = data.optJSONObject("coordinates")?.let {
            VideoCoordinates.from(it.optDouble("latitude"), it.optDouble("longitude"))
        }
        return SavedVideoScan(
            directory, candidates, verdicts, data.optBoolean("draftSaved"), coordinates,
            data.optString("fileName").takeIf { it.isNotBlank() }, data.optLong("durationMs"),
            data.optBoolean("isPhoto"),
        )
    }

    fun save(scan: SavedVideoScan) {
        val rows = JSONArray()
        scan.candidates.forEachIndexed { position, candidate ->
            val row = JSONObject().put("time", candidate.timeMs).put("box", candidate.box.json())
            scan.verdicts[position]?.let { verdict ->
                row.put("verification", JSONObject().put("status", verdict.status).put("reason", verdict.reason))
            }
            rows.put(row)
        }
        val json = JSONObject()
            .put("directory", scan.directory.name)
            .put("candidates", rows)
            .put("draftSaved", scan.draftSaved)
            .put("fileName", scan.fileName)
            .put("durationMs", scan.durationMs)
            .put("isPhoto", scan.isPhoto)
        scan.coordinates?.let {
            json.put("coordinates", JSONObject().put("latitude", it.latitude).put("longitude", it.longitude))
        }
        val output = index.startWrite()
        try {
            output.write(json.toString().encodeToByteArray())
            index.finishWrite(output)
        } catch (e: Exception) {
            index.failWrite(output)
            throw e
        }
    }
}
