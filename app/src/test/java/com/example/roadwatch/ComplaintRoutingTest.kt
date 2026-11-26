package com.example.roadwatch

import com.example.roadwatch.data.reports.Capture
import com.example.roadwatch.ui.scan.ComplaintLocation
import com.example.roadwatch.ui.scan.RoadAuthority
import com.example.roadwatch.ui.scan.coordinates
import com.example.roadwatch.ui.scan.suggestAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComplaintRoutingTest {
    @Test fun selectsKnownRoadsFromCaptureLocation() {
        assertEquals(RoadAuthority.KENHA, suggestAuthority(ComplaintLocation("A 2", "Kiambu", true), 15f))
        assertEquals(RoadAuthority.KENHA, suggestAuthority(ComplaintLocation("Nairobi–Thika Superhighway", "Kiambu", true), 15f))
        assertEquals(RoadAuthority.KURA, suggestAuthority(ComplaintLocation("Outer Ring Road", "Nairobi", true), 15f))
        assertEquals(RoadAuthority.COUNTY, suggestAuthority(ComplaintLocation("Waudo Road", "Nairobi", true), 15f))
        assertEquals(RoadAuthority.KERRA, suggestAuthority(ComplaintLocation("RWC 1062", "Uasin Gishu", true), 15f))
    }

    @Test fun requiresConfirmationWithoutAReliableRoadLead() {
        assertNull(suggestAuthority(ComplaintLocation("A2", "Kiambu", true), 60f))
        assertNull(suggestAuthority(ComplaintLocation("A2", "Kiambu", true), null))
        assertNull(suggestAuthority(ComplaintLocation("A2", "Kiambu", false), 10f))
        assertNull(suggestAuthority(ComplaintLocation("Outer Ring Road", "Mombasa", true), 10f))
        assertNull(suggestAuthority(ComplaintLocation("A2 Service Road", "Kiambu", true), 10f))
        assertNull(suggestAuthority(ComplaintLocation("C67", "Kiambu", true), 10f))
        assertNull(suggestAuthority(ComplaintLocation("Unknown Road", "Nairobi", true), 10f))
    }

    @Test fun formatsOnlyValidCapturedCoordinates() {
        val capture = Capture("capture", "report", "", "[]", -1.2345678, 36.8765432)
        assertEquals("-1.234568, 36.876543", capture.coordinates())
        assertNull(capture.copy(latitude = null).coordinates())
        assertNull(capture.copy(longitude = Double.NaN).coordinates())
        assertNull(capture.copy(latitude = 91.0).coordinates())
    }
}
