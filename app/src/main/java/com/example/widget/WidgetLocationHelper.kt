package com.example.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.PreferencesManager
import com.example.data.model.LocationItem
import java.util.Locale

object WidgetLocationHelper {

    private const val TAG = "WidgetLocationHelper"

    /**
     * Resolves the target location for the widget based on user preferences.
     * Defaults to imprecise GPS/network location, falling back to fixed location or app location.
     */
    fun getWidgetLocation(context: Context, prefs: PreferencesManager): LocationItem {
        val useGps = prefs.isWidgetGpsEnabled()
        if (!useGps) {
            return prefs.getWidgetFixedLocation()
        }

        // GPS / Imprecise location mode (Default)
        val gpsLoc = getImpreciseLocation(context)
        if (gpsLoc != null) {
            Log.d(TAG, "Using imprecise GPS location for widget: ${gpsLoc.name} (${gpsLoc.latitude}, ${gpsLoc.longitude})")
            return gpsLoc
        }

        // Fallback if location fix not yet available
        Log.d(TAG, "Imprecise GPS location unavailable, falling back to fixed/selected location")
        return prefs.getWidgetFixedLocation()
    }

    /**
     * Obtains the last known coarse/imprecise location (Network or Passive provider preferred for battery & privacy).
     */
    fun getImpreciseLocation(context: Context): LocationItem? {
        try {
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasCoarse && !hasFine) {
                Log.d(TAG, "Location permissions not granted for widget GPS refresh")
                return null
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

            // Coarse / Imprecise providers first
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
                LocationManager.GPS_PROVIDER
            )

            var bestLocation: Location? = null
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = try {
                        locationManager.getLastKnownLocation(provider)
                    } catch (_: SecurityException) {
                        null
                    }
                    if (loc != null) {
                        if (bestLocation == null || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            }

            if (bestLocation != null) {
                val resolvedName = resolveLocationName(context, bestLocation.latitude, bestLocation.longitude) ?: "Current Location"
                return LocationItem(
                    id = "widget_gps_current",
                    name = resolvedName,
                    region = null,
                    country = "United Kingdom",
                    latitude = bestLocation.latitude,
                    longitude = bestLocation.longitude,
                    timezone = "Europe/London",
                    isCurrentLocation = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting imprecise location for widget", e)
        }
        return null
    }

    private fun resolveLocationName(context: Context, lat: Double, lon: Double): String? {
        return try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.UK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    val addr = addresses?.firstOrNull()
                    addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea ?: addr?.featureName
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    val addr = addresses?.firstOrNull()
                    addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea ?: addr?.featureName
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
