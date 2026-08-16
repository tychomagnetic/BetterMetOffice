package com.example.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LocationItem
import com.example.data.util.TimezoneUtils
import com.example.ui.components.ApiDebugSheet
import com.example.ui.components.ApiKeyDialog
import com.example.ui.components.DailyForecastCard
import com.example.ui.components.DayHourlyDetailSheet
import com.example.ui.components.HeroWeatherCard
import com.example.ui.components.HourlyForecastRow
import com.example.ui.components.LocationSearchSheet
import com.example.ui.components.UnitSettingsDialog
import com.example.ui.components.WeatherBackground
import com.example.ui.components.WeatherMetricsGrid
import com.example.ui.components.WeatherTopBar
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoHero
import com.example.ui.theme.BentoHeroText
import com.example.ui.theme.BentoPurplePrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val report = uiState.weatherReport

    if (uiState.isSettingsOpen) {
        SettingsScreen(
            viewModel = viewModel,
            onBack = { viewModel.closeSettings() },
            modifier = modifier
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { _ ->
        WeatherBackground(current = report?.current) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                val isSelectedLocFavorite = uiState.favoriteLocations.any {
                    it.id == uiState.selectedLocation.id ||
                    (it.name.equals(uiState.selectedLocation.name, ignoreCase = true) && Math.abs(it.latitude - uiState.selectedLocation.latitude) < 0.05 && Math.abs(it.longitude - uiState.selectedLocation.longitude) < 0.05) ||
                    (Math.abs(it.latitude - uiState.selectedLocation.latitude) < 0.01 && Math.abs(it.longitude - uiState.selectedLocation.longitude) < 0.01)
                } || uiState.selectedLocation.isFavorite

                // Top App Bar
                WeatherTopBar(
                    location = uiState.selectedLocation,
                    isFavorite = isSelectedLocFavorite,
                    dataSource = report?.dataSource,
                    forecastSource = uiState.forecastSource,
                    hasApiKey = uiState.apiKey.isNotBlank(),
                    hasBpfApiKey = uiState.bpfApiKey.isNotBlank(),
                    isRefreshing = uiState.isRefreshing,
                    onLocationClick = { viewModel.openLocationSheet() },
                    onFavoriteToggle = { viewModel.toggleFavorite(uiState.selectedLocation) },
                    onSearchClick = { viewModel.openLocationSheet() },
                    onSettingsClick = { viewModel.openSettings() },
                    onDataSourceSelect = { source -> viewModel.selectForecastSource(source) },
                    onRefresh = { viewModel.loadWeather(isRefresh = true) }
                )

                // Error Notification Banner
                if (uiState.errorMessage != null && report != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFEBEE),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFB71C1C),
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Main Content Body
                if (uiState.isLoading && report == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = BentoPurplePrimary,
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Fetching Met Office Forecast...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = BentoTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                } else if (report != null) {
                    val heroPresentation = buildHeroWeatherPresentation(
                        report = report,
                        selectedDayIndex = uiState.selectedDayIndex
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Hero Card (Current Temp, Condition, High/Low)
                        HeroWeatherCard(
                            current = heroPresentation.weather,
                            periodLabel = heroPresentation.periodLabel,
                            rainLabel = heroPresentation.rainLabel,
                            tempUnit = uiState.tempUnit,
                            windUnit = uiState.windUnit
                        )

                        // Hourly Forecast Timeline (Interactive day selection & scrolling)
                        HourlyForecastRow(
                            dailyList = report.daily,
                            hourlyList = report.hourly,
                            selectedDayIndex = uiState.selectedDayIndex,
                            onSelectDay = { viewModel.selectForecastDay(it) },
                            tempUnit = uiState.tempUnit,
                            windUnit = uiState.windUnit,
                            location = report.location,
                            onOpenDetailSheet = {
                                report.daily.getOrNull(uiState.selectedDayIndex)?.let { day ->
                                    viewModel.openDayDetailSheet(day, uiState.selectedDayIndex)
                                }
                            }
                        )

                        // 7-Day Forecast Card (Clickable rows with detail trigger)
                        DailyForecastCard(
                            dailyList = report.daily,
                            tempUnit = uiState.tempUnit,
                            selectedDayIndex = uiState.selectedDayIndex,
                            onDayClick = { index, day ->
                                viewModel.openDayDetailSheet(day, index)
                            }
                        )

                        // Detailed Meteorological Metrics Grid
                        val todayDaily = report.daily.firstOrNull()
                        WeatherMetricsGrid(
                            current = report.current,
                            windUnit = uiState.windUnit,
                            pressureUnit = uiState.pressureUnit,
                            sunrise = todayDaily?.sunrise,
                            sunset = todayDaily?.sunset
                        )

                        // Attribution & Model Run Footer
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp, horizontal = 24.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = BentoPurplePrimary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Data Source: ${report.dataSource.displayName}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = BentoTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            val modelRunMillis = TimezoneUtils.parseIsoToMillis(report.modelRunTime)
                            val timestampMillis = modelRunMillis ?: report.fetchedAtMillis
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (modelRunMillis != null) {
                                    "Model run: ${formatFooterTimestamp(timestampMillis, report.location)}"
                                } else {
                                    "Data updated: ${formatFooterTimestamp(timestampMillis, report.location)}"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = BentoTextSecondary.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                } else {
                    // Empty / Error State with Retry
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Unable to Load Forecast",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.errorMessage ?: "Please check network connection or verify your Met Office API credentials.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = BentoTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadWeather() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BentoPurplePrimary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }

        // Dialogs & Sheets
        if (uiState.isApiKeyDialogOpen) {
            ApiKeyDialog(
                currentApiKey = uiState.apiKey,
                currentClientSecret = uiState.clientSecret,
                isTesting = uiState.isTestingApiKey,
                testResult = uiState.apiKeyTestStatus,
                onSaveKey = { key, secret -> viewModel.saveApiKey(key, secret) },
                onTestKey = { key, secret -> viewModel.testApiKey(key, secret) },
                onDismiss = { viewModel.closeApiKeyDialog() }
            )
        }

        if (uiState.isLocationSheetOpen) {
            LocationSearchSheet(
                searchQuery = uiState.searchQuery,
                searchResults = uiState.searchResults,
                isSearching = uiState.isSearching,
                favoriteLocations = uiState.favoriteLocations,
                currentSelectedId = uiState.selectedLocation.id,
                onQueryChange = { viewModel.searchLocations(it) },
                onLocationSelect = { viewModel.selectLocation(it) },
                onGpsSelect = { lat, lon, name -> viewModel.setGpsLocation(lat, lon, name) },
                onDismiss = { viewModel.closeLocationSheet() }
            )
        }

        if (uiState.isUnitsDialogOpen) {
            UnitSettingsDialog(
                currentTempUnit = uiState.tempUnit,
                currentWindUnit = uiState.windUnit,
                currentPressureUnit = uiState.pressureUnit,
                onTempUnitChange = { viewModel.setTemperatureUnit(it) },
                onWindUnitChange = { viewModel.setWindSpeedUnit(it) },
                onPressureUnitChange = { viewModel.setPressureUnit(it) },
                onDismiss = { viewModel.closeUnitsDialog() }
            )
        }

        if (uiState.isDayDetailSheetOpen && report != null) {
            DayHourlyDetailSheet(
                dailyList = report.daily,
                allHourlyList = report.hourly,
                location = report.location,
                selectedDayIndex = uiState.selectedDayIndex,
                tempUnit = uiState.tempUnit,
                windUnit = uiState.windUnit,
                pressureUnit = uiState.pressureUnit,
                onSelectDay = { viewModel.selectForecastDay(it) },
                onDismiss = { viewModel.closeDayDetailSheet() }
            )
        }

        if (uiState.isDebugSheetOpen) {
            ApiDebugSheet(
                isOpen = uiState.isDebugSheetOpen,
                debugInfo = uiState.debugInfo,
                currentLocation = uiState.selectedLocation,
                customLatInput = uiState.customLatInput,
                customLonInput = uiState.customLonInput,
                coordinateTestResult = uiState.coordinateTestResult,
                isTestingCoordinates = uiState.isTestingCoordinates,
                rawGeocodingQueryInput = uiState.rawGeocodingQueryInput,
                rawGeocodingResultJson = uiState.rawGeocodingResultJson,
                rawGeocodingLocations = uiState.rawGeocodingLocations,
                isTestingGeocoding = uiState.isTestingGeocoding,
                onClose = { viewModel.closeDebugSheet() },
                onRefreshCurrent = { viewModel.loadWeather(isRefresh = true) },
                onUpdateCustomLat = { viewModel.updateCustomLat(it) },
                onUpdateCustomLon = { viewModel.updateCustomLon(it) },
                onNudgeCoordinates = { latD, lonD -> viewModel.nudgeCoordinates(latD, lonD) },
                onRunCoordinateTest = { lat, lon -> viewModel.runCoordinateTest(lat, lon) },
                onUpdateGeocodingQuery = { viewModel.updateRawGeocodingQuery(it) },
                onSelectLocationFromGeocode = { loc ->
                    viewModel.selectLocation(loc)
                    viewModel.closeDebugSheet()
                }
            )
        }
    }
}

private fun formatFooterTimestamp(timestampMillis: Long, location: LocationItem): String {
    return SimpleDateFormat("EEE d MMM, h:mm a z", Locale.getDefault()).apply {
        timeZone = TimezoneUtils.getTimeZoneForLocation(location)
    }.format(Date(timestampMillis))
}
