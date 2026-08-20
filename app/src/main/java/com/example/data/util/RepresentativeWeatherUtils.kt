package com.example.data.util

import com.example.data.model.DailyForecastItem
import com.example.data.model.HourlyForecastItem
import com.example.data.model.LocationItem
import com.example.data.model.MetOfficeWeatherCode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Selects a useful headline condition instead of treating the first hour as the whole day. */
object RepresentativeWeatherUtils {

    fun applyToDailyForecast(
        daily: List<DailyForecastItem>,
        hourly: List<HourlyForecastItem>,
        location: LocationItem,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DailyForecastItem> {
        if (daily.isEmpty() || hourly.isEmpty()) return daily

        val timezone = TimezoneUtils.getTimeZoneForLocation(location)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = timezone
        }.format(Date(nowMillis))
        val nowCalendar = Calendar.getInstance(timezone).apply { timeInMillis = nowMillis }
        val nowMinute = nowCalendar.get(Calendar.HOUR_OF_DAY) * 60 + nowCalendar.get(Calendar.MINUTE)
        val hourlyByDate = hourly.groupBy { it.date }

        return daily.map { day ->
            val samples = hourlyByDate[day.date].orEmpty()
            if (samples.isEmpty()) return@map day

            val remaining = if (day.date == today) {
                samples.filter { localMinute(it.fullTime, location) / 60 >= nowMinute / 60 }
            } else {
                samples
            }
            if (remaining.isEmpty()) return@map day

            val calculatedSunTimes = TimezoneUtils.calculateSunTimes(day.date, location)
            val sunriseMinute = parseClockMinute(day.sunrise ?: calculatedSunTimes.first) ?: 6 * 60
            val sunsetMinute = parseClockMinute(day.sunset ?: calculatedSunTimes.second) ?: 21 * 60
            val daylightRemaining = remaining.filter {
                localMinute(it.fullTime, location) in sunriseMinute until sunsetMinute
            }

            // Once today's daylight is over, the headline should describe the rest of
            // tonight. Future days continue to use the more useful daylight window.
            val candidates = when {
                day.date == today && nowMinute >= sunsetMinute -> remaining
                daylightRemaining.isNotEmpty() -> daylightRemaining
                else -> remaining
            }
            val representative = selectRepresentative(candidates) ?: return@map day
            day.copy(dayWeatherCode = representative)
        }
    }

    internal fun selectRepresentative(items: List<HourlyForecastItem>): MetOfficeWeatherCode? {
        if (items.isEmpty()) return null

        val midpoint = (items.lastIndex) / 2.0
        val winningFamily = items.withIndex()
            .groupBy { weatherFamily(it.value.weatherCode) }
            .values
            .maxWithOrNull(
                compareBy<List<IndexedValue<HourlyForecastItem>>> { it.size }
                    // When equally frequent, prefer the condition centred in the useful
                    // part of the period, rather than a one-off severe event at an edge.
                    .thenBy { group -> -group.map { kotlin.math.abs(it.index - midpoint) }.average() }
                    .thenBy { group -> group.maxOf { it.value.weatherCode.code } }
            )
            .orEmpty()

        return winningFamily
            .groupBy { it.value.weatherCode }
            .values
            .maxWithOrNull(
                compareBy<List<IndexedValue<HourlyForecastItem>>> { it.size }
                    .thenBy { group -> -group.map { kotlin.math.abs(it.index - midpoint) }.average() }
            )
            ?.firstOrNull()
            ?.value
            ?.weatherCode
    }

    private fun weatherFamily(code: MetOfficeWeatherCode): String = when (code.code) {
        0, 1 -> "clear"
        2, 3 -> "partly_cloudy"
        9, 10 -> "light_rain_shower"
        13, 14 -> "heavy_rain_shower"
        16, 17 -> "sleet_shower"
        19, 20 -> "hail_shower"
        22, 23 -> "light_snow_shower"
        25, 26 -> "heavy_snow_shower"
        28, 29 -> "thunder_shower"
        else -> "code_${code.code}"
    }

    private fun localMinute(time: String, location: LocationItem): Int =
        TimezoneUtils.getLocalHour(time, location) * 60

    private fun parseClockMinute(value: String): Int? {
        val parts = value.takeLast(5).split(':')
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return hour * 60 + minute
    }
}
