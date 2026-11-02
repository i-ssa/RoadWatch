package com.example.roadwatch.ui.scan

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.roadwatch.data.reports.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun ReportsScreen(owner: String) {
    val context=LocalContext.current
    val store=remember { ReportStore.get(context) }
    var reports by remember(owner) { mutableStateOf(emptyList<ScanReport>()) }
    var selected by remember(owner) { mutableStateOf<String?>(null) }
    LaunchedEffect(owner) {
        withContext(Dispatchers.IO) { ReportSyncWorker.schedule(context,owner) }
        while(true) { reports=withContext(Dispatchers.IO) { store.reports(owner) }; delay(2000) }
    }
    Text("Saved reports",style=MaterialTheme.typography.headlineSmall)
    Text("Reports stay on this device and sync automatically when connected. Only this account can upload them.")
    TextButton(onClick={ReportSyncWorker.schedule(context,owner)}) { Text("Retry sync") }
    if(reports.isEmpty()) Text("No scans yet. Start a scan from Home.")
    reports.forEach { report ->
        Card(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(DateFormat.getDateTimeInstance().format(Date(report.started)))
                Text("${report.detections} captures · "+when(report.status) { "synced" -> "Synced"; "recording" -> "Scan in progress"; else -> "Saved on device · waiting to sync" })
                report.syncError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
                TextButton(onClick={selected=if(selected==report.id) null else report.id}) { Text("Review captures") }
                if(selected==report.id) CaptureList(store,report.id)
            }
        }
    }
}

@Composable
private fun CaptureList(store: ReportStore, reportId: String) {
    var captures by remember(reportId) { mutableStateOf(emptyList<Capture>()) }
    LaunchedEffect(reportId) { captures=withContext(Dispatchers.IO) { store.captures(reportId) } }
    if(captures.isEmpty()) Text("No potholes were detected in this scan.")
    captures.forEach { capture ->
        val bitmap by produceState<android.graphics.Bitmap?>(null,capture.path) {
            value=withContext(Dispatchers.IO) { BitmapFactory.decodeFile(capture.path,BitmapFactory.Options().apply { inSampleSize=2 }) }
        }
        bitmap?.let { Image(it.asImageBitmap(),"Captured road image",Modifier.fillMaxWidth().heightIn(max=240.dp)) }
        Text("Possible pothole · model suggestion. Location and severity have not been measured.")
    }
}
