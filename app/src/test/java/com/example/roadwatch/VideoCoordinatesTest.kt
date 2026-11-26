package com.example.roadwatch

import com.example.roadwatch.ui.scan.VideoCoordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCoordinatesTest {
    @Test
    fun readsSignedDecimalCoordinatesWithOptionalAltitude() {
        assertEquals(VideoCoordinates(-1.2864, 36.8172), VideoCoordinates.parse("-01.2864+036.8172/"))
        assertEquals(VideoCoordinates(37.422, -122.084), VideoCoordinates.parse("+37.422-122.084+015.2/"))
        assertEquals(VideoCoordinates(-1.2864, 36.8172), VideoCoordinates.parse("-1.2864+36.8172"))
    }

    @Test
    fun ignoresMissingMalformedOrOutOfRangeCoordinates() {
        for (tag in listOf(null, "", "Nairobi", "+91.0+036.0/", "-01.0+181.0/", "-0100+03600/")) {
            assertNull(VideoCoordinates.parse(tag))
        }
        assertNull(VideoCoordinates.from(Double.NaN, 36.8172))
    }
}
