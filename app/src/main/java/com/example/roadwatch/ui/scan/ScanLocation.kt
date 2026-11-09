package com.example.roadwatch.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/** Foreground-only fixes. Never substitutes an old coordinate when GPS is unavailable. */
class ScanLocation(private val context: Context) : AutoCloseable {
    private val manager=context.getSystemService(LocationManager::class.java)
    @Volatile private var latest: Location?=null
    private val listener=object: LocationListener {
        override fun onLocationChanged(location: Location) { latest=Location(location) }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Legacy Android callback")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    }
    fun start() {
        val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!fine && !coarse) return
        for(provider in listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER)) {
            try {
                if(manager.isProviderEnabled(provider)) manager.requestLocationUpdates(provider,2000L,0f,listener,Looper.getMainLooper())
            } catch(_: SecurityException) { /* Approximate permission may exclude GPS. */ }
            catch(_: IllegalArgumentException) { /* Provider unavailable on this device. */ }
        }
    }
    fun current(): Location? = latest?.takeIf { isFresh(it,SystemClock.elapsedRealtimeNanos()) }?.let { Location(it) }
    override fun close() { manager.removeUpdates(listener); latest=null }
    companion object {
        fun isFresh(location: Location, nowNanos: Long): Boolean {
            val age=nowNanos-location.elapsedRealtimeNanos
            return age in 0..30_000_000_000L && location.hasAccuracy() && location.accuracy in 0f..100f &&
                location.latitude.isFinite() && location.longitude.isFinite() &&
                location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0
        }
    }
}
