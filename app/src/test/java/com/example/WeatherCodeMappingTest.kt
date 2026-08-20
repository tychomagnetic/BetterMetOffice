package com.example

import com.example.data.model.MetOfficeWeatherCode
import com.example.data.model.WeatherIconType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeatherCodeMappingTest {

    @Test
    fun `WMO snow grains do not map to hail`() {
        assertEquals(
            MetOfficeWeatherCode.LIGHT_SNOW,
            MetOfficeWeatherCode.fromWmoCode(77)
        )
    }

    @Test
    fun `WMO heavy snow showers retain heavy day and night variants`() {
        assertEquals(
            MetOfficeWeatherCode.HEAVY_SNOW_SHOWER_DAY,
            MetOfficeWeatherCode.fromWmoCode(86, isNight = false)
        )
        assertEquals(
            MetOfficeWeatherCode.HEAVY_SNOW_SHOWER_NIGHT,
            MetOfficeWeatherCode.fromWmoCode(86, isNight = true)
        )
    }

    @Test
    fun `Met Office heavy showers remain distinct from persistent heavy rain`() {
        val nightShower = MetOfficeWeatherCode.fromCode(13)
        val dayShower = MetOfficeWeatherCode.fromCode(14)
        val heavyRain = MetOfficeWeatherCode.fromCode(15)

        assertEquals("Heavy showers", nightShower.description)
        assertEquals(WeatherIconType.HEAVY_RAIN_SHOWER_NIGHT, nightShower.iconType)
        assertEquals("Heavy showers", dayShower.description)
        assertEquals(WeatherIconType.HEAVY_RAIN_SHOWER_DAY, dayShower.iconType)
        assertEquals("Heavy rain", heavyRain.description)
        assertEquals(WeatherIconType.HEAVY_RAIN, heavyRain.iconType)
    }

    @Test
    fun `Met Office thunder codes never collapse into heavy rain`() {
        listOf(28, 29, 30).forEach { code ->
            val weather = MetOfficeWeatherCode.fromCode(code)
            assertNotEquals(WeatherIconType.HEAVY_RAIN, weather.iconType)
            assertNotEquals(WeatherIconType.HEAVY_RAIN_SHOWER_DAY, weather.iconType)
            assertNotEquals(WeatherIconType.HEAVY_RAIN_SHOWER_NIGHT, weather.iconType)
        }
    }
}
