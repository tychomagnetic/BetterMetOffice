package com.example

import com.example.data.model.CurrentWeather
import com.example.data.model.DailyForecastItem
import com.example.data.model.HourlyForecastItem
import com.example.data.model.LocationItem
import com.example.data.model.MetOfficeWeatherCode
import com.example.data.model.WeatherDataSource
import com.example.data.model.WeatherReport
import com.example.ui.buildHeroWeatherPresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class HeroWeatherPresentationTest {

    private val location = LocationItem(
        id = "test",
        name = "London",
        country = "United Kingdom",
        latitude = 51.5,
        longitude = -0.1,
        timezone = "Europe/London"
    )

    private val current = CurrentWeather(
        temperatureCelsius = 20.0,
        feelsLikeCelsius = 19.0,
        weatherCode = MetOfficeWeatherCode.CLOUDY,
        maxTempCelsius = 22.0,
        minTempCelsius = 14.0,
        humidityPercent = 60,
        windSpeedMph = 8.0,
        windGustMph = 15.0,
        windDirectionDegrees = 180,
        precipitationChance = 8,
        uvIndex = 2,
        visibilityMeters = 20_000,
        pressureHpa = 1015.0,
        timestamp = "2026-08-16T16:00:00Z",
        isNight = false
    )

    private val daily = listOf(
        DailyForecastItem(
            date = "2026-08-16",
            dayOfWeek = "Today",
            dateFormatted = "16 Aug",
            maxTempCelsius = 22.0,
            minTempCelsius = 14.0,
            dayWeatherCode = MetOfficeWeatherCode.CLOUDY,
            nightWeatherCode = MetOfficeWeatherCode.CLOUDY,
            precipitationChance = 71,
            uvIndex = 4,
            maxWindGustMph = 18.0
        ),
        DailyForecastItem(
            date = "2026-08-17",
            dayOfWeek = "Tomorrow",
            dateFormatted = "17 Aug",
            maxTempCelsius = 24.0,
            minTempCelsius = 15.0,
            dayWeatherCode = MetOfficeWeatherCode.OVERCAST,
            nightWeatherCode = MetOfficeWeatherCode.LIGHT_RAIN,
            precipitationChance = 76,
            uvIndex = 5,
            maxWindGustMph = 22.0
        )
    )

    @Test
    fun todayUsesCurrentHourRainProbability() {
        val result = buildHeroWeatherPresentation(report(), selectedDayIndex = 0)

        assertEquals("Now", result.periodLabel)
        assertEquals("Rain now", result.rainLabel)
        assertEquals(8, result.weather.precipitationChance)
        assertEquals(current, result.weather)
    }

    @Test
    fun futureDayUsesLocalNoonConditionsAndDailyPeakRainProbability() {
        val result = buildHeroWeatherPresentation(report(), selectedDayIndex = 1)

        assertEquals("Tomorrow", result.periodLabel)
        assertEquals("Peak rain", result.rainLabel)
        assertEquals(76, result.weather.precipitationChance)
        assertEquals(18.0, result.weather.temperatureCelsius, 0.0)
        assertEquals(MetOfficeWeatherCode.LIGHT_RAIN, result.weather.weatherCode)
        assertEquals(24.0, result.weather.maxTempCelsius, 0.0)
        assertEquals(15.0, result.weather.minTempCelsius, 0.0)
        assertEquals(12.0, result.weather.windSpeedMph, 0.0)
    }

    private fun report() = WeatherReport(
        location = location,
        current = current,
        hourly = listOf(
            // London is on BST: 11:00Z is local noon and should be selected.
            HourlyForecastItem(
                timeLabel = "12 PM",
                fullTime = "2026-08-17T11:00:00Z",
                date = "2026-08-17",
                temperatureCelsius = 18.0,
                feelsLikeCelsius = 17.0,
                weatherCode = MetOfficeWeatherCode.LIGHT_RAIN,
                precipitationChance = 45,
                windSpeedMph = 12.0,
                windDirectionDegrees = 225,
                humidityPercent = 75,
                uvIndex = 3,
                pressureHpa = 1009.0
            ),
            HourlyForecastItem(
                timeLabel = "3 PM",
                fullTime = "2026-08-17T14:00:00Z",
                date = "2026-08-17",
                temperatureCelsius = 22.0,
                feelsLikeCelsius = 21.0,
                weatherCode = MetOfficeWeatherCode.OVERCAST,
                precipitationChance = 76,
                windSpeedMph = 14.0,
                windDirectionDegrees = 240,
                humidityPercent = 68,
                uvIndex = 4,
                pressureHpa = 1008.0
            )
        ),
        daily = daily,
        dataSource = WeatherDataSource.MET_OFFICE_BPF
    )
}
