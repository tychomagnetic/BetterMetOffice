package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.model.PressureUnit
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayHourlyDetailSheet(
    dailyList: List<DailyForecastItem>,
    allHourlyList: List<HourlyForecastItem>,
    location: LocationItem,
    selectedDayIndex: Int,
    tempUnit: TemperatureUnit,
    windUnit: WindSpeedUnit,
    pressureUnit: PressureUnit = PressureUnit.HPA,
    onSelectDay: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentDayIndex = selectedDayIndex.coerceIn(0, (dailyList.size - 1).coerceAtLeast(0))
    val day = dailyList.getOrNull(currentDayIndex) ?: return

    var expandedHourKey by remember(currentDayIndex) { mutableStateOf<String?>(null) }

    val targetDate = day.date.take(10)
    val dayHourly = allHourlyList.filter {
        TimezoneUtils.getForecastLocalDate(it.fullTime, location) == targetDate
    }.ifEmpty {
        (0..23).map { h ->
            val hourStr = String.format(java.util.Locale.US, "%02d:00", h)
            val fullTime = "${targetDate}T$hourStr:00Z"
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
            HourlyForecastItem(
                timeLabel = "$h12 $amPm",
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
                pressureHpa = 1013.25,
                isNow = false
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BentoTile,
        modifier = modifier.testTag("day_hourly_detail_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Header: Title & Close Button
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoPurplePrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = BentoPurplePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${day.dayOfWeek} Hourly Forecast",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary
                                )
                            )
                            Text(
                                text = "${day.dateFormatted} (${dayHourly.size} hours)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = BentoTextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_day_detail_sheet")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = BentoTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Day Selector Pill Strip
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(dailyList.size) { index ->
                        val item = dailyList[index]
                        val isSelected = index == currentDayIndex
                        Surface(
                            onClick = { onSelectDay(index) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) BentoPurplePrimary else BentoCardWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) BentoPurplePrimary else BentoBorder.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.testTag("detail_day_pill_$index")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                val label = when (item.dayOfWeek) {
                                    "Today" -> "Today"
                                    "Tomorrow" -> "Tomorrow"
                                    "Yesterday" -> "Yesterday"
                                    else -> item.dayOfWeek.take(3)
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else BentoTextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${tempUnit.convert(item.maxTempCelsius).toInt()}°",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else BentoTextSecondary
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Day Overview Summary Card with Enhanced Top Details
            item {
                val dailyPrecipChance = (day.precipitationChance).coerceAtLeast(
                    dayHourly.map { it.precipitationChance }.maxOrNull() ?: 0
                )

                val highestWindSpeed = (day.maxWindGustMph).coerceAtLeast(
                    dayHourly.map { it.windSpeedMph }.maxOrNull() ?: 0.0
                )

                val (calculatedSunrise, calculatedSunset) = TimezoneUtils.calculateSunTimes(day.date, location)
                val sunriseFormatted = TimezoneUtils.formatDisplaySunTime(day.sunrise ?: calculatedSunrise)
                val sunsetFormatted = TimezoneUtils.formatDisplaySunTime(day.sunset ?: calculatedSunset)

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = BentoHero,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoPurplePrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Top row: Condition, High/Low, and Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = day.dayWeatherCode.description,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = BentoHeroText,
                                        fontSize = 17.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = BentoPurplePrimary.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = BentoHeroText,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "High: ${tempUnit.format(day.maxTempCelsius)}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = BentoHeroText,
                                                    fontSize = 12.sp
                                                )
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = BentoPurplePrimary.copy(alpha = 0.1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = BentoHeroText.copy(alpha = 0.75f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Low: ${tempUnit.format(day.minTempCelsius)}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = BentoHeroText.copy(alpha = 0.75f),
                                                    fontSize = 12.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            WeatherIconView(
                                iconType = day.dayWeatherCode.iconType,
                                size = 48.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = BentoPurplePrimary.copy(alpha = 0.2f), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Grid of 4 Key Daily Highlights
                        // Row 1: Rain Chance & Peak Wind
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Precipitation Chance
                            DayHighlightTile(
                                icon = Icons.Default.WaterDrop,
                                iconTint = RainCyan,
                                iconBg = RainCyan.copy(alpha = 0.15f),
                                label = "RAIN CHANCE",
                                value = "$dailyPrecipChance%",
                                description = when {
                                    dailyPrecipChance == 0 -> "Dry conditions"
                                    dailyPrecipChance < 30 -> "Low risk"
                                    dailyPrecipChance < 60 -> "Scattered showers"
                                    else -> "Rain likely"
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Peak Wind / Highest Wind
                            DayHighlightTile(
                                icon = Icons.Default.Air,
                                iconTint = BentoPurplePrimary,
                                iconBg = BentoPurplePrimary.copy(alpha = 0.15f),
                                label = "HIGHEST WIND",
                                value = windUnit.format(highestWindSpeed),
                                description = when {
                                    highestWindSpeed < 10 -> "Light breeze"
                                    highestWindSpeed < 20 -> "Moderate breeze"
                                    highestWindSpeed < 30 -> "Fresh / Gusty"
                                    else -> "Strong gusts"
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Row 2: Sunrise & Sunset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Sunrise
                            DayHighlightTile(
                                icon = Icons.Default.WbSunny,
                                iconTint = SolarGold,
                                iconBg = SolarGold.copy(alpha = 0.18f),
                                label = "SUNRISE",
                                value = sunriseFormatted,
                                description = "Dawn",
                                modifier = Modifier.weight(1f)
                            )

                            // Sunset
                            DayHighlightTile(
                                icon = Icons.Default.NightsStay,
                                iconTint = androidx.compose.ui.graphics.Color(0xFF6366F1),
                                iconBg = androidx.compose.ui.graphics.Color(0xFF6366F1).copy(alpha = 0.15f),
                                label = "SUNSET",
                                value = sunsetFormatted,
                                description = "Dusk",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Horizontal Hourly Scroll Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = BentoPurplePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HOURLY TIMELINE (${dayHourly.size} HOURS)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Horizontal Hourly Cards
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = dayHourly,
                        key = { "sheet_${it.date}_${it.fullTime}_${it.timeLabel}" }
                    ) { item ->
                        HourlyItemCard(
                            item = item,
                            tempUnit = tempUnit,
                            windUnit = windUnit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Detailed Hour-by-Hour List Breakdown
            item {
                Text(
                    text = "Detailed Breakdown",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(BentoCardWhite)
                        .border(1.dp, BentoBorder.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        dayHourly.forEachIndexed { index, item ->
                            val rowKey = "${item.date}_${item.fullTime}_${item.timeLabel}_$index"
                            val isExpanded = (expandedHourKey == rowKey)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedHourKey = if (isExpanded) null else rowKey
                                    }
                                    .testTag("detail_hourly_row_$index")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    // Hour Time & Expand Indicator
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.width(62.dp)
                                    ) {
                                        Text(
                                            text = item.timeLabel,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isExpanded) BentoPurplePrimary else BentoTextPrimary
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                                            tint = if (isExpanded) BentoPurplePrimary else BentoTextSecondary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Weather Icon & Condition
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        WeatherIconView(
                                            iconType = item.weatherCode.iconType,
                                            size = 24.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.weatherCode.description,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = BentoTextPrimary,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            maxLines = 1
                                        )
                                    }

                                    // Rain %
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.width(46.dp)
                                    ) {
                                        if (item.precipitationChance > 0) {
                                            val isHighPrecip = item.precipitationChance > 30
                                            val precipColor = if (isHighPrecip) RainCyan else BentoTextSecondary
                                            Icon(
                                                imageVector = Icons.Default.WaterDrop,
                                                contentDescription = null,
                                                tint = precipColor,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "${item.precipitationChance}%",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = precipColor,
                                                    fontWeight = if (isHighPrecip) FontWeight.Bold else FontWeight.Medium
                                                )
                                            )
                                        }
                                    }

                                    // Wind direction arrow & speed
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.width(62.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Navigation,
                                            contentDescription = "Wind: ${item.windDirectionDegrees}° (${item.windDirectionCompass})",
                                            tint = BentoPurplePrimary,
                                            modifier = Modifier
                                                .size(11.dp)
                                                .rotate((item.windDirectionDegrees + 180f) % 360f)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = windUnit.format(item.windSpeedMph),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = BentoTextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }

                                    // Temp
                                    Text(
                                        text = "${tempUnit.convert(item.temperatureCelsius).toInt()}°",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = BentoTextPrimary
                                        ),
                                        modifier = Modifier.width(30.dp)
                                    )
                                }

                                // Expandable Additional Details Panel (Humidity, Pressure, UV Index, Feels Like)
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(BentoTile.copy(alpha = 0.65f))
                                                .border(1.dp, BentoBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // Humidity
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(RainCyan.copy(alpha = 0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.WaterDrop,
                                                            contentDescription = null,
                                                            tint = RainCyan,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text(
                                                            text = "HUMIDITY",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 9.sp,
                                                                color = BentoTextSecondary,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        )
                                                        Text(
                                                            text = "${item.humidityPercent}%",
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = BentoTextPrimary
                                                            )
                                                        )
                                                    }
                                                }

                                                // Pressure
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1.1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(BentoPurplePrimary.copy(alpha = 0.12f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Compress,
                                                            contentDescription = null,
                                                            tint = BentoPurplePrimary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text(
                                                            text = "PRESSURE",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 9.sp,
                                                                color = BentoTextSecondary,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        )
                                                        Text(
                                                            text = pressureUnit.format(item.pressureHpa),
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = BentoTextPrimary
                                                            )
                                                        )
                                                    }
                                                }

                                                // UV Index
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(0.9f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(SolarGold.copy(alpha = 0.18f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.WbSunny,
                                                            contentDescription = null,
                                                            tint = SolarGold,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text(
                                                            text = "UV INDEX",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 9.sp,
                                                                color = BentoTextSecondary,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        )
                                                        Text(
                                                            text = "${item.uvIndex} (${when (item.uvIndex) {
                                                                0, 1, 2 -> "Low"
                                                                3, 4, 5 -> "Mod"
                                                                6, 7 -> "High"
                                                                8, 9, 10 -> "V.High"
                                                                else -> "Ext"
                                                            }})",
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = BentoTextPrimary
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (index < dayHourly.lastIndex) {
                                HorizontalDivider(
                                    color = BentoBorder.copy(alpha = 0.35f),
                                    thickness = 0.8.dp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHighlightTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    iconBg: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BentoCardWhite.copy(alpha = 0.65f),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoPurplePrimary.copy(alpha = 0.15f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoHeroText,
                        fontSize = 13.5.sp
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.5.sp,
                        color = BentoTextSecondary.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
