package com.example

import com.example.data.model.CurrentWeather
import com.example.data.model.DailyForecastItem
import com.example.data.model.HourlyForecastItem
import com.example.data.model.LocationItem
import com.example.data.model.MetOfficeWeatherCode
import com.example.data.model.WeatherDataSource
import com.example.data.model.WeatherReport
import com.example.data.util.WeatherClockUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WeatherClockUtilsTest {
    @Test
    fun `clock advance moves now marker current conditions and followed day across midnight`() {
        val first = hour("2026-01-01T23:00:00Z", "2026-01-01", 4.0, isNow = true)
        val second = hour("2026-01-02T00:00:00Z", "2026-01-02", 7.0)
        val report = report(listOf(first, second))

        val update = WeatherClockUtils.advance(
            report,
            selectedDayIndex = 0,
            nowMillis = Instant.parse("2026-01-02T00:30:00Z").toEpochMilli()
        )

        assertFalse(update.report.hourly[0].isNow)
        assertTrue(update.report.hourly[1].isNow)
        assertEquals("Now", update.report.hourly[1].timeLabel)
        assertEquals(7.0, update.report.current.temperatureCelsius, 0.0)
        assertEquals(1, update.selectedDayIndex)
        assertEquals("Today", update.report.daily[1].dayOfWeek)
    }

    private fun hour(time: String, date: String, temperature: Double, isNow: Boolean = false) =
        HourlyForecastItem(
            timeLabel = if (isNow) "Now" else "12 AM",
            fullTime = time,
            date = date,
            temperatureCelsius = temperature,
            feelsLikeCelsius = temperature,
            weatherCode = MetOfficeWeatherCode.OVERCAST,
            precipitationChance = 20,
            windSpeedMph = 5.0,
            windDirectionDegrees = 180,
            humidityPercent = 70,
            uvIndex = 0,
            isNow = isNow
        )

    private fun report(hours: List<HourlyForecastItem>): WeatherReport {
        val location = LocationItem.DEFAULT_LOCATIONS.first().copy(timezone = "Europe/London")
        val daily = listOf("2026-01-01", "2026-01-02").map { date ->
            DailyForecastItem(
                date = date,
                dayOfWeek = "",
                dateFormatted = "",
                maxTempCelsius = 8.0,
                minTempCelsius = 2.0,
                dayWeatherCode = MetOfficeWeatherCode.OVERCAST,
                nightWeatherCode = MetOfficeWeatherCode.OVERCAST,
                precipitationChance = 20,
                uvIndex = 0,
                maxWindGustMph = 10.0
            )
        }
        return WeatherReport(
            location = location,
            current = CurrentWeather(
                temperatureCelsius = 4.0,
                feelsLikeCelsius = 4.0,
                weatherCode = MetOfficeWeatherCode.OVERCAST,
                maxTempCelsius = 8.0,
                minTempCelsius = 2.0,
                humidityPercent = 70,
                windSpeedMph = 5.0,
                windGustMph = 10.0,
                windDirectionDegrees = 180,
                precipitationChance = 20,
                uvIndex = 0,
                visibilityMeters = 10_000,
                pressureHpa = 1013.0,
                timestamp = hours.first().fullTime,
                isNight = true
            ),
            hourly = hours,
            daily = daily,
            dataSource = WeatherDataSource.MET_OFFICE_DATAHUB
        )
    }
}
