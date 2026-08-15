package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DailyForecastItem
import com.example.data.model.HourlyForecastItem
import com.example.data.model.LocationItem
import com.example.data.model.TemperatureUnit
import com.example.data.model.WindSpeedUnit
import com.example.data.util.TimezoneUtils
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardWhite
import com.example.ui.theme.BentoHero
import com.example.ui.theme.BentoHeroText
import com.example.ui.theme.BentoPurplePrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTile
import com.example.ui.theme.RainCyan
import com.example.ui.theme.SolarGold
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HourlyTimelineEntry(
    val item: HourlyForecastItem,
    val dayIndex: Int,
    val hourOfDay: Int,
    val isMidnight: Boolean,
    val dayLabel: String,
    val dateStr: String
)

@Composable
fun HourlyForecastRow(
    dailyList: List<DailyForecastItem>,
    hourlyList: List<HourlyForecastItem>,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit,
    tempUnit: TemperatureUnit,
    windUnit: WindSpeedUnit,
    location: LocationItem? = null,
    onOpenDetailSheet: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentDayIndex = selectedDayIndex.coerceIn(0, (dailyList.size - 1).coerceAtLeast(0))
    val selectedDay = dailyList.getOrNull(currentDayIndex)

    // Build the complete continuous multi-day timeline
    val timelineEntries = remember(dailyList, hourlyList, location) {
        val entries = mutableListOf<HourlyTimelineEntry>()

        if (dailyList.isNotEmpty()) {
            dailyList.forEachIndexed { dIndex, dayItem ->
                val targetDate = dayItem.date.take(10)
                val matched = hourlyList.filter {
                    localDateFromTime(it.fullTime, location) == targetDate
                }

                val dayHours: List<HourlyForecastItem> = if (matched.size >= 24) {
                    matched
                } else if (matched.isNotEmpty()) {
                    // Fill any missing hours to make a continuous 24h day
                    val hourMap = matched.associateBy { item ->
                        extractHourFromTime(item.fullTime, location)
                    }
                    (0..23).map { h ->
                        hourMap[h] ?: createSyntheticHourItem(dayItem, targetDate, h, location)
                    }
                } else {
                    (0..23).map { h ->
                        createSyntheticHourItem(dayItem, targetDate, h, location)
                    }
                }

                dayHours.forEach { hItem ->
                    val hVal = extractHourFromTime(hItem.fullTime, location)
                    val itemLocalDate = localDateFromTime(hItem.fullTime, location)
                    entries.add(
                        HourlyTimelineEntry(
                            item = hItem,
                            dayIndex = dIndex,
                            hourOfDay = hVal,
                            isMidnight = (hVal == 0),
                            // A midnight badge identifies the calendar day of
                            // this hour, not the bucket it was assembled under.
                            dayLabel = weekdayAbbreviation(itemLocalDate),
                            dateStr = itemLocalDate
                        )
                    )
                }
            }
        } else {
            hourlyList.forEachIndexed { idx, hItem ->
                val hVal = extractHourFromTime(hItem.fullTime, location)
                entries.add(
                    HourlyTimelineEntry(
                        item = hItem,
                        dayIndex = 0,
                        hourOfDay = hVal,
                        isMidnight = (hVal == 0),
                        dayLabel = weekdayAbbreviation(localDateFromTime(hItem.fullTime, location)),
                        dateStr = localDateFromTime(hItem.fullTime, location)
                    )
                )
            }
        }
        entries
    }

    val hourlyListState = rememberLazyListState()
    val dayChipsListState = rememberLazyListState()
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // 1. When a day is selected (tapped in the carousel or changed programmatically),
    // scroll the horizontal hourly timeline:
    // - For "Today": start at "Now" (with backward scroll to earlier hours permitted)
    // - For other days: start at 6:00 AM (with backward scroll to 12am-5am permitted)
    LaunchedEffect(selectedDayIndex, timelineEntries) {
        if (timelineEntries.isNotEmpty()) {
            val isToday = selectedDayIndex == 0 || selectedDay?.dayOfWeek.equals("Today", ignoreCase = true)
            val targetIndex = if (isToday) {
                val nowEntryIndex = timelineEntries.indexOfFirst { it.dayIndex == selectedDayIndex && it.item.isNow }
                    .takeIf { it >= 0 }
                    ?: timelineEntries.indexOfFirst { it.item.isNow }
                    .takeIf { it >= 0 }
                nowEntryIndex ?: (timelineEntries.indexOfFirst { it.dayIndex == selectedDayIndex && it.hourOfDay == 6 }
                    .takeIf { it >= 0 }
                    ?: timelineEntries.indexOfFirst { it.dayIndex == selectedDayIndex })
            } else {
                timelineEntries.indexOfFirst { it.dayIndex == selectedDayIndex && it.hourOfDay == 6 }
                    .takeIf { it >= 0 }
                    ?: timelineEntries.indexOfFirst { it.dayIndex == selectedDayIndex }
            }

            if (targetIndex >= 0) {
                val currentVisibleEntry = timelineEntries.getOrNull(hourlyListState.firstVisibleItemIndex)
                if (currentVisibleEntry?.dayIndex != selectedDayIndex || !hourlyListState.isScrollInProgress) {
                    isProgrammaticScroll = true
                    try {
                        hourlyListState.animateScrollToItem(targetIndex)
                    } finally {
                        isProgrammaticScroll = false
                    }
                }
            }

            // Keep the selected day chip visible in the top carousel
            if (selectedDayIndex in 0 until dailyList.size) {
                dayChipsListState.animateScrollToItem((selectedDayIndex - 1).coerceAtLeast(0))
            }
        }
    }

    // 2. When the user manually scrolls horizontally across the midnight boundary,
    // automatically detect the new day and update selectedDayIndex!
    LaunchedEffect(timelineEntries, hourlyListState) {
        snapshotFlow {
            val firstIndex = hourlyListState.firstVisibleItemIndex
            val entry = timelineEntries.getOrNull(firstIndex)
            val dayIdx = entry?.dayIndex
            dayIdx to hourlyListState.isScrollInProgress
        }.distinctUntilChanged().collect { (visibleDayIndex, isScrolling) ->
            if (visibleDayIndex != null && isScrolling && !isProgrammaticScroll) {
                if (visibleDayIndex != selectedDayIndex) {
                    onSelectDay(visibleDayIndex)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(BentoTile)
            .border(1.dp, BentoBorder.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(vertical = 14.dp)
            .testTag("hourly_forecast_section")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Section Header with active Day title & hour count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = BentoPurplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HOURLY FORECAST",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary,
                            letterSpacing = 1.1.sp,
                            fontSize = 11.5.sp
                        )
                    )
                    if (selectedDay != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${selectedDay.dayOfWeek}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BentoPurplePrimary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                if (onOpenDetailSheet != null) {
                    Surface(
                        onClick = onOpenDetailSheet,
                        shape = RoundedCornerShape(10.dp),
                        color = BentoPurplePrimary.copy(alpha = 0.1f),
                        modifier = Modifier.testTag("open_hourly_detail_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "View All",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BentoPurplePrimary,
                                    fontSize = 11.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = BentoPurplePrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Day Selector Pill Carousel
            if (dailyList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    state = dayChipsListState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(dailyList.size) { index ->
                        val dayItem = dailyList[index]
                        val isSelected = index == currentDayIndex
                        Surface(
                            onClick = { onSelectDay(index) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) BentoPurplePrimary else BentoCardWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) BentoPurplePrimary else BentoBorder.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.testTag("hourly_day_chip_$index")
                        ) {
                            val label = when (dayItem.dayOfWeek) {
                                "Today" -> "Today"
                                "Tomorrow" -> "Tomorrow"
                                "Yesterday" -> "Yesterday"
                                else -> dayItem.dayOfWeek.take(3)
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else BentoTextPrimary,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Continuous Horizontal Timeline across days
            LazyRow(
                state = hourlyListState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = timelineEntries,
                    key = { "${it.dayIndex}_${it.hourOfDay}_${it.item.fullTime}" }
                ) { entry ->
                    HourlyItemCard(
                        entry = entry,
                        tempUnit = tempUnit,
                        windUnit = windUnit
                    )
                }
            }
        }
    }
}

@Composable
fun HourlyItemCard(
    item: HourlyForecastItem,
    tempUnit: TemperatureUnit,
    windUnit: WindSpeedUnit,
    modifier: Modifier = Modifier
) {
    val hVal = extractHourFromTime(item.fullTime)
    val entry = HourlyTimelineEntry(
        item = item,
        dayIndex = 0,
        hourOfDay = hVal,
        isMidnight = (hVal == 0),
        dayLabel = "",
        dateStr = item.date
    )
    HourlyItemCard(
        entry = entry,
        tempUnit = tempUnit,
        windUnit = windUnit,
        modifier = modifier
    )
}

@Composable
fun HourlyItemCard(
    entry: HourlyTimelineEntry,
    tempUnit: TemperatureUnit,
    windUnit: WindSpeedUnit,
    modifier: Modifier = Modifier
) {
    val item = entry.item
    val isNow = item.isNow
    val isMidnight = entry.isMidnight

    Box(
        modifier = modifier
            .width(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isNow) BentoHero else BentoCardWhite)
            .border(
                1.dp,
                if (isNow) BentoPurplePrimary.copy(alpha = 0.7f)
                else if (isMidnight) BentoPurplePrimary.copy(alpha = 0.4f)
                else BentoBorder.copy(alpha = 0.55f),
                RoundedCornerShape(18.dp)
            )
            .padding(vertical = 10.dp, horizontal = 5.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Day Badge for Midnight / Day Transitions
            if (isMidnight && !isNow) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = BentoPurplePrimary.copy(alpha = 0.14f),
                    modifier = Modifier.padding(bottom = 3.dp)
                ) {
                    Text(
                        text = entry.dayLabel.take(3).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = BentoPurplePrimary,
                            fontSize = 8.5.sp,
                            letterSpacing = 0.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // Time Label (e.g., "Now", "6 AM", "12 AM", "2 PM")
            Text(
                text = item.timeLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isNow) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (isNow) BentoHeroText else BentoTextSecondary,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Weather Icon
            WeatherIconView(
                iconType = item.weatherCode.iconType,
                size = 30.dp
            )

            Spacer(modifier = Modifier.height(5.dp))

            // Temperature
            Text(
                text = "${tempUnit.convert(item.temperatureCelsius).toInt()}°",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isNow) BentoHeroText else BentoTextPrimary,
                    fontSize = 16.sp
                )
            )

            // Feels Like Temperature
            Text(
                text = "FL ${tempUnit.convert(item.feelsLikeCelsius).toInt()}°",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isNow) BentoHeroText.copy(alpha = 0.75f) else BentoTextSecondary.copy(alpha = 0.75f)
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Always identify this value as precipitation. A missing API value is
            // normalized to 0% when the forecast item is mapped.
            val isHighPrecip = item.precipitationChance > 30
            val precipColor = if (isHighPrecip) RainCyan else BentoTextSecondary.copy(alpha = 0.65f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = "Precipitation chance: ${item.precipitationChance}%",
                    tint = precipColor,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(1.5.dp))
                Text(
                    text = "${item.precipitationChance}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = if (isHighPrecip) FontWeight.Bold else FontWeight.SemiBold,
                        color = precipColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Wind Direction & Speed
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Wind direction: ${item.windDirectionDegrees}° (${item.windDirectionCompass})",
                    tint = if (isNow) BentoHeroText.copy(alpha = 0.85f) else BentoPurplePrimary,
                    modifier = Modifier
                        .size(9.5.dp)
                        .rotate((item.windDirectionDegrees + 180f) % 360f)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = windUnit.format(item.windSpeedMph),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isNow) BentoHeroText.copy(alpha = 0.85f) else BentoTextSecondary
                    ),
                    maxLines = 1
                )
            }

            // Reserve a fixed slot for UV so cards stay the same height. The
            // midnight boundary already has a day-name badge, so omit the UV
            // slot there to prevent the two badges from stacking vertically.
            if (!isMidnight) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier.height(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.uvIndex >= 1) {
                        val uvColor = when {
                            item.uvIndex <= 2 -> Color(0xFF43A047) // Low
                            item.uvIndex <= 5 -> SolarGold // Moderate
                            else -> Color(0xFFE53935) // High / very high
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = uvColor.copy(alpha = if (isNow) 0.25f else 0.15f)
                        ) {
                            Text(
                                text = "UV ${item.uvIndex}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = uvColor
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun extractHourFromTime(fullTime: String?, location: LocationItem? = null): Int {
    if (fullTime == null) return 0
    if (location != null) {
        return TimezoneUtils.getLocalHour(fullTime, location)
    }
    return try {
        if (fullTime.contains("T")) {
            val timePart = fullTime.substringAfter("T").take(5)
            timePart.substringBefore(":").toInt()
        } else if (fullTime.contains(" ")) {
            val timePart = fullTime.substringAfter(" ").take(5)
            timePart.substringBefore(":").toInt()
        } else {
            0
        }
    } catch (_: Exception) {
        0
    }
}

private fun localDateFromTime(fullTime: String?, location: LocationItem?): String {
    if (fullTime.isNullOrBlank()) return ""

    // Open-Meteo returns local timestamps without a zone. In that case the date
    // already represents the local calendar day and must not be converted again.
    if (location == null || !fullTime.endsWith("Z")) return fullTime.take(10)

    val millis = TimezoneUtils.parseIsoToMillis(fullTime) ?: return fullTime.take(10)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimezoneUtils.getTimeZoneForLocation(location)
    }.format(Date(millis))
}

private fun weekdayAbbreviation(date: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsedDate = parser.parse(date) ?: return date.take(3)
        SimpleDateFormat("EEE", Locale.US).format(parsedDate)
    } catch (_: Exception) {
        date.take(3)
    }
}

private fun createSyntheticHourItem(
    day: DailyForecastItem,
    targetDate: String,
    h: Int,
    location: LocationItem? = null
): HourlyForecastItem {
    val isNight = h < 6 || h >= 21
    val tempFraction = when (h) {
        in 0..5 -> ((5 - h) / 5.0) * 0.20
        in 6..14 -> Math.sin(((h - 5.0) / 9.0) * Math.PI / 2.0).coerceIn(0.0, 1.0)
        else -> (Math.cos(((h - 14.0) / 10.0) * Math.PI / 2.0) * 0.85 + 0.15).coerceIn(0.0, 1.0)
    }
    val calculatedTemp = day.minTempCelsius + (tempFraction * (day.maxTempCelsius - day.minTempCelsius))
    val amPm = if (h >= 12) "PM" else "AM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    val timeLabel = "$h12 $amPm"

    val fullTime = if (location != null) {
        val tz = TimezoneUtils.getTimeZoneForLocation(location)
        val cal = java.util.Calendar.getInstance(tz).apply {
            val parts = targetDate.split("-")
            val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
            val m = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
            val d = parts.getOrNull(2)?.toIntOrNull() ?: 1
            set(y, m, d, h, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(cal.time)
    } else {
        val hourStr = String.format(java.util.Locale.US, "%02d:00", h)
        "${targetDate}T$hourStr:00Z"
    }

    return HourlyForecastItem(
        timeLabel = timeLabel,
        fullTime = fullTime,
        date = targetDate,
        temperatureCelsius = Math.round(calculatedTemp * 10.0) / 10.0,
        feelsLikeCelsius = Math.round(calculatedTemp * 10.0) / 10.0,
        weatherCode = if (isNight) day.nightWeatherCode else day.dayWeatherCode,
        precipitationChance = day.precipitationChance,
        windSpeedMph = Math.round(day.maxWindGustMph * 0.65 * 10.0) / 10.0,
        windDirectionDegrees = 225,
        humidityPercent = (85 - (tempFraction * 35)).toInt().coerceIn(35, 95),
        uvIndex = if (isNight || h < 8 || h > 18) 0 else day.uvIndex,
        isNow = false
    )
}
