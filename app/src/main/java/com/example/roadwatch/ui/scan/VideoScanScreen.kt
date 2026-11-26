package com.example.roadwatch.ui.scan

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.roadwatch.BuildConfig
import com.example.roadwatch.data.reports.ReportStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
internal fun VideoScanScreen(owner: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var operation by remember { mutableStateOf<String?>("Loading scan") }
    var progress by remember { mutableFloatStateOf(0f) }
    val storage = remember(context, owner) { VideoScanStore(context, owner) }
    var scan by remember { mutableStateOf<SavedVideoScan?>(null) }
    val candidates = scan?.candidates
    val verdicts = scan?.verdicts.orEmpty()
    var activeFileName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val saved = scan?.draftSaved == true

    suspend fun checkDetections() {
        var current = scan ?: return
        val pending = current.candidates.indices.filter { it !in current.verdicts }
        if (pending.isEmpty()) return
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            error = "Automatic pothole verification is unavailable in this build."
            return
        }

        operation = "Checking for potholes"
        progress = 0f
        try {
            for (group in pending.chunked(VIDEO_VERIFICATION_BATCH_SIZE)) {
                operation = "Checking for potholes"
                val batchScan = current
                current = withContext(Dispatchers.IO) {
                    val verdicts = verifyVideoCandidates(
                        BuildConfig.GEMINI_API_KEY,
                        group.map { batchScan.candidates[it] },
                    ) {
                        withContext(Dispatchers.Main) { operation = "Waiting for Gemini to accept more requests" }
                    }
                    batchScan.copy(verdicts = batchScan.verdicts + group.zip(verdicts)).also { storage.save(it) }
                }
                scan = current
                progress = (current.verdicts.size - (current.candidates.size - pending.size)).toFloat() / pending.size
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = when ((e as? GeminiHttpException)?.status) {
                503 -> "Pothole verification is temporarily busy. Reopen this screen in a moment to resume."
                429 -> "Pothole verification reached its request limit. Reopen this screen later to resume."
                401, 403 -> "Automatic pothole verification needs attention from the demo organizer."
                else -> "Couldn’t finish checking for potholes. Reopen this screen to resume."
            }
        }
    }

    LaunchedEffect(storage) {
        job = coroutineContext[Job]
        try {
            scan = withContext(Dispatchers.IO) { storage.restore() }
            checkDetections()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn’t reopen the last scan. Choose a photo or video to scan again."
        } finally {
            operation = null
        }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            operation = "Scanning media on this device"
            progress = 0f
            error = null
            activeFileName = null
            val previousDirectory = scan?.directory
            job = scope.launch {
                var isPhoto = false
                try {
                    scan = withContext(Dispatchers.IO) {
                        val fileName = runCatching {
                            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                        }.getOrNull()
                        val mimeType = context.contentResolver.getType(uri)
                        val extensionType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                            fileName.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT),
                        )
                        isPhoto = (mimeType?.startsWith("image/") == true ||
                            (mimeType?.startsWith("video/") != true && extensionType?.startsWith("image/") == true))
                        withContext(Dispatchers.Main) { activeFileName = fileName }
                        val directory = storage.newScanDirectory()
                        try {
                            val result = if (isPhoto) {
                                scanUploadedPhoto(context, uri, directory)
                            } else {
                                scanRecordedVideo(context, uri, directory) { value ->
                                    withContext(Dispatchers.Main) { progress = value }
                                }
                            }
                            result.copy(fileName = fileName).also { storage.save(it) }
                        } catch (e: Exception) {
                            directory.deleteRecursively()
                            throw e
                        }.also { previousDirectory?.deleteRecursively() }
                    }
                    checkDetections()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = if (isPhoto) "Couldn’t scan this photo. Try a JPG or PNG."
                        else "Couldn’t scan this file. Try a JPG, PNG, or a short MP4 that plays on this phone."
                } finally {
                    operation = null
                }
            }
        }
    }
    fun leave() {
        job?.cancel()
        onBack()
    }
    BackHandler { leave() }

    val completed = candidates != null && verdicts.size == candidates.size
    val confirmed = if (completed) candidates.orEmpty().withIndex().filter {
        verdicts[it.index]?.status == "confirmed"
    } else emptyList()
    val scanningMedia = operation == "Scanning media on this device"
    val finished = completed && !scanningMedia
    val heading = when {
        finished && confirmed.size == 1 -> "1 pothole confirmed"
        finished && confirmed.isNotEmpty() -> "${confirmed.size} potholes confirmed"
        finished -> "No potholes confirmed"
        scanningMedia -> "Scanning photo or video"
        scan?.isPhoto == true && operation == null -> "Verification incomplete"
        operation != null && operation != "Loading scan" -> "Checking for potholes"
        else -> "Scan a photo or video"
    }
    val openGallery = {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = ::leave, modifier = Modifier.heightIn(min = 48.dp)) { Text("‹  Back") }
            Text(heading, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    finished && confirmed.isNotEmpty() -> "Review the marked results before saving a draft."
                    finished -> "Choose another photo or video to scan."
                    scanningMedia -> "RoadWatch is scanning the file on this phone."
                    operation == "Checking for potholes" -> "The scan is complete. RoadWatch is verifying the detections."
                    else -> "Choose a photo or video. Images with possible potholes are sent to Google for verification."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val fileName = if (scanningMedia) activeFileName else scan?.fileName
        if (fileName != null) item {
            ScanInfoCard(
                title = fileName,
                detail = scan?.durationMs?.takeIf { it > 0 && !scanningMedia }?.let {
                    val seconds = (it + 500) / 1_000
                    String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
                },
            )
        }
        if (!finished && operation == null) item {
            Button(onClick = openGallery, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (candidates == null) "Choose photo or video" else "Choose another photo or video")
            }
        }
        operation?.let { label ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        if (label != "Loading scan" && label != "Saving draft") {
                            Text("${(progress * 100).toInt()}%")
                        }
                    }
                    if (label == "Loading scan" || label == "Saving draft") {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
                    }
                }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        val photoScan = scan?.takeIf { it.isPhoto && !scanningMedia }
        if (photoScan != null) item {
            val preview = remember(photoScan.directory) {
                BitmapFactory.decodeFile(File(photoScan.directory, "0.jpg").path)?.asImageBitmap()
            }
            if (preview != null) {
                Image(
                    preview, contentDescription = "Uploaded photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                )
            }
        }
        if (confirmed.isNotEmpty() && !scanningMedia) {
            items(confirmed) { (index, candidate) ->
                val verdict = verdicts.getValue(index)
                val image = remember(candidate) {
                    candidate.preview(Color.GREEN).asImageBitmap()
                }
                val seconds = candidate.timeMs / 1000
                val time = String.format(Locale.ROOT, "%d:%02d.%03d", seconds / 60, seconds % 60, candidate.timeMs % 1000)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Image(
                            image,
                            contentDescription = if (photoScan != null) "Confirmed pothole" else "Confirmed pothole at $time",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height)
                                .heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Text(if (photoScan != null) "Confirmed pothole" else "Confirmed · $time", style = MaterialTheme.typography.titleSmall)
                        Text(verdict.reason)
                    }
                }
            }
            item {
                val hasLocation = scan?.coordinates != null
                ScanInfoCard(
                    title = if (hasLocation) "Video GPS tag found" else "Location needed",
                    detail = if (hasLocation) "Check the location if the clip covers more than one road."
                        else "Add a road or landmark when preparing the report.",
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = operation == null && !saved,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    onClick = {
                        operation = "Saving draft"
                        error = null
                        job = scope.launch {
                            try {
                                val current = checkNotNull(scan)
                                scan = withContext(Dispatchers.IO) {
                                    val store = ReportStore.get(context)
                                    val report = store.start(owner)
                                    try {
                                        val location = current.coordinates?.asLocation()
                                        confirmed.forEach { (_, candidate) ->
                                            store.addCapture(report, candidate.imageFile.readBytes(),
                                                PotholeDetector.json(listOf(candidate.box)), location)
                                        }
                                    } finally {
                                        store.finish(report)
                                    }
                                    current.copy(draftSaved = true).also { storage.save(it) }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = "Couldn’t save the complete draft. Check Detections before trying again."
                            } finally {
                                operation = null
                            }
                        }
                    },
                ) { Text(if (saved) "Draft saved" else "Save draft") }
                if (saved) Text("Saved in Detections for review.")
            }
        }
        if (finished) item {
            OutlinedButton(
                enabled = operation == null,
                onClick = openGallery,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Choose another photo or video") }
        }
    }
}

@Composable
private fun ScanInfoCard(title: String, detail: String?) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
