package com.example

import com.example.data.model.DailyForecastItem
import com.example.data.model.HourlyForecastItem
import com.example.data.model.LocationItem
import com.example.data.model.MetOfficeWeatherCode
import com.example.data.util.RepresentativeWeatherUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class RepresentativeWeatherUtilsTest {
    private val location = LocationItem.DEFAULT_LOCATIONS.first()

    @Test
    fun `future day uses predominant daylight condition rather than first hour`() {
        val daily = listOf(day("2026-08-22"))
        val hourly = listOf(
            hour("2026-08-22T06:00:00Z", MetOfficeWeatherCode.OVERCAST),
            hour("2026-08-22T09:00:00Z", MetOfficeWeatherCode.SUNNY_INTERVALS),
            hour("2026-08-22T12:00:00Z", MetOfficeWeatherCode.SUNNY_INTERVALS),
            hour("2026-08-22T15:00:00Z", MetOfficeWeatherCode.SUNNY_INTERVALS)
        )

        val result = RepresentativeWeatherUtils.applyToDailyForecast(
            daily, hourly, location, utcMillis("2026-08-20T12:00:00Z")
        )

        assertEquals(MetOfficeWeatherCode.SUNNY_INTERVALS, result.single().dayWeatherCode)
    }

    @Test
    fun `today after sunset describes remaining night`() {
        val daily = listOf(day("2026-08-20", sunset = "20:15"))
        val hourly = listOf(
            hour("2026-08-20T12:00:00Z", MetOfficeWeatherCode.SUNNY_INTERVALS),
            hour("2026-08-20T20:00:00Z", MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_NIGHT),
            hour("2026-08-20T21:00:00Z", MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_NIGHT),
            hour("2026-08-20T22:00:00Z", MetOfficeWeatherCode.PARTLY_CLOUDY_NIGHT)
        )

        val result = RepresentativeWeatherUtils.applyToDailyForecast(
            daily, hourly, location, utcMillis("2026-08-20T20:30:00Z")
        )

        assertEquals(MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_NIGHT, result.single().dayWeatherCode)
    }

    @Test
    fun `day and night variants count as the same weather family`() {
        val items = listOf(
            hour("2026-08-20T05:00:00Z", MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_NIGHT),
            hour("2026-08-20T06:00:00Z", MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_DAY),
            hour("2026-08-20T07:00:00Z", MetOfficeWeatherCode.CLOUDY)
        )

        assertEquals(
            MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_DAY,
            RepresentativeWeatherUtils.selectRepresentative(items)
        )
    }

    private fun day(date: String, sunset: String = "20:30") = DailyForecastItem(
        date = date,
        dayOfWeek = "Day",
        dateFormatted = date,
        maxTempCelsius = 20.0,
        minTempCelsius = 10.0,
        dayWeatherCode = MetOfficeWeatherCode.OVERCAST,
        nightWeatherCode = MetOfficeWeatherCode.CLEAR_NIGHT,
        precipitationChance = 0,
        uvIndex = 0,
        maxWindGustMph = 0.0,
        sunrise = "06:00",
        sunset = sunset
    )

    private fun hour(time: String, code: MetOfficeWeatherCode) = HourlyForecastItem(
        timeLabel = time,
        fullTime = time,
        date = time.take(10),
        temperatureCelsius = 15.0,
        feelsLikeCelsius = 15.0,
        weatherCode = code,
        precipitationChance = 0,
        windSpeedMph = 0.0,
        windDirectionDegrees = 0,
        humidityPercent = 50,
        uvIndex = 0
    )

    private fun utcMillis(value: String): Long = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)!!.time
}
