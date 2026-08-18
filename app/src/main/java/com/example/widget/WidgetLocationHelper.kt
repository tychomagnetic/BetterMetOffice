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
import java.util.TimeZone

object WidgetLocationHelper {

    private const val TAG = "WidgetLocationHelper"

    /**
     * Resolves the target location for the widget based on user preferences.
     * Returns null when GPS mode is selected but permission/a location fix is unavailable.
     * It must not silently substitute a fixed or app-selected location.
     */
    fun getWidgetLocation(context: Context, prefs: PreferencesManager): LocationItem? {
        val useGps = prefs.isWidgetGpsEnabled()
        if (!useGps) {
            return prefs.getWidgetFixedLocation()
        }

        if (!hasLocationPermission(context)) {
            Log.d(TAG, "Location permissions not granted for widget GPS refresh")
            return null
        }

        // Prefer a newly available fix for the next forecast fetch. If Android's
        // while-in-use restriction prevents that background lookup, retain the
        // last GPS location whose widget forecast completed successfully.
        val gpsLoc = getImpreciseLocation(context)
        if (gpsLoc != null) {
            Log.d(TAG, "Using imprecise GPS location for widget: ${gpsLoc.name} (${gpsLoc.latitude}, ${gpsLoc.longitude})")
            return gpsLoc
        }

        val cachedGpsLocation = getLastSuccessfulGpsLocation(prefs)
        if (cachedGpsLocation != null) {
            Log.d(TAG, "Live GPS unavailable; refreshing the last successful widget GPS location")
            return cachedGpsLocation
        }

        Log.d(TAG, "Imprecise GPS location unavailable and no successful GPS widget location is cached")
        return null
    }

    /**
     * Returns the stable location associated with the currently displayed GPS
     * forecast. A fresh coordinate is not exposed here until its fetch succeeds,
     * preventing a widget redraw from pairing a new place with old weather.
     */
    fun getWidgetDisplayLocation(context: Context, prefs: PreferencesManager): LocationItem? {
        if (!prefs.isWidgetGpsEnabled()) return prefs.getWidgetFixedLocation()
        if (!hasLocationPermission(context)) return null
        return getLastSuccessfulGpsLocation(prefs) ?: getImpreciseLocation(context)
    }

    fun commitSuccessfulGpsLocation(prefs: PreferencesManager, location: LocationItem) {
        if (prefs.isWidgetGpsEnabled() && location.isCurrentLocation) {
            prefs.setCachedWidgetGpsLocation(location)
        }
    }

    private fun getLastSuccessfulGpsLocation(prefs: PreferencesManager): LocationItem? =
        prefs.getCachedWidgetGpsLocation()
            ?: prefs.getCachedWidgetWeatherReport()?.location?.takeIf { it.isCurrentLocation }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Obtains the last known coarse/imprecise location (Network or Passive provider preferred for battery & privacy).
     */
    fun getImpreciseLocation(context: Context): LocationItem? {
        try {
            if (!hasLocationPermission(context)) {
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
                    country = null,
                    latitude = bestLocation.latitude,
                    longitude = bestLocation.longitude,
                    timezone = TimeZone.getDefault().id,
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
