package com.example.data.util

import com.example.data.model.WeatherReport

object WeatherClockUtils {
    data class Update(
        val report: WeatherReport,
        val selectedDayIndex: Int
    )

    fun advance(report: WeatherReport, selectedDayIndex: Int, nowMillis: Long): Update {
        if (report.hourly.isEmpty()) return Update(report, selectedDayIndex)

        val previousNowDate = report.hourly.firstOrNull { it.isNow }?.date
        val nowIndex = TimezoneUtils.findCurrentHourItemIndex(
            report.hourly.map { it.fullTime },
            nowMillis,
            report.location
        )
        val nowItem = report.hourly.getOrNull(nowIndex) ?: return Update(report, selectedDayIndex)
        val hourly = report.hourly.mapIndexed { index, item ->
            val isNow = index == nowIndex
            item.copy(
                isNow = isNow,
                timeLabel = TimezoneUtils.formatHourLabel(item.fullTime, report.location, isNow)
            )
        }
        val relabelledDaily = report.daily.map { day ->
            val labels = TimezoneUtils.formatDayOfWeek(day.date, report.location, nowMillis)
            day.copy(dayOfWeek = labels.first, dateFormatted = labels.second)
        }
        val daily = RepresentativeWeatherUtils.applyToDailyForecast(
            daily = relabelledDaily,
            hourly = hourly,
            location = report.location,
            nowMillis = nowMillis
        )
        val today = daily.firstOrNull { it.date == nowItem.date }
        val current = report.current.copy(
            temperatureCelsius = nowItem.temperatureCelsius,
            feelsLikeCelsius = nowItem.feelsLikeCelsius,
            weatherCode = nowItem.weatherCode,
            maxTempCelsius = today?.maxTempCelsius ?: report.current.maxTempCelsius,
            minTempCelsius = today?.minTempCelsius ?: report.current.minTempCelsius,
            humidityPercent = nowItem.humidityPercent,
            windSpeedMph = nowItem.windSpeedMph,
            windDirectionDegrees = nowItem.windDirectionDegrees,
            precipitationChance = nowItem.precipitationChance,
            uvIndex = nowItem.uvIndex,
            pressureHpa = nowItem.pressureHpa,
            timestamp = nowItem.fullTime,
            isNight = TimezoneUtils.isNightTime(nowItem.fullTime, report.location)
        )

        val selectedDate = report.daily.getOrNull(selectedDayIndex)?.date
        val shouldFollowNow = selectedDate == previousNowDate
        val nextSelectedDay = if (shouldFollowNow) {
            daily.indexOfFirst { it.date == nowItem.date }.takeIf { it >= 0 } ?: selectedDayIndex
        } else {
            selectedDayIndex.coerceIn(0, (daily.size - 1).coerceAtLeast(0))
        }
        return Update(
            report.copy(current = current, hourly = hourly, daily = daily),
            nextSelectedDay
        )
    }
}
