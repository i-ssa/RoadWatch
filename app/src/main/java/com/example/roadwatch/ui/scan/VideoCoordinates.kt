package com.example.roadwatch.ui.scan

import android.location.Location

internal data class VideoCoordinates(val latitude: Double, val longitude: Double) {
    fun asLocation() = Location("video metadata").apply {
        latitude = this@VideoCoordinates.latitude
        longitude = this@VideoCoordinates.longitude
    }

    companion object {
        private val decimalLocation = Regex(
            """([+-][0-9]{1,2}(?:\.[0-9]+)?)([+-][0-9]{1,3}(?:\.[0-9]+)?)(?:[+-][0-9]+(?:\.[0-9]+)?)?/?""",
        )

        fun parse(value: String?): VideoCoordinates? {
            val match = value?.trim()?.let(decimalLocation::matchEntire) ?: return null
            return from(match.groupValues[1].toDoubleOrNull(), match.groupValues[2].toDoubleOrNull())
        }

        fun from(latitude: Double?, longitude: Double?): VideoCoordinates? {
            if (latitude == null || latitude !in -90.0..90.0 ||
                longitude == null || longitude !in -180.0..180.0
            ) return null
            return VideoCoordinates(latitude, longitude)
        }
    }
}
