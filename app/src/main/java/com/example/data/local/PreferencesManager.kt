package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.LocationItem
import com.example.data.model.PressureUnit
import com.example.data.model.TemperatureUnit
import com.example.data.model.WeatherReport
import com.example.data.model.WindSpeedUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    fun isMetOfficePreferred(): Boolean {
        return prefs.getBoolean(KEY_USE_MET_OFFICE, true)
    }

    fun setMetOfficePreferred(useMetOffice: Boolean) {
        prefs.edit().putBoolean(KEY_USE_MET_OFFICE, useMetOffice).apply()
        _useMetOfficeSourceFlow.value = useMetOffice
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

    fun getWidgetPageOffset(): Int {
        return prefs.getInt(KEY_WIDGET_PAGE_OFFSET, 0)
    }

    fun setWidgetPageOffset(offset: Int) {
        prefs.edit().putInt(KEY_WIDGET_PAGE_OFFSET, offset.coerceAtLeast(0)).apply()
    }

    companion object {
        private const val PREFS_NAME = "met_office_weather_prefs"
        private const val KEY_MET_OFFICE_API_KEY = "met_office_api_key"
        private const val KEY_MET_OFFICE_SECRET = "met_office_secret"
        private const val KEY_SELECTED_LOCATION = "selected_location"
        private const val KEY_FAVORITE_LOCATIONS = "favorite_locations"
        private const val KEY_TEMP_UNIT = "temp_unit"
        private const val KEY_WIND_UNIT = "wind_unit"
        private const val KEY_PRESSURE_UNIT = "pressure_unit"
        private const val KEY_USE_MET_OFFICE = "use_met_office_source"
        private const val KEY_CACHED_WEATHER_REPORT = "cached_weather_report"
        private const val KEY_WIDGET_PAGE_OFFSET = "widget_page_offset"
    }
}
