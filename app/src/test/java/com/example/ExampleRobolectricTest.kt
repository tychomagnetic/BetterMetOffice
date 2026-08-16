package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.data.model.CurrentWeather
import com.example.data.model.LocationItem
import com.example.data.model.MetOfficeWeatherCode
import com.example.data.model.WeatherDataSource
import com.example.data.model.WeatherReport
import com.example.ui.WeatherViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Met Office Weather (Dev)", appName)
    }

    @Test
    fun `debug sheet state toggles and coordinate updates work`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = WeatherViewModel(app)

        assertFalse(viewModel.uiState.value.isDebugSheetOpen)

        viewModel.openDebugSheet()
        assertTrue(viewModel.uiState.value.isDebugSheetOpen)

        viewModel.updateCustomLat("53.4808")
        viewModel.updateCustomLon("-2.2426")
        assertEquals("53.4808", viewModel.uiState.value.customLatInput)
        assertEquals("-2.2426", viewModel.uiState.value.customLonInput)

        viewModel.closeDebugSheet()
        assertFalse(viewModel.uiState.value.isDebugSheetOpen)
    }

    @Test
    fun `toggle favorite updates state immediately`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = WeatherViewModel(app)

        val currentSelected = viewModel.uiState.value.selectedLocation
        val initialFavoriteState = viewModel.uiState.value.selectedLocation.isFavorite

        viewModel.toggleFavorite(currentSelected)
        assertEquals(!initialFavoriteState, viewModel.uiState.value.selectedLocation.isFavorite)

        viewModel.toggleFavorite(currentSelected)
        assertEquals(initialFavoriteState, viewModel.uiState.value.selectedLocation.isFavorite)
    }

    @Test
    fun `select forecast day updates selected day index`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = WeatherViewModel(app)

        assertEquals(0, viewModel.uiState.value.selectedDayIndex)
        viewModel.selectForecastDay(2)
        assertEquals(2, viewModel.uiState.value.selectedDayIndex)
        viewModel.selectForecastDay(4)
        assertEquals(4, viewModel.uiState.value.selectedDayIndex)
    }

    @Test
    fun `settings screen state and data source toggle work correctly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = WeatherViewModel(app)

        assertFalse(viewModel.uiState.value.isSettingsOpen)
        viewModel.openSettings()
        assertTrue(viewModel.uiState.value.isSettingsOpen)
        viewModel.closeSettings()
        assertFalse(viewModel.uiState.value.isSettingsOpen)

        // Test toggle data source
        viewModel.toggleDataSource(false)
        assertFalse(viewModel.uiState.value.useMetOfficeSource)

        viewModel.toggleDataSource(true)
        assertTrue(viewModel.uiState.value.useMetOfficeSource)
    }

    @Test
    fun `widget refresh is hourly unless explicitly disabled`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = WeatherViewModel(app)

        assertEquals(com.example.data.model.WidgetRefreshInterval.ONE_HOUR, viewModel.uiState.value.widgetRefreshInterval)

        viewModel.setWidgetRefreshInterval(com.example.data.model.WidgetRefreshInterval.TWO_HOURS)
        assertEquals(com.example.data.model.WidgetRefreshInterval.ONE_HOUR, viewModel.uiState.value.widgetRefreshInterval)

        viewModel.setWidgetRefreshInterval(com.example.data.model.WidgetRefreshInterval.FOUR_HOURS)
        assertEquals(com.example.data.model.WidgetRefreshInterval.ONE_HOUR, viewModel.uiState.value.widgetRefreshInterval)

        viewModel.setWidgetRefreshInterval(com.example.data.model.WidgetRefreshInterval.OFF)
        assertEquals(com.example.data.model.WidgetRefreshInterval.OFF, viewModel.uiState.value.widgetRefreshInterval)
    }

    @Test
    fun `BPF cache is per location and expires after two hours`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesManager(context)
        val location = LocationItem(
            id = "cache_test_one",
            name = "Cache Test One",
            latitude = 52.1234,
            longitude = 1.2345,
            country = "United Kingdom"
        )
        val fetchedAt = 1_000_000L
        val report = WeatherReport(
            location = location,
            current = CurrentWeather(
                temperatureCelsius = 18.0,
                feelsLikeCelsius = 17.0,
                weatherCode = MetOfficeWeatherCode.CLOUDY,
                maxTempCelsius = 20.0,
                minTempCelsius = 12.0,
                humidityPercent = 70,
                windSpeedMph = 8.0,
                windGustMph = 12.0,
                windDirectionDegrees = 180,
                precipitationChance = 30,
                uvIndex = 2,
                visibilityMeters = 20_000,
                pressureHpa = 1012.0,
                timestamp = "2026-08-16T12:00:00Z",
                isNight = false
            ),
            hourly = emptyList(),
            daily = emptyList(),
            dataSource = WeatherDataSource.MET_OFFICE_BPF,
            fetchedAtMillis = fetchedAt
        )
        val twoHours = 2L * 60L * 60L * 1000L

        prefs.setCachedBpfWeatherReport(report)

        assertNotNull(
            prefs.getFreshCachedBpfWeatherReport(location, twoHours, fetchedAt + twoHours)
        )
        assertEquals(
            null,
            prefs.getFreshCachedBpfWeatherReport(location, twoHours, fetchedAt + twoHours + 1L)
        )
        assertEquals(
            null,
            prefs.getFreshCachedBpfWeatherReport(
                location.copy(id = "cache_test_two", latitude = 53.1234),
                twoHours,
                fetchedAt + 1L
            )
        )
    }
}
