package com.example.roadwatch.ui.scan

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.roadwatch.data.reports.Capture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

@Composable
internal fun ComplaintReview(capture: Capture, accountEmail: String?, fullName: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val coordinates = capture.coordinates()
    var lookup by remember { mutableStateOf<ComplaintLocation?>(null) }
    var recipient by remember { mutableStateOf<RoadAuthority?>(null) }
    var chosenManually by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(fullName.orEmpty()) }
    var email by remember { mutableStateOf(accountEmail.orEmpty()) }
    var landmark by remember { mutableStateOf("") }
    var editingLocation by remember { mutableStateOf(false) }
    var countyOverride by remember { mutableStateOf<String?>(null) }
    var choosingCounty by remember { mutableStateOf(false) }
    var webForm by remember { mutableStateOf(false) }
    val date = remember(capture) {
        capture.capturedAt.takeIf { it > 0 }?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
    }
    val photo = remember(capture) {
        File(capture.path).takeIf { it.isFile }?.let { file ->
            runCatching { FileProvider.getUriForFile(context, "${context.packageName}.complaint.photos", file) }.getOrNull()
        }
    }
    LaunchedEffect(capture.id) {
        val location = if (coordinates == null) ComplaintLocation()
            else findComplaintLocation(context, capture.latitude!!, capture.longitude!!)
        if (!editingLocation) landmark = listOf(location.road, location.county).filter { it.isNotBlank() }.distinct().joinToString(", ")
        lookup = location
        if (!chosenManually) recipient = suggestAuthority(location, capture.accuracy)
    }
    val locationText = listOfNotNull(landmark.trim().takeIf { it.isNotEmpty() }, coordinates).joinToString(" · ")
    val fields = ComplaintFields(name.trim(), email.trim(), locationText, buildString {
        appendLine("Please inspect the reported pothole and arrange any needed repairs.")
        if (locationText.isNotEmpty()) appendLine("Location: $locationText")
        if (coordinates != null) {
            appendLine("Map: https://www.google.com/maps/search/?api=1&query=${Uri.encode(coordinates)}")
            capture.accuracy?.takeIf { it.isFinite() && it > 0f }?.let { appendLine("GPS accuracy: about ${it.toInt()} m") }
        }
        if (date != null) appendLine("Observed: $date")
        append("Prepared in RoadWatch AI.")
    })
    val selected = recipient
    val countyName = countyOverride ?: lookup?.county.orEmpty()
    val county = countyContacts.firstOrNull { it.name.equals(countyName, ignoreCase = true) }
    val portal = if (selected == RoadAuthority.COUNTY) county?.portal else selected?.portal
    val website = if (selected == RoadAuthority.COUNTY) county?.url ?: COUNTY_DIRECTORY else portal?.url
    val recipientEmail = if (selected == RoadAuthority.COUNTY) county?.email else selected?.email

    if (webForm && portal != null) {
        ComplaintForm(portal, fields, photo, onClose = { webForm = false },
            onCopy = { copyComplaint(context, fields) },
            onBrowser = { website?.let { openWebsite(context, it) } })
        return
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Report pothole to authority") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    coordinates == null -> Text("This capture has no GPS fix. Enter the road and a nearby landmark.")
                    capture.accuracy == null -> Text("Video GPS tag: $coordinates. Check that it matches the pothole’s location.")
                    lookup == null -> Text("Checking the road near this capture…")
                    else -> Text("GPS: $coordinates")
                }
                OutlinedTextField(landmark, { landmark = it; editingLocation = true }, label = { Text(if (coordinates == null) "Road or landmark" else "Road or landmark (optional)") })
                val suggested = lookup?.let { suggestAuthority(it, capture.accuracy) }
                if (suggested != null) Text("Suggested recipient: ${suggested.label}. Check that it manages this road section.")
                else if (lookup != null) Text("Choose the road manager. If you’re unsure, check the Kenya Roads Board map.")
                RoadAuthority.entries.forEach { authority ->
                    val label = if (authority == RoadAuthority.COUNTY && countyName.isNotBlank())
                        "$countyName County" else authority.label
                    FilterChip(selected = selected == authority, onClick = {
                        chosenManually = true
                        recipient = authority
                    }, label = { Text(label) })
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Reply email") }, singleLine = true)
                Text("Review these details before opening the complaint. You’ll submit it on the website or send it from your email app.")
                if (selected == RoadAuthority.COUNTY) {
                    Box {
                        TextButton(onClick = { choosingCounty = true }) { Text("Change county") }
                        DropdownMenu(expanded = choosingCounty, onDismissRequest = { choosingCounty = false }) {
                            countyContacts.forEach { contact ->
                                DropdownMenuItem(text = { Text(contact.name) }, onClick = {
                                    countyOverride = contact.name
                                    choosingCounty = false
                                })
                            }
                            DropdownMenuItem(text = { Text("Another county") }, onClick = {
                                countyOverride = ""
                                choosingCounty = false
                            })
                        }
                    }
                    if (county == null) Text("Use the county directory to find its contact channel, then paste the copied report.")
                }
                if (photo != null && recipientEmail != null) Text("The email draft can include this scan photo.")
                if (recipientEmail != null) OutlinedButton(enabled = locationText.isNotBlank(), onClick = { composeComplaint(context, recipientEmail, fields, photo) }) {
                    Text(if (photo != null) "Draft email with photo" else "Draft email")
                }
                TextButton(onClick = { copyComplaint(context, fields) }) { Text("Copy report details") }
                TextButton(onClick = { openWebsite(context, KRB_REPORTING_MAP) }) { Text("Check Kenya Roads Board map") }
            }
        },
        confirmButton = {
            Button(enabled = website != null && locationText.isNotBlank(), onClick = {
                if (portal != null) webForm = true else website?.let { openWebsite(context, it) }
            }) {
                Text(when {
                    portal != null -> "Open official form"
                    county != null -> "County contacts"
                    else -> "County directory"
                })
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Back") } },
    )
}

private suspend fun findComplaintLocation(context: Context, latitude: Double, longitude: Double): ComplaintLocation {
    if (!Geocoder.isPresent()) return ComplaintLocation()
    val address = withTimeoutOrNull(4500) {
        try {
            val geocoder = Geocoder(context, Locale.ENGLISH)
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(addresses.firstOrNull())
                        override fun onError(errorMessage: String?) = continuation.resume(null)
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                }
            }
        } catch (_: IOException) { null }
    } ?: return ComplaintLocation()
    val county = address.adminArea.orEmpty().trim().replace(Regex("\\s+County$", RegexOption.IGNORE_CASE), "")
    return ComplaintLocation(address.thoroughfare.orEmpty(), county, address.countryCode.equals("KE", ignoreCase = true))
}

private fun openWebsite(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No web browser is available.", Toast.LENGTH_LONG).show()
    }
}

private fun copyComplaint(context: Context, fields: ComplaintFields) {
    val clip = ClipData.newPlainText("Pothole complaint", fields.asText())
    clip.description.extras = PersistableBundle().apply {
        putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Report copied.", Toast.LENGTH_SHORT).show()
}

private fun composeComplaint(context: Context, recipient: String, fields: ComplaintFields, photo: Uri?) {
    val mailto = Intent(Intent.ACTION_SENDTO, "mailto:$recipient".toUri())
    val intent = if (photo != null) {
        val mailApps = context.packageManager.queryIntentActivities(mailto, 0).map { it.activityInfo.packageName }.toSet()
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, fields.subject)
            putExtra(Intent.EXTRA_TEXT, fields.asText())
            putExtra(Intent.EXTRA_STREAM, photo)
            clipData = ClipData.newUri(context.contentResolver, "Pothole photo", photo)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val drafts = mailApps.map { app -> Intent(share).setPackage(app) }
            .filter { it.resolveActivity(context.packageManager) != null }
        if (drafts.isEmpty()) null else Intent.createChooser(drafts.first(), "Open email draft").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, drafts.drop(1).toTypedArray())
        }
    } else null
    val body = Uri.encode(fields.asText())
    val subject = Uri.encode(fields.subject)
    try {
        if (intent == null && photo != null) {
            Toast.makeText(context, "No compatible email app could attach the scan photo.", Toast.LENGTH_LONG).show()
        }
        context.startActivity(intent ?: Intent(Intent.ACTION_SENDTO, "mailto:$recipient?subject=$subject&body=$body".toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No email app is available. Copy the report details to use another channel.", Toast.LENGTH_LONG).show()
    }
}
