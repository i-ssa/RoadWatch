package com.example.roadwatch.ui.scan

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.roadwatch.data.reports.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun ReportsScreen(owner: String) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val store=remember { ReportStore.get(context) }
    var reports by remember(owner) { mutableStateOf(emptyList<ScanReport>()) }
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
            } catch(_: Exception) { feedback="Couldn't update this report. Please try again." }
            finally { busy=false }
        }
    }
    LaunchedEffect(owner) {
        withContext(Dispatchers.IO) { ReportSyncWorker.schedule(context,owner) }
        while(true) { reports=withContext(Dispatchers.IO) { store.reports(owner) }; delay(2000) }
    }
    Text("Saved reports",style=MaterialTheme.typography.headlineSmall)
    Text("Drafts stay on this device until you review and submit them. Remove incorrect captures before submitting.")
    TextButton(onClick={ReportSyncWorker.schedule(context,owner)}) { Text("Retry sync") }
    feedback?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    if(reports.isEmpty()) Text("No scans yet. Start a scan from Home.")
    reports.forEach { report -> key(report.id) {
        Card(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(DateFormat.getDateTimeInstance().format(Date(report.started)))
                Text("${report.detections} captures · "+when(report.status) { "synced" -> "Synced"; "recording" -> "Scan in progress"; "draft" -> "Draft · review before submitting"; else -> "Saved on device · waiting to sync" })
                report.syncError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
                TextButton(onClick={selected=if(selected==report.id) null else report.id}) { Text("Review captures") }
                if(selected==report.id) {
                    CaptureList(store,report.id,revision,editable=report.status=="draft" && !busy) { capture ->
                        change { check(store.removeCapture(capture.id,report.id,owner)) }
                    }
                    if(report.status=="draft") {
                        Text("Submitting uploads this report when connected and shares its pothole locations with other signed-in users. Photos remain private. Submitted reports cannot be edited here.")
                        Button(enabled=!busy,onClick={change {
                            check(store.submit(report.id,owner))
                            ReportSyncWorker.schedule(context,owner)
                        }}) { Text("Submit report") }
                    }
                }
                if(report.status in listOf("draft","synced")) {
                    TextButton(enabled=!busy,onClick={deleting=report}) {
                        Text(if(report.status=="synced") "Remove from this device" else "Delete draft")
                    }
                }
            }
        }
    } }
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
