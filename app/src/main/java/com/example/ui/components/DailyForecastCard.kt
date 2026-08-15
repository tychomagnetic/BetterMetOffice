package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DailyForecastItem
import com.example.data.model.TemperatureUnit
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoPurplePrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTile
import com.example.ui.theme.RainCyan
import com.example.ui.theme.SolarGold

@Composable
fun DailyForecastCard(
    dailyList: List<DailyForecastItem>,
    tempUnit: TemperatureUnit,
    selectedDayIndex: Int = 0,
    onDayClick: (Int, DailyForecastItem) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (dailyList.isEmpty()) return

    val globalMin = dailyList.minOfOrNull { it.minTempCelsius } ?: 0.0
    val globalMax = dailyList.maxOfOrNull { it.maxTempCelsius } ?: 30.0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(BentoTile)
            .border(1.dp, BentoBorder.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
            .padding(18.dp)
            .testTag("daily_forecast_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = BentoPurplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "7-DAY FORECAST",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp
                        )
                    )
                }

                Text(
                    text = "Tap day for hourly",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = BentoPurplePrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp
                    )
                )
            }

            // Daily items
            dailyList.forEachIndexed { index, item ->
                val isSelected = index == selectedDayIndex

                Surface(
                    onClick = { onDayClick(index, item) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) BentoPurplePrimary.copy(alpha = 0.09f) else Color.Transparent,
                    border = if (isSelected) {
                        androidx.compose.foundation.BorderStroke(1.dp, BentoPurplePrimary.copy(alpha = 0.35f))
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forecast_day_row_$index")
                ) {
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        DailyForecastRowItem(
                            item = item,
                            tempUnit = tempUnit,
                            globalMin = globalMin,
                            globalMax = globalMax,
                            isSelected = isSelected
                        )
                    }
                }

                if (index < dailyList.lastIndex) {
                    HorizontalDivider(
                        color = BentoBorder.copy(alpha = 0.35f),
                        thickness = 0.8.dp,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DailyForecastRowItem(
    item: DailyForecastItem,
    tempUnit: TemperatureUnit,
    globalMin: Double,
    globalMax: Double,
    isSelected: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Day name & date
        Column(modifier = Modifier.width(92.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(BentoPurplePrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = item.dayOfWeek,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) BentoPurplePrimary else BentoTextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = item.dateFormatted,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = BentoTextSecondary,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(start = if (isSelected) 12.dp else 0.dp)
            )
        }

        // Weather Icon & Rain %
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(65.dp)
        ) {
            WeatherIconView(
                iconType = item.dayWeatherCode.iconType,
                size = 28.dp
            )
            if (item.precipitationChance > 0) {
                val isHighPrecip = item.precipitationChance > 30
                val precipColor = if (isHighPrecip) RainCyan else BentoTextSecondary
                Spacer(modifier = Modifier.width(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = precipColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "${item.precipitationChance}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (isHighPrecip) FontWeight.Bold else FontWeight.Medium,
                            color = precipColor
                        )
                    )
                }
            }
        }

        // Temperature range min/max and visual bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End
        ) {
            // Min temp
            Text(
                text = "${tempUnit.convert(item.minTempCelsius).toInt()}°",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = BentoTextSecondary
                ),
                modifier = Modifier.width(28.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Range Bar accurately positioned along the weekly temperature continuum
            val range = (globalMax - globalMin).coerceAtLeast(1.0)
            val startRatio = ((item.minTempCelsius - globalMin) / range).coerceIn(0.0, 1.0).toFloat()
            val endRatio = ((item.maxTempCelsius - globalMin) / range).coerceIn(0.0, 1.0).toFloat()

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
            ) {
                val trackWidth = size.width
                val trackHeight = size.height
                val cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2)

                // Background track representing the full weekly range [globalMin..globalMax]
                drawRoundRect(
                    color = BentoBorder.copy(alpha = 0.35f),
                    size = size,
                    cornerRadius = cornerRadius
                )

                // Calculate start and end X for this day
                val startX = (startRatio * trackWidth).coerceIn(0f, trackWidth)
                val endX = (endRatio * trackWidth).coerceIn(0f, trackWidth)
                val barWidth = (endX - startX).coerceAtLeast(trackHeight) // Ensure at least a dot pill
                val clampedStartX = startX.coerceAtMost(trackWidth - barWidth).coerceAtLeast(0f)

                // Draw the temperature range bar positioned exactly from startX to endX
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(BentoPurplePrimary, SolarGold),
                        startX = 0f,
                        endX = trackWidth
                    ),
                    topLeft = Offset(clampedStartX, 0f),
                    size = Size(barWidth, trackHeight),
                    cornerRadius = cornerRadius
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Max temp
            Text(
                text = "${tempUnit.convert(item.maxTempCelsius).toInt()}°",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                ),
                modifier = Modifier.width(28.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isSelected) BentoPurplePrimary else BentoTextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
