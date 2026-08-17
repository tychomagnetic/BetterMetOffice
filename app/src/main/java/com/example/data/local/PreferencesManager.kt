package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.LocationItem
import com.example.data.model.PressureUnit
import com.example.data.model.TemperatureUnit
import com.example.data.model.ForecastSource
import com.example.data.model.WeatherDataSource
import com.example.data.model.WeatherReport
import com.example.data.model.WidgetRefreshInterval
import com.example.data.model.WindSpeedUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val locationListAdapter = moshi.adapter<List<LocationItem>>(
        Types.newParameterizedType(List::class.java, LocationItem::class.java)
    )
    private val locationAdapter = moshi.adapter(LocationItem::class.java)
    private val weatherReportAdapter = moshi.adapter(WeatherReport::class.java)

    private val _apiKeyFlow = MutableStateFlow(getApiKey())
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    private val _clientSecretFlow = MutableStateFlow(getClientSecret())
    val clientSecretFlow: StateFlow<String> = _clientSecretFlow.asStateFlow()

    private val _selectedLocationFlow = MutableStateFlow(getSelectedLocation())
    val selectedLocationFlow: StateFlow<LocationItem> = _selectedLocationFlow.asStateFlow()

    private val _favoriteLocationsFlow = MutableStateFlow(getFavoriteLocations())
    val favoriteLocationsFlow: StateFlow<List<LocationItem>> = _favoriteLocationsFlow.asStateFlow()

    private val _tempUnitFlow = MutableStateFlow(getTemperatureUnit())
    val tempUnitFlow: StateFlow<TemperatureUnit> = _tempUnitFlow.asStateFlow()

    private val _windUnitFlow = MutableStateFlow(getWindSpeedUnit())
    val windUnitFlow: StateFlow<WindSpeedUnit> = _windUnitFlow.asStateFlow()

    private val _pressureUnitFlow = MutableStateFlow(getPressureUnit())
    val pressureUnitFlow: StateFlow<PressureUnit> = _pressureUnitFlow.asStateFlow()

    private val _useMetOfficeSourceFlow = MutableStateFlow(isMetOfficePreferred())
    val useMetOfficeSourceFlow: StateFlow<Boolean> = _useMetOfficeSourceFlow.asStateFlow()

    private val _forecastSourceFlow = MutableStateFlow(getForecastSource())
    val forecastSourceFlow: StateFlow<ForecastSource> = _forecastSourceFlow.asStateFlow()

    private val _bpfApiKeyFlow = MutableStateFlow(getBpfApiKey())
    val bpfApiKeyFlow: StateFlow<String> = _bpfApiKeyFlow.asStateFlow()

    private val _widgetRefreshIntervalFlow = MutableStateFlow(getWidgetRefreshInterval())
    val widgetRefreshIntervalFlow: StateFlow<WidgetRefreshInterval> = _widgetRefreshIntervalFlow.asStateFlow()

    private val _widgetGpsEnabledFlow = MutableStateFlow(isWidgetGpsEnabled())
    val widgetGpsEnabledFlow: StateFlow<Boolean> = _widgetGpsEnabledFlow.asStateFlow()

    private val _widgetFixedLocationFlow = MutableStateFlow(getWidgetFixedLocation())
    val widgetFixedLocationFlow: StateFlow<LocationItem> = _widgetFixedLocationFlow.asStateFlow()

    fun isMetOfficePreferred(): Boolean {
        return prefs.getBoolean(KEY_USE_MET_OFFICE, true)
    }

    fun setMetOfficePreferred(useMetOffice: Boolean) {
        prefs.edit().putBoolean(KEY_USE_MET_OFFICE, useMetOffice).apply()
        _useMetOfficeSourceFlow.value = useMetOffice
        setForecastSource(if (useMetOffice) ForecastSource.MET_OFFICE_SPOT else ForecastSource.OPEN_METEO)
    }

    fun getForecastSource(): ForecastSource {
        val saved = prefs.getString(KEY_FORECAST_SOURCE, null)
        if (saved != null) {
            return try {
                ForecastSource.valueOf(saved)
            } catch (_: Exception) {
                ForecastSource.MET_OFFICE_SPOT
            }
        }
        // Migration for existing installs using the previous two-source switch.
        return if (prefs.getBoolean(KEY_USE_MET_OFFICE, true)) ForecastSource.MET_OFFICE_SPOT else ForecastSource.OPEN_METEO
    }

    fun setForecastSource(source: ForecastSource) {
        prefs.edit()
            .putString(KEY_FORECAST_SOURCE, source.name)
            .putBoolean(KEY_USE_MET_OFFICE, source != ForecastSource.OPEN_METEO)
            .apply()
        _forecastSourceFlow.value = source
        _useMetOfficeSourceFlow.value = source != ForecastSource.OPEN_METEO
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_MET_OFFICE_API_KEY, "") ?: ""
    }

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_MET_OFFICE_API_KEY, key.trim()).apply()
        _apiKeyFlow.value = key.trim()
    }

    fun getClientSecret(): String {
        return prefs.getString(KEY_MET_OFFICE_SECRET, "") ?: ""
    }

    fun setClientSecret(secret: String) {
        prefs.edit().putString(KEY_MET_OFFICE_SECRET, secret.trim()).apply()
        _clientSecretFlow.value = secret.trim()
    }

    fun getBpfApiKey(): String = prefs.getString(KEY_MET_OFFICE_BPF_API_KEY, "") ?: ""

    fun setBpfApiKey(key: String) {
        prefs.edit().putString(KEY_MET_OFFICE_BPF_API_KEY, key.trim()).apply()
        _bpfApiKeyFlow.value = key.trim()
    }

    fun getSelectedLocation(): LocationItem {
        val json = prefs.getString(KEY_SELECTED_LOCATION, null)
        if (json != null) {
            try {
                val item = locationAdapter.fromJson(json)
                if (item != null) return item
            } catch (_: Exception) {
            }
        }
        return LocationItem.DEFAULT_LOCATIONS.first()
    }

    fun setSelectedLocation(location: LocationItem) {
        val json = locationAdapter.toJson(location)
        prefs.edit().putString(KEY_SELECTED_LOCATION, json).apply()
        _selectedLocationFlow.value = location
    }

    fun getFavoriteLocations(): List<LocationItem> {
        val json = prefs.getString(KEY_FAVORITE_LOCATIONS, null)
        if (json != null) {
            try {
                val list = locationListAdapter.fromJson(json)
                if (!list.isNullOrEmpty()) return list
            } catch (_: Exception) {
            }
        }
        return LocationItem.DEFAULT_LOCATIONS.take(5)
    }

    fun saveFavoriteLocations(list: List<LocationItem>) {
        val json = locationListAdapter.toJson(list)
        prefs.edit().putString(KEY_FAVORITE_LOCATIONS, json).apply()
        _favoriteLocationsFlow.value = list
    }

    fun addOrToggleFavorite(location: LocationItem) {
        val current = getFavoriteLocations().toMutableList()
        val index = current.indexOfFirst {
            it.id == location.id || (Math.abs(it.latitude - location.latitude) < 0.01 && Math.abs(it.longitude - location.longitude) < 0.01)
        }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(0, location.copy(isFavorite = true))
        }
        saveFavoriteLocations(current)
    }

    fun getTemperatureUnit(): TemperatureUnit {
        val name = prefs.getString(KEY_TEMP_UNIT, TemperatureUnit.CELSIUS.name)
        return try {
            TemperatureUnit.valueOf(name ?: TemperatureUnit.CELSIUS.name)
        } catch (_: Exception) {
            TemperatureUnit.CELSIUS
        }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        prefs.edit().putString(KEY_TEMP_UNIT, unit.name).apply()
        _tempUnitFlow.value = unit
    }

    fun getWindSpeedUnit(): WindSpeedUnit {
        val name = prefs.getString(KEY_WIND_UNIT, WindSpeedUnit.MPH.name)
        return try {
            WindSpeedUnit.valueOf(name ?: WindSpeedUnit.MPH.name)
        } catch (_: Exception) {
            WindSpeedUnit.MPH
        }
    }

    fun setWindSpeedUnit(unit: WindSpeedUnit) {
        prefs.edit().putString(KEY_WIND_UNIT, unit.name).apply()
        _windUnitFlow.value = unit
    }

    fun getPressureUnit(): PressureUnit {
        val name = prefs.getString(KEY_PRESSURE_UNIT, PressureUnit.HPA.name)
        return try {
            PressureUnit.valueOf(name ?: PressureUnit.HPA.name)
        } catch (_: Exception) {
            PressureUnit.HPA
        }
    }

    fun setPressureUnit(unit: PressureUnit) {
        prefs.edit().putString(KEY_PRESSURE_UNIT, unit.name).apply()
        _pressureUnitFlow.value = unit
    }

    fun getCachedWeatherReport(): WeatherReport? {
        val json = prefs.getString(KEY_CACHED_WEATHER_REPORT, null) ?: return null
        return try {
            weatherReportAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    fun setCachedWeatherReport(report: WeatherReport) {
        try {
            val json = weatherReportAdapter.toJson(report)
            prefs.edit().putString(KEY_CACHED_WEATHER_REPORT, json).apply()
        } catch (_: Exception) {
        }
    }

    fun getFreshCachedBpfWeatherReport(
        location: LocationItem,
        maxAgeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): WeatherReport? {
        val locationCacheKey = bpfCacheKey(location)
        // Only entries written into the current verified, per-location cache are
        // eligible. Older reports did not retain enough server-coordinate data
        // to prove they belong to the requested location.
        val json = prefs.getString(locationCacheKey, null) ?: return null
        val report = try {
            weatherReportAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        } ?: return null

        val ageMillis = nowMillis - report.fetchedAtMillis
        val sameLocation = kotlin.math.abs(report.location.latitude - location.latitude) < 0.0001 &&
            kotlin.math.abs(report.location.longitude - location.longitude) < 0.0001
        val freshReport = report.takeIf {
            it.dataSource == WeatherDataSource.MET_OFFICE_BPF &&
                sameLocation &&
                ageMillis in 0..maxAgeMillis
        } ?: return null

        return freshReport
    }

    fun setCachedBpfWeatherReport(report: WeatherReport) {
        if (report.dataSource != WeatherDataSource.MET_OFFICE_BPF) return
        try {
            val json = weatherReportAdapter.toJson(report)
            prefs.edit().putString(bpfCacheKey(report.location), json).apply()
        } catch (_: Exception) {
        }
    }

    private fun bpfCacheKey(location: LocationItem): String =
        KEY_CACHED_BPF_LOCATION_PREFIX + String.format(
            Locale.US,
            "%.4f_%.4f",
            location.latitude,
            location.longitude
        )

    fun getCachedWidgetWeatherReport(): WeatherReport? {
        val json = prefs.getString(KEY_CACHED_WIDGET_WEATHER_REPORT, null) ?: return null
        return try {
            weatherReportAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    fun setCachedWidgetWeatherReport(report: WeatherReport) {
        try {
            val json = weatherReportAdapter.toJson(report)
            prefs.edit().putString(KEY_CACHED_WIDGET_WEATHER_REPORT, json).apply()
        } catch (_: Exception) {
        }
    }

    fun getWidgetPageOffset(): Int {
        return prefs.getInt(KEY_WIDGET_PAGE_OFFSET, 0)
    }

    fun setWidgetPageOffset(offset: Int) {
        prefs.edit().putInt(KEY_WIDGET_PAGE_OFFSET, offset.coerceAtLeast(0)).apply()
    }

    fun getWidgetRefreshInterval(): WidgetRefreshInterval {
        val name = prefs.getString(KEY_WIDGET_REFRESH_INTERVAL, WidgetRefreshInterval.ONE_HOUR.name)
        return try {
            WidgetRefreshInterval.valueOf(name ?: WidgetRefreshInterval.ONE_HOUR.name)
        } catch (_: Exception) {
            WidgetRefreshInterval.ONE_HOUR
        }
    }

    fun setWidgetRefreshInterval(interval: WidgetRefreshInterval) {
        prefs.edit().putString(KEY_WIDGET_REFRESH_INTERVAL, interval.name).apply()
        _widgetRefreshIntervalFlow.value = interval
    }

    fun isWidgetGpsEnabled(): Boolean {
        // Defaults to true (GPS imprecise location on widget refresh)
        return prefs.getBoolean(KEY_WIDGET_USE_GPS, true)
    }

    fun setWidgetGpsEnabled(useGps: Boolean) {
        prefs.edit().putBoolean(KEY_WIDGET_USE_GPS, useGps).apply()
        _widgetGpsEnabledFlow.value = useGps
    }

    fun getWidgetFixedLocation(): LocationItem {
        val json = prefs.getString(KEY_WIDGET_FIXED_LOCATION, null)
        if (json != null) {
            try {
                val item = locationAdapter.fromJson(json)
                if (item != null) return item
            } catch (_: Exception) {
            }
        }
        // Migration for installs that pre-date the dedicated widget location.
        // Freeze the current app location once instead of dynamically following
        // every later location selected in the main app.
        val initialFixedLocation = getSelectedLocation()
        prefs.edit()
            .putString(KEY_WIDGET_FIXED_LOCATION, locationAdapter.toJson(initialFixedLocation))
            .apply()
        return initialFixedLocation
    }

    fun setWidgetFixedLocation(location: LocationItem) {
        val json = locationAdapter.toJson(location)
        prefs.edit().putString(KEY_WIDGET_FIXED_LOCATION, json).apply()
        _widgetFixedLocationFlow.value = location
    }

    companion object {
        private const val PREFS_NAME = "met_office_weather_prefs"
        private const val KEY_MET_OFFICE_API_KEY = "met_office_api_key"
        private const val KEY_MET_OFFICE_SECRET = "met_office_secret"
        private const val KEY_MET_OFFICE_BPF_API_KEY = "met_office_bpf_api_key"
        private const val KEY_SELECTED_LOCATION = "selected_location"
        private const val KEY_FAVORITE_LOCATIONS = "favorite_locations"
        private const val KEY_TEMP_UNIT = "temp_unit"
        private const val KEY_WIND_UNIT = "wind_unit"
        private const val KEY_PRESSURE_UNIT = "pressure_unit"
        private const val KEY_USE_MET_OFFICE = "use_met_office_source"
        private const val KEY_FORECAST_SOURCE = "forecast_source"
        private const val KEY_CACHED_WEATHER_REPORT = "cached_weather_report"
        private const val KEY_CACHED_WIDGET_WEATHER_REPORT = "cached_widget_weather_report"
        private const val KEY_CACHED_BPF_LOCATION_PREFIX = "cached_bpf_location_v3_"
        private const val KEY_WIDGET_PAGE_OFFSET = "widget_page_offset"
        private const val KEY_WIDGET_REFRESH_INTERVAL = "widget_refresh_interval"
        private const val KEY_WIDGET_USE_GPS = "widget_use_gps"
        private const val KEY_WIDGET_FIXED_LOCATION = "widget_fixed_location"
    }
}
