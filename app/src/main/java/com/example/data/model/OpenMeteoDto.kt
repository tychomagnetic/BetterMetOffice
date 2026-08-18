package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "elevation") val elevation: Double? = null,
    @Json(name = "timezone") val timezone: String? = null,
    @Json(name = "current") val current: OpenMeteoCurrent? = null,
    @Json(name = "hourly") val hourly: OpenMeteoHourly? = null,
    @Json(name = "daily") val daily: OpenMeteoDaily? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoCurrent(
    @Json(name = "time") val time: String? = null,
    @Json(name = "temperature_2m") val temperature2m: Double? = null,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int? = null,
    @Json(name = "apparent_temperature") val apparentTemperature: Double? = null,
    @Json(name = "precipitation") val precipitation: Double? = null,
    @Json(name = "precipitation_probability") val precipitationProbability: Int? = null,
    @Json(name = "weather_code") val weatherCode: Int? = null,
    @Json(name = "pressure_msl") val pressureMsl: Double? = null,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double? = null,
    @Json(name = "wind_direction_10m") val windDirection10m: Int? = null,
    @Json(name = "wind_gusts_10m") val windGusts10m: Double? = null,
    @Json(name = "visibility") val visibility: Double? = null,
    @Json(name = "uv_index") val uvIndex: Double? = null,
    @Json(name = "is_day") val isDay: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourly(
    @Json(name = "time") val time: List<String>? = null,
    @Json(name = "temperature_2m") val temperature2m: List<Double>? = null,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: List<Int>? = null,
    @Json(name = "apparent_temperature") val apparentTemperature: List<Double>? = null,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int>? = null,
    @Json(name = "weather_code") val weatherCode: List<Int>? = null,
    @Json(name = "pressure_msl") val pressureMsl: List<Double>? = null,
    @Json(name = "visibility") val visibility: List<Double>? = null,
    @Json(name = "wind_speed_10m") val windSpeed10m: List<Double>? = null,
    @Json(name = "wind_direction_10m") val windDirection10m: List<Int>? = null,
    @Json(name = "uv_index") val uvIndex: List<Double>? = null,
    @Json(name = "is_day") val isDay: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoDaily(
    @Json(name = "time") val time: List<String>? = null,
    @Json(name = "weather_code") val weatherCode: List<Int>? = null,
    @Json(name = "temperature_2m_max") val temperature2mMax: List<Double>? = null,
    @Json(name = "temperature_2m_min") val temperature2mMin: List<Double>? = null,
    @Json(name = "apparent_temperature_max") val apparentTemperatureMax: List<Double>? = null,
    @Json(name = "apparent_temperature_min") val apparentTemperatureMin: List<Double>? = null,
    @Json(name = "sunrise") val sunrise: List<String>? = null,
    @Json(name = "sunset") val sunset: List<String>? = null,
    @Json(name = "uv_index_max") val uvIndexMax: List<Double>? = null,
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int>? = null,
    @Json(name = "wind_speed_10m_max") val windSpeed10mMax: List<Double>? = null,
    @Json(name = "wind_gusts_10m_max") val windGusts10mMax: List<Double>? = null
)
