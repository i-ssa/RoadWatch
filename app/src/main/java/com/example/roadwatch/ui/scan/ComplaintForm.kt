package com.example.roadwatch.ui.scan

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import org.json.JSONObject

internal data class ComplaintFields(
    val name: String,
    val email: String,
    val location: String,
    val details: String,
) {
    val subject = "Pothole report"
    fun asText() = buildString {
        appendLine(subject)
        if (name.isNotBlank()) appendLine("Name: $name")
        if (email.isNotBlank()) appendLine("Reply email: $email")
        append(details)
    }
}

private data class FormFields(val form: String, val values: Map<String, String>)

private fun ComplaintPortal.formFields(fields: ComplaintFields): FormFields {
    val name = fields.name.trim().split(Regex("\\s+"), limit = 2)
    val first = name.firstOrNull().orEmpty()
    val last = name.getOrNull(1).orEmpty()
    return when (this) {
        ComplaintPortal.KENHA -> FormFields("#forminator-module-20851", mapOf(
            "name-1-first-name" to first, "name-1-last-name" to last, "email-1" to fields.email,
            "text-1" to "Pothole", "text-2" to fields.location, "text-3" to fields.subject,
            "textarea-1" to fields.details,
        ))
        ComplaintPortal.KURA -> FormFields("#wpforms-form-14552", mapOf(
            "wpforms[fields][0][first]" to first, "wpforms[fields][0][last]" to last,
            "wpforms[fields][1]" to fields.email, "wpforms[fields][3]" to "Pothole",
            "wpforms[fields][4]" to fields.location, "wpforms[fields][2]" to fields.details,
        ))
        ComplaintPortal.KERRA -> FormFields("#forminator-module-7784", mapOf(
            "text-1" to fields.name, "text-2" to fields.subject, "text-6" to fields.name,
            "email-1" to fields.email, "textarea-1" to fields.details,
        ))
        ComplaintPortal.MOMBASA -> FormFields("#contact-form", mapOf(
            "name" to fields.name, "email" to fields.email, "subject" to fields.subject, "message" to fields.details,
        ))
    }
}

private fun matchesForm(expected: Uri, actual: Uri): Boolean =
    actual.scheme == "https" && actual.encodedAuthority == expected.encodedAuthority &&
        actual.encodedPath == expected.encodedPath && actual.encodedQuery == null

// Guard in the page too, since a navigation can happen between onPageFinished and evaluation.
internal fun ComplaintPortal.prefillScript(fields: ComplaintFields): String {
    val form = formFields(fields)
    val expected = url.toUri()
    return """
        (() => {
            if (location.origin !== ${JSONObject.quote("https://${expected.host}")} ||
                location.pathname !== ${JSONObject.quote(expected.path)} || location.search) return 0;
            const form = document.querySelector(${JSONObject.quote(form.form)});
            if (!form) return 0;
            const values = ${JSONObject(form.values).toString()};
            let matched = 0;
            for (const field of form.elements) {
                if (!Object.prototype.hasOwnProperty.call(values, field.name) ||
                    !(field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement) ||
                    !['text', 'email', 'textarea'].includes(field.type)) continue;
                if (!field.value && values[field.name]) {
                    field.value = values[field.name];
                    field.dispatchEvent(new Event('input', { bubbles: true }));
                    field.dispatchEvent(new Event('change', { bubbles: true }));
                }
                matched++;
            }
            return matched === Object.keys(values).length ? matched : 0;
        })();
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled") // Required by the three allowlisted government forms.
@Composable
internal fun ComplaintForm(
    portal: ComplaintPortal,
    fields: ComplaintFields,
    photo: Uri?,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onBrowser: () -> Unit,
) {
    val expected = remember(portal) { portal.url.toUri() }
    val script = remember(portal, fields) { portal.prefillScript(fields) }
    var status by remember { mutableStateOf("Loading the official form…") }
    var picker by remember { mutableStateOf<AlertDialog?>(null) }
    var prefilled by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.95f)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("${portal.label} complaint", style = MaterialTheme.typography.titleLarge)
                    Text(status)
                    if (portal == ComplaintPortal.KENHA && photo != null) {
                        Text("Use Upload Photo on the form to attach the selected scan photo.")
                    }
                    Row {
                        TextButton(onClick = onCopy) { Text("Copy details") }
                        TextButton(onClick = onBrowser) { Text("Browser") }
                        TextButton(onClick = onClose) { Text("Close") }
                    }
                }
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    return request.isForMainFrame && !matchesForm(expected, request.url)
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    if (prefilled || !matchesForm(expected, url.toUri())) return
                                    view.evaluateJavascript(script) { result ->
                                        prefilled = result.toIntOrNull()?.let { it > 0 } == true
                                        status = if (prefilled) "Review the fields and submit on the authority’s website."
                                        else "Some fields may need manual entry. You can copy the report details above."
                                    }
                                }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (request.isForMainFrame) status = "The official form couldn’t load. Try opening it in your browser."
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onShowFileChooser(
                                    webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams,
                                ): Boolean {
                                    if (photo == null || portal != ComplaintPortal.KENHA ||
                                        !matchesForm(expected, webView.url.orEmpty().toUri())) {
                                        callback.onReceiveValue(null)
                                        return true
                                    }
                                    picker?.dismiss()
                                    var confirmed = false
                                    picker = AlertDialog.Builder(context)
                                        .setTitle("Attach this scan photo?")
                                        .setMessage("KeNHA’s website will receive the selected pothole photo.")
                                        .setPositiveButton("Attach photo") { _, _ ->
                                            confirmed = matchesForm(expected, webView.url.orEmpty().toUri())
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .setOnDismissListener {
                                            picker = null
                                            callback.onReceiveValue(if (confirmed) arrayOf(photo) else null)
                                        }
                                        .show()
                                    return true
                                }
                            }
                            loadUrl(portal.url + portal.formAnchor)
                        }
                    },
                    onRelease = {
                        picker?.dismiss()
                        it.stopLoading()
                        it.destroy()
                    },
                )
            }
        }
    }
}
