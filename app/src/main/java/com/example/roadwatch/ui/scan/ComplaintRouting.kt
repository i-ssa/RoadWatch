package com.example.roadwatch.ui.scan

import com.example.roadwatch.data.reports.Capture
import java.util.Locale

internal const val COUNTY_DIRECTORY = "https://www.parliament.go.ke/the-senate/counties"
internal const val KRB_REPORTING_MAP = "https://maps.krb.go.ke/kenya-roads-board12769/maps/110400/2-road-network-conditions-map"

internal enum class ComplaintPortal(val label: String, val url: String, val formAnchor: String = "") {
    KENHA("KeNHA", "https://kenha.co.ke/report-an-incident/"),
    KURA("KURA", "https://kura.go.ke/report-incident/"),
    KERRA("KeRRA", "https://kerra.go.ke/kerra-complaint-handling-form/"),
    MOMBASA("Mombasa County", "https://eportal.mombasa.go.ke/", "#contact-form"),
}

internal enum class RoadAuthority(val label: String, val portal: ComplaintPortal?, val email: String?) {
    KENHA("KeNHA", ComplaintPortal.KENHA, "complaints@kenha.co.ke"),
    KURA("KURA", ComplaintPortal.KURA, "info@kura.go.ke"),
    KERRA("KeRRA", ComplaintPortal.KERRA, "dg@kerra.go.ke"),
    COUNTY("County government", null, null),
}

internal data class CountyContact(
    val name: String,
    val url: String,
    val email: String,
    val portal: ComplaintPortal? = null,
)

// County-specific channels checked against the county websites. Other counties use the government directory.
internal val countyContacts = listOf(
    CountyContact("Nairobi", "https://nairobi.go.ke/faqs-and-help-desks", "info@nairobi.go.ke"),
    CountyContact("Mombasa", ComplaintPortal.MOMBASA.url, "info@mombasa.go.ke", ComplaintPortal.MOMBASA),
)

internal data class ComplaintLocation(val road: String = "", val county: String = "", val inKenya: Boolean = false)

private val trunkReference = Regex("[SAB]\\s*[0-9]{1,3}", RegexOption.IGNORE_CASE)

// Named road leads from published works/maintenance records. The reporter confirms the section.
private val highwayNames = setOf("thika superhighway", "nairobi thika superhighway", "uhuru highway", "waiyaki way")
private val kuraNairobiNames = setOf("outer ring road", "outering road", "jogoo road", "red hill link road")
private val countyNairobiNames = setOf("galole street", "galole street road", "waudo road", "muinami road")

internal fun suggestAuthority(location: ComplaintLocation, accuracy: Float?): RoadAuthority? {
    if (!location.inKenya || accuracy == null || !accuracy.isFinite() || accuracy <= 0f || accuracy > 35f) return null
    val road = location.road.trim().lowercase(Locale.ROOT).replace(Regex("[\\s–-]+"), " ")
    return when {
        trunkReference.matches(road) || road in highwayNames -> RoadAuthority.KENHA
        location.county.equals("Nairobi", ignoreCase = true) && road in kuraNairobiNames -> RoadAuthority.KURA
        location.county.equals("Nairobi", ignoreCase = true) && road in countyNairobiNames -> RoadAuthority.COUNTY
        location.county.equals("Uasin Gishu", ignoreCase = true) &&
            road in setOf("rwc 1062", "danger lengut road") -> RoadAuthority.KERRA
        else -> null
    }
}

internal fun Capture.coordinates(): String? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return String.format(Locale.ROOT, "%.6f, %.6f", lat, lon)
}
