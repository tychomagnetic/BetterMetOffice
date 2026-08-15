package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.WeatherIconType
import com.example.ui.theme.RainCyan
import com.example.ui.theme.SolarGold

@Composable
fun WeatherIconView(
    iconType: WeatherIconType,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color? = null
) {
    if (iconType == WeatherIconType.PARTLY_CLOUDY_DAY) {
        val sunColor = tint ?: SolarGold
        Box(modifier = modifier.size(size), contentAlignment = Alignment.BottomEnd) {
            Icon(
                imageVector = Icons.Filled.WbSunny,
                contentDescription = iconType.name,
                tint = sunColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(size * 0.64f)
            )
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = null,
                tint = Color(0xFF90A4AE),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.76f)
            )
        }
        return
    }

    val (icon, defaultColor) = when (iconType) {
        WeatherIconType.CLEAR_DAY -> Icons.Filled.WbSunny to SolarGold
        WeatherIconType.CLEAR_NIGHT -> Icons.Filled.NightsStay to Color(0xFFE2E8F0)
        WeatherIconType.PARTLY_CLOUDY_DAY -> Icons.Filled.WbCloudy to Color(0xFFFFD54F)
        WeatherIconType.PARTLY_CLOUDY_NIGHT -> Icons.Filled.DarkMode to Color(0xFFCBD5E1)
        WeatherIconType.CLOUDY -> Icons.Filled.Cloud to Color(0xFFB0BEC5)
        WeatherIconType.OVERCAST -> Icons.Filled.Cloud to Color(0xFF78909C)
        WeatherIconType.MIST, WeatherIconType.FOG -> Icons.Outlined.Air to Color(0xFF90A4AE)
        WeatherIconType.DRIZZLE, WeatherIconType.LIGHT_RAIN -> Icons.Filled.Grain to RainCyan
        WeatherIconType.RAIN_SHOWER_DAY -> Icons.Filled.WaterDrop to Color(0xFF4FC3F7)
        WeatherIconType.RAIN_SHOWER_NIGHT -> Icons.Filled.WaterDrop to Color(0xFF29B6F6)
        WeatherIconType.HEAVY_RAIN, WeatherIconType.HEAVY_RAIN_DAY, WeatherIconType.HEAVY_RAIN_NIGHT -> Icons.Filled.WaterDrop to Color(0xFF0288D1)
        WeatherIconType.SLEET, WeatherIconType.SLEET_DAY, WeatherIconType.SLEET_NIGHT, WeatherIconType.HAIL -> Icons.Filled.AcUnit to Color(0xFF81D4FA)
        WeatherIconType.SNOW, WeatherIconType.SNOW_DAY, WeatherIconType.SNOW_NIGHT, WeatherIconType.HEAVY_SNOW, WeatherIconType.HEAVY_SNOW_DAY, WeatherIconType.HEAVY_SNOW_NIGHT -> Icons.Filled.AcUnit to Color(0xFFE0F7FA)
        WeatherIconType.THUNDERSTORM, WeatherIconType.THUNDERSTORM_DAY, WeatherIconType.THUNDERSTORM_NIGHT -> Icons.Filled.Thunderstorm to Color(0xFFFFD54F)
    }

    Icon(
        imageVector = icon,
        contentDescription = iconType.name,
        tint = tint ?: defaultColor,
        modifier = modifier.size(size)
    )
}
