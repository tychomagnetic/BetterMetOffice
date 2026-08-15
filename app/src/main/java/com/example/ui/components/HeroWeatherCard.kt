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
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CurrentWeather
import com.example.data.model.TemperatureUnit
import com.example.data.model.WindSpeedUnit
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoHero
import com.example.ui.theme.BentoHeroText
import com.example.ui.theme.BentoPillAccent
import com.example.ui.theme.BentoPurplePrimary
import com.example.ui.theme.RainCyan

@Composable
fun HeroWeatherCard(
    current: CurrentWeather,
    tempUnit: TemperatureUnit,
    windUnit: WindSpeedUnit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(BentoHero)
            .border(1.dp, BentoBorder.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("hero_weather_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Main Top Row: Weather Icon + Condition description on left, Big Temp + High/Low on right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left: Condition Icon & text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    WeatherIconView(
                        iconType = current.weatherCode.iconType,
                        size = 46.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = current.weatherCode.description,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BentoHeroText,
                                fontSize = 16.sp
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = "Feels like ${tempUnit.format(current.feelsLikeCelsius)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = BentoHeroText.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Compact Main Temperature & High/Low
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "${tempUnit.convert(current.temperatureCelsius).toInt()}°",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BentoHeroText,
                            letterSpacing = (-1).sp
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "H:${tempUnit.convert(current.maxTempCelsius).toInt()}°",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = BentoHeroText.copy(alpha = 0.9f),
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "L:${tempUnit.convert(current.minTempCelsius).toInt()}°",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = BentoHeroText.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bento Badges: Precipitation & Wind (compact)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Precipitation Badge
                val isHighPrecip = current.precipitationChance > 30
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BentoPillAccent)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = "Rain Probability",
                        tint = if (isHighPrecip) RainCyan else BentoPurplePrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${current.precipitationChance}% Rain",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isHighPrecip) FontWeight.Bold else FontWeight.SemiBold,
                            color = BentoHeroText,
                            fontSize = 12.sp
                        )
                    )
                }

                // Wind Speed Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BentoPillAccent)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = "Wind Speed",
                        tint = BentoPurplePrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${windUnit.format(current.windSpeedMph)} ${current.windDirectionCompass}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = BentoHeroText,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

