package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CurrentWeather
import com.example.data.model.PressureUnit
import com.example.data.model.WindSpeedUnit
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardWhite
import com.example.ui.theme.BentoPurplePrimary
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

@Composable
fun WeatherMetricsGrid(
    current: CurrentWeather,
    windUnit: WindSpeedUnit,
    pressureUnit: PressureUnit,
    sunrise: String? = null,
    sunset: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("weather_metrics_grid")
    ) {
        // Row 1: Wind & UV Index
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Wind Card
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "WIND & GUSTS",
                icon = Icons.Default.Air,
                mainValue = windUnit.format(current.windSpeedMph),
                subValue = "Gusts: ${windUnit.format(current.windGustMph)}",
                extraContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Wind Direction",
                            tint = BentoPurplePrimary,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(current.windDirectionDegrees.toFloat())
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${current.windDirectionCompass} (${current.windDirectionDegrees}°)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = BentoTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            )

            // UV Index Card
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "UV INDEX",
                icon = Icons.Default.WbSunny,
                mainValue = "${current.uvIndex}",
                subValue = current.uvCategory,
                extraContent = {
                    val advice = when (current.uvIndex) {
                        0, 1, 2 -> "No protection required"
                        3, 4, 5 -> "Wear sun protection"
                        6, 7 -> "Protection essential"
                        else -> "Seek shade in midday"
                    }
                    Text(
                        text = advice,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = BentoTextSecondary,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 2: Humidity & Pressure
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Humidity Card
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "HUMIDITY",
                icon = Icons.Default.Opacity,
                mainValue = "${current.humidityPercent}%",
                subValue = if (current.humidityPercent > 70) "Humid conditions" else "Comfortable level",
                extraContent = null
            )

            // Pressure Card
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "PRESSURE",
                icon = Icons.Default.Compress,
                mainValue = pressureUnit.format(current.pressureHpa),
                subValue = if (current.pressureHpa > 1013) "High (Settled)" else "Low (Unsettled)",
                extraContent = null
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 3: Visibility & Rain / Sun
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Visibility Card
            val visKm = current.visibilityMeters / 1000.0
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "VISIBILITY",
                icon = Icons.Default.Visibility,
                mainValue = "${String.format("%.1f", visKm)} km",
                subValue = current.visibilityCategory,
                extraContent = null
            )

            // Sun / Precipitation Card
            MetricCard(
                modifier = Modifier.weight(1f),
                title = if (!sunrise.isNullOrBlank()) "SUN TIMES" else "PRECIPITATION",
                icon = if (!sunrise.isNullOrBlank()) Icons.Default.WbSunny else Icons.Default.WaterDrop,
                mainValue = if (!sunrise.isNullOrBlank()) "Rise: $sunrise" else "${current.precipitationChance}%",
                subValue = if (!sunset.isNullOrBlank()) "Set: $sunset" else "Chance of rain",
                extraContent = null
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    icon: ImageVector,
    mainValue: String,
    subValue: String,
    modifier: Modifier = Modifier,
    extraContent: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(BentoCardWhite)
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column {
            // Header: Title & Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BentoPurplePrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = BentoTextSecondary,
                        letterSpacing = 0.8.sp,
                        fontSize = 10.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Value
            Text(
                text = mainValue,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary,
                    fontSize = 20.sp
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Sub Value
            Text(
                text = subValue,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = BentoTextSecondary,
                    fontSize = 11.5.sp
                )
            )

            extraContent?.invoke()
        }
    }
}

