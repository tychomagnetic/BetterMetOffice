package com.example.data.model

enum class MetOfficeWeatherCode(val code: Int, val description: String, val isDay: Boolean, val iconType: WeatherIconType) {
    CLEAR_NIGHT(0, "Clear night", false, WeatherIconType.CLEAR_NIGHT),
    SUNNY_DAY(1, "Sunny day", true, WeatherIconType.CLEAR_DAY),
    PARTLY_CLOUDY_NIGHT(2, "Partly cloudy", false, WeatherIconType.PARTLY_CLOUDY_NIGHT),
    SUNNY_INTERVALS(3, "Sunny intervals", true, WeatherIconType.PARTLY_CLOUDY_DAY),
    DUST(4, "Dust", true, WeatherIconType.FOG),
    MIST(5, "Mist", true, WeatherIconType.MIST),
    FOG(6, "Fog", true, WeatherIconType.FOG),
    CLOUDY(7, "Cloudy", true, WeatherIconType.CLOUDY),
    OVERCAST(8, "Overcast", true, WeatherIconType.OVERCAST),
    LIGHT_RAIN_SHOWER_NIGHT(9, "Light rain shower", false, WeatherIconType.RAIN_SHOWER_NIGHT),
    LIGHT_RAIN_SHOWER_DAY(10, "Light rain shower", true, WeatherIconType.RAIN_SHOWER_DAY),
    DRIZZLE(11, "Drizzle", true, WeatherIconType.DRIZZLE),
    LIGHT_RAIN(12, "Light rain", true, WeatherIconType.LIGHT_RAIN),
    HEAVY_RAIN_SHOWER_NIGHT(13, "Heavy rain shower", false, WeatherIconType.HEAVY_RAIN_NIGHT),
    HEAVY_RAIN_SHOWER_DAY(14, "Heavy rain shower", true, WeatherIconType.HEAVY_RAIN_DAY),
    HEAVY_RAIN(15, "Heavy rain", true, WeatherIconType.HEAVY_RAIN),
    SLEET_SHOWER_NIGHT(16, "Sleet shower", false, WeatherIconType.SLEET_NIGHT),
    SLEET_SHOWER_DAY(17, "Sleet shower", true, WeatherIconType.SLEET_DAY),
    SLEET(18, "Sleet", true, WeatherIconType.SLEET),
    HAIL_SHOWER_NIGHT(19, "Hail shower", false, WeatherIconType.HAIL),
    HAIL_SHOWER_DAY(20, "Hail shower", true, WeatherIconType.HAIL),
    HAIL(21, "Hail", true, WeatherIconType.HAIL),
    LIGHT_SNOW_SHOWER_NIGHT(22, "Light snow shower", false, WeatherIconType.SNOW_NIGHT),
    LIGHT_SNOW_SHOWER_DAY(23, "Light snow shower", true, WeatherIconType.SNOW_DAY),
    LIGHT_SNOW(24, "Light snow", true, WeatherIconType.SNOW),
    HEAVY_SNOW_SHOWER_NIGHT(25, "Heavy snow shower", false, WeatherIconType.HEAVY_SNOW_NIGHT),
    HEAVY_SNOW_SHOWER_DAY(26, "Heavy snow shower", true, WeatherIconType.HEAVY_SNOW_DAY),
    HEAVY_SNOW(27, "Heavy snow", true, WeatherIconType.HEAVY_SNOW),
    THUNDER_SHOWER_NIGHT(28, "Thunder shower", false, WeatherIconType.THUNDERSTORM_NIGHT),
    THUNDER_SHOWER_DAY(29, "Thunder shower", true, WeatherIconType.THUNDERSTORM_DAY),
    THUNDER(30, "Thunder", true, WeatherIconType.THUNDERSTORM);

    companion object {
        fun fromCode(code: Int?, isNightFallback: Boolean = false): MetOfficeWeatherCode {
            if (code == null) {
                return if (isNightFallback) CLEAR_NIGHT else SUNNY_DAY
            }
            return entries.find { it.code == code } ?: if (isNightFallback) CLEAR_NIGHT else SUNNY_DAY
        }

        fun fromWmoCode(wmoCode: Int, isNight: Boolean = false): MetOfficeWeatherCode {
            return when (wmoCode) {
                0 -> if (isNight) CLEAR_NIGHT else SUNNY_DAY
                1, 2 -> if (isNight) PARTLY_CLOUDY_NIGHT else SUNNY_INTERVALS
                3 -> OVERCAST
                45, 48 -> FOG
                51, 53, 55 -> DRIZZLE
                56, 57 -> SLEET
                61, 63 -> LIGHT_RAIN
                65 -> HEAVY_RAIN
                66, 67 -> SLEET
                71, 73 -> LIGHT_SNOW
                75 -> HEAVY_SNOW
                77 -> LIGHT_SNOW
                80, 81 -> if (isNight) LIGHT_RAIN_SHOWER_NIGHT else LIGHT_RAIN_SHOWER_DAY
                82 -> if (isNight) HEAVY_RAIN_SHOWER_NIGHT else HEAVY_RAIN_SHOWER_DAY
                85 -> if (isNight) LIGHT_SNOW_SHOWER_NIGHT else LIGHT_SNOW_SHOWER_DAY
                86 -> if (isNight) HEAVY_SNOW_SHOWER_NIGHT else HEAVY_SNOW_SHOWER_DAY
                95 -> if (isNight) THUNDER_SHOWER_NIGHT else THUNDER_SHOWER_DAY
                96, 99 -> THUNDER
                else -> if (isNight) PARTLY_CLOUDY_NIGHT else SUNNY_INTERVALS
            }
        }
    }
}

enum class WeatherIconType {
    CLEAR_DAY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY_DAY,
    PARTLY_CLOUDY_NIGHT,
    CLOUDY,
    OVERCAST,
    MIST,
    FOG,
    DRIZZLE,
    LIGHT_RAIN,
    RAIN_SHOWER_DAY,
    RAIN_SHOWER_NIGHT,
    HEAVY_RAIN,
    HEAVY_RAIN_DAY,
    HEAVY_RAIN_NIGHT,
    SLEET,
    SLEET_DAY,
    SLEET_NIGHT,
    HAIL,
    SNOW,
    SNOW_DAY,
    SNOW_NIGHT,
    HEAVY_SNOW,
    HEAVY_SNOW_DAY,
    HEAVY_SNOW_NIGHT,
    THUNDERSTORM,
    THUNDERSTORM_DAY,
    THUNDERSTORM_NIGHT
}

data class CurrentWeather(
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val weatherCode: MetOfficeWeatherCode,
    val maxTempCelsius: Double,
    val minTempCelsius: Double,
    val humidityPercent: Int,
    val windSpeedMph: Double,
    val windGustMph: Double,
    val windDirectionDegrees: Int,
    val precipitationChance: Int,
    val uvIndex: Int,
    val visibilityMeters: Int,
    val pressureHpa: Double,
    val timestamp: String,
    val isNight: Boolean
) {
    val windDirectionCompass: String
        get() {
            val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
            val index = (((windDirectionDegrees % 360) + 11.25) / 22.5).toInt() % 16
            return directions[index]
        }

    val visibilityCategory: String
        get() = when {
            visibilityMeters < 1000 -> "Very Poor (Fog)"
            visibilityMeters < 4000 -> "Poor"
            visibilityMeters < 10000 -> "Moderate"
            visibilityMeters < 20000 -> "Good"
            visibilityMeters < 40000 -> "Very Good"
            else -> "Excellent"
        }

    val uvCategory: String
        get() = when (uvIndex) {
            0, 1, 2 -> "Low"
            3, 4, 5 -> "Moderate"
            6, 7 -> "High"
            8, 9, 10 -> "Very High"
            else -> "Extreme"
        }
}

data class HourlyForecastItem(
    val timeLabel: String,
    val fullTime: String,
    val date: String = fullTime.take(10),
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val weatherCode: MetOfficeWeatherCode,
    val precipitationChance: Int,
    val windSpeedMph: Double,
    val windDirectionDegrees: Int,
    val humidityPercent: Int,
    val uvIndex: Int,
    val pressureHpa: Double = 1013.25,
    val isNow: Boolean = false
) {
    val windDirectionCompass: String
        get() {
            val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
            val index = (((windDirectionDegrees % 360) + 11.25) / 22.5).toInt() % 16
            return directions[index]
        }
}

data class DailyForecastItem(
    val date: String,
    val dayOfWeek: String,
    val dateFormatted: String,
    val maxTempCelsius: Double,
    val minTempCelsius: Double,
    val dayWeatherCode: MetOfficeWeatherCode,
    val nightWeatherCode: MetOfficeWeatherCode,
    val precipitationChance: Int,
    val uvIndex: Int,
    val maxWindGustMph: Double,
    val sunrise: String? = null,
    val sunset: String? = null
)

data class WeatherReport(
    val location: LocationItem,
    val current: CurrentWeather,
    val hourly: List<HourlyForecastItem>,
    val daily: List<DailyForecastItem>,
    val dataSource: WeatherDataSource,
    val modelRunTime: String? = null,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
    val partialFallbackSource: WeatherDataSource? = null
)

enum class WeatherDataSource(val displayName: String, val isOfficialMetOffice: Boolean) {
    MET_OFFICE_DATAHUB("Met Office DataHub API (Site-Specific)", true),
    MET_OFFICE_BPF("Met Office BPF Advanced Model", true),
    MET_OFFICE_DATAPOINT("Met Office DataPoint API", true),
    OPEN_METEO_METEOROLOGICAL("Meteorological Unified Model (Demo)", false)
}

/** The source selected in Settings. Kept separate from [WeatherDataSource], which describes a report. */
enum class ForecastSource {
    MET_OFFICE_SPOT,
    MET_OFFICE_BPF,
    OPEN_METEO
}

enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F");

    fun convert(celsius: Double): Double = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> (celsius * 9.0 / 5.0) + 32.0
    }

    fun format(celsius: Double): String = "${convert(celsius).toInt()}$symbol"
}

enum class WindSpeedUnit(val label: String) {
    MPH("mph"),
    KPH("km/h"),
    KNOTS("knots");

    fun convert(mph: Double): Double = when (this) {
        MPH -> mph
        KPH -> mph * 1.60934
        KNOTS -> mph * 0.868976
    }

    fun format(mph: Double): String = "${convert(mph).toInt()} $label"
}

enum class PressureUnit(val label: String) {
    HPA("hPa"),
    MBAR("mbar"),
    INHG("inHg");

    fun convert(hpa: Double): Double = when (this) {
        HPA -> hpa
        MBAR -> hpa
        INHG -> hpa * 0.02953
    }

    fun format(hpa: Double): String = when (this) {
        INHG -> String.format("%.2f %s", convert(hpa), label)
        else -> "${convert(hpa).toInt()} $label"
    }
}
