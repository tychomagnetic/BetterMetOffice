package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
}

