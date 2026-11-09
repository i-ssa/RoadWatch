package com.example.roadwatch.ui.scan

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.roadwatch.data.reports.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun ReportsScreen(owner: String, onStartScan: (() -> Unit)? = null) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val store=remember { ReportStore.get(context) }
    var reports by remember(owner) { mutableStateOf<List<ScanReport>?>(null) }
    var selected by remember(owner) { mutableStateOf<String?>(null) }
    var deleting by remember(owner) { mutableStateOf<ScanReport?>(null) }
    var feedback by remember(owner) { mutableStateOf<String?>(null) }
    var busy by remember(owner) { mutableStateOf(false) }
    var revision by remember(owner) { mutableIntStateOf(0) }
    fun change(action: () -> Unit) {
        if(busy) return
        busy=true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                reports=withContext(Dispatchers.IO) { store.reports(owner) }
                revision++
            } catch (e: CancellationException) { throw e }
            catch(_: Exception) { feedback="Couldn't update this report. Please try again." }
            finally { busy=false }
        }
    }
    LaunchedEffect(owner) {
        withContext(Dispatchers.IO) { ReportSyncWorker.schedule(context,owner) }
        while(true) { reports=withContext(Dispatchers.IO) { store.reports(owner) }; delay(2000) }
    }
    feedback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    when (val saved = reports) {
        null -> LinearProgressIndicator(Modifier.fillMaxWidth())
        else -> if (saved.isEmpty()) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    JourneyGlyph("report", MaterialTheme.colorScheme.primary)
                    Text("No reports yet", style = MaterialTheme.typography.titleLarge)
                    Text("Scans appear here as drafts for you to review.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    onStartScan?.let { Button(onClick = it, shape = MaterialTheme.shapes.medium) { Text("Start scan") } }
                }
            }
        } else {
            if (saved.any { it.status == "pending" }) {
                OutlinedButton(onClick = { ReportSyncWorker.schedule(context, owner) }) { Text("Retry sync") }
            }
            saved.forEach { report -> key(report.id) {
                val expanded = selected == report.id
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(report.started)),
                            style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            val status = when (report.status) {
                                "draft" -> "Draft"
                                "synced" -> "Submitted"
                                "recording" -> "Scanning"
                                else -> "Waiting to sync"
                            }
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(status, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                            }
                            Text(if (report.detections == 1) "1 capture" else "${report.detections} captures",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        report.syncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        OutlinedButton(onClick = { selected = if (expanded) null else report.id },
                            shape = MaterialTheme.shapes.medium) {
                            Text(if (expanded) "Hide captures" else "Review captures")
                        }
                        if (expanded) {
                            HorizontalDivider()
                            CaptureList(store, report.id, revision, editable = report.status == "draft" && !busy) { capture ->
                                change { check(store.removeCapture(capture.id, report.id, owner)) }
                            }
                            if (report.status == "draft") {
                                Text("Submitting shares hazard locations with signed-in users. Your photos stay private.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                Button(enabled = !busy, onClick = { change {
                                    check(store.submit(report.id, owner))
                                    ReportSyncWorker.schedule(context, owner)
                                } }, shape = MaterialTheme.shapes.medium) { Text("Submit report") }
                            }
                            if (report.status == "draft" || report.status == "synced") {
                                TextButton(enabled = !busy, onClick = { deleting = report }) {
                                    Text(if (report.status == "synced") "Remove from this device" else "Delete draft")
                                }
                            }
                        }
                    }
                }
            } }
        }
    }
    deleting?.let { report ->
        AlertDialog(onDismissRequest={deleting=null},title={Text(if(report.status=="draft") "Delete draft?" else "Remove local copy?")},
            text={Text(if(report.status=="draft") "This permanently removes the draft and its images from this device." else "This frees space on this device. The submitted report and images remain in your cloud account.")},
            confirmButton={TextButton(onClick={deleting=null; change { check(store.deleteLocalReport(report.id,owner)) }}) { Text("Remove") }},
            dismissButton={TextButton(onClick={deleting=null}) { Text("Cancel") }})
    }
}

@Composable
private fun CaptureList(store: ReportStore, reportId: String, revision: Int, editable: Boolean, remove: (Capture) -> Unit) {
    var captures by remember(reportId) { mutableStateOf(emptyList<Capture>()) }
    var page by remember(reportId) { mutableIntStateOf(0) }
    LaunchedEffect(reportId,revision) { captures=withContext(Dispatchers.IO) { store.captures(reportId) } }
    if(captures.isEmpty()) Text("No captures in this report.")
    val pageCount=((captures.size+4)/5).coerceAtLeast(1)
    val currentPage=page.coerceAtMost(pageCount-1)
    captures.drop(currentPage*5).take(5).forEach { capture -> key(capture.id) {
        CaptureImage(capture)
        if(editable) TextButton(onClick={remove(capture)}) { Text("Remove capture") }
        HorizontalDivider(Modifier.padding(vertical=12.dp))
    } }
    if(pageCount>1) {
        Text("Captures · page ${currentPage+1} of $pageCount")
        Row {
            TextButton(enabled=currentPage>0,onClick={page=currentPage-1}) { Text("Previous captures") }
            TextButton(enabled=currentPage<pageCount-1,onClick={page=currentPage+1}) { Text("Next captures") }
        }
    }
}
