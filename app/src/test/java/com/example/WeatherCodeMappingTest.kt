package com.example

import com.example.data.model.MetOfficeWeatherCode
import org.junit.Assert.assertEquals
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
}
