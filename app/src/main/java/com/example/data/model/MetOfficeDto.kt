package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MetOfficeHourlyResponse(
    @Json(name = "type") val type: String? = null,
    @Json(name = "features") val features: List<MetOfficeHourlyFeature>? = null
)

@JsonClass(generateAdapter = true)
data class MetOfficeHourlyFeature(
    @Json(name = "type") val type: String? = null,
    @Json(name = "geometry") val geometry: MetOfficeGeometry? = null,
    @Json(name = "properties") val properties: MetOfficeHourlyProperties? = null
)

@JsonClass(generateAdapter = true)
data class MetOfficeGeometry(
    @Json(name = "type") val type: String? = null,
    @Json(name = "coordinates") val coordinates: List<Double>? = null
)

@JsonClass(generateAdapter = true)
data class MetOfficeLocation(
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class MetOfficeHourlyProperties(
    @Json(name = "location") val location: MetOfficeLocation? = null,
    @Json(name = "modelRunDate") val modelRunDate: String? = null,
    @Json(name = "timeSeries") val timeSeries: List<MetOfficeHourlyTimeSeriesItem>? = null
)

@JsonClass(generateAdapter = true)
data class MetOfficeHourlyTimeSeriesItem(
    @Json(name = "time") val time: String? = null,
    @Json(name = "screenTemperature") val screenTemperature: Double? = null,
    @Json(name = "maxScreenAirTemp") val maxScreenAirTemp: Double? = null,
    @Json(name = "minScreenAirTemp") val minScreenAirTemp: Double? = null,
    @Json(name = "screenApparentTemperature") val screenApparentTemperature: Double? = null,
    @Json(name = "feelsLikeTemp") val feelsLikeTemp: Double? = null,
    @Json(name = "feelsLikeTemperature") val feelsLikeTemperature: Double? = null,
    @Json(name = "screenDewPointTemperature") val screenDewPointTemperature: Double? = null,
    @Json(name = "screenRelativeHumidity") val screenRelativeHumidity: Double? = null,
    @Json(name = "significantWeatherCode") val significantWeatherCode: Int? = null,
    @Json(name = "probOfPrecipitation") val probOfPrecipitation: Int? = null,
    @Json(name = "totalPrecipAmount") val totalPrecipAmount: Double? = null,
    @Json(name = "totalSnowAmount") val totalSnowAmount: Double? = null,
    @Json(name = "windSpeed10m") val windSpeed10m: Double? = null, // in m/s or knots/mph depending on unit, DataHub returns m/s
    @Json(name = "windGustSpeed10m") val windGustSpeed10m: Double? = null,
    @Json(name = "windDirectionFrom10m") val windDirectionFrom10m: Int? = null,
    @Json(name = "visibility") val visibility: Int? = null, // in meters
    @Json(name = "mslp") val mslp: Double? = null, // in Pa (e.g. 101325) or hPa
    @Json(name = "uvIndex") val uvIndex: Int? = null
)

// Daily API Response
@JsonClass(generateAdapter = true)
data class MetOfficeDailyResponse(
    @Json(name = "type") val type: String? = null,
    @Json(name = "features") val features: List<MetOfficeDailyFeature>? = null
)

@JsonClass(generateAdapter = true)
data class MetOfficeDailyFeature(
    @Json(name = "type") val type: String? = null,
    @Json(name = "geometry") val geometry: MetOfficeGeometry? = null,
    @Json(name = "properties") val properties: MetOfficeDailyProperties? = null
)

@JsonClass(generateAdapter = true)
data class MetOfficeDailyProperties(
    @Json(name = "location") val location: MetOfficeLocation? = null,
    @Json(name = "modelRunDate") val modelRunDate: String? = null,
    @Json(name = "timeSeries") val timeSeries: List<MetOfficeDailyTimeSeriesItem>? = null
)

@JsonClass(generateAdapter = true)
data class MetOfficeDailyTimeSeriesItem(
    @Json(name = "time") val time: String? = null,
    @Json(name = "daySignificantWeatherCode") val daySignificantWeatherCode: Int? = null,
    @Json(name = "nightSignificantWeatherCode") val nightSignificantWeatherCode: Int? = null,
    @Json(name = "dayMaxScreenTemperature") val dayMaxScreenTemperature: Double? = null,
    @Json(name = "nightMinScreenTemperature") val nightMinScreenTemperature: Double? = null,
    @Json(name = "dayMaxFeelsLikeTemp") val dayMaxFeelsLikeTemp: Double? = null,
    @Json(name = "nightMinFeelsLikeTemp") val nightMinFeelsLikeTemp: Double? = null,
    @Json(name = "dayProbabilityOfPrecipitation") val dayProbabilityOfPrecipitation: Int? = null,
    @Json(name = "nightProbabilityOfPrecipitation") val nightProbabilityOfPrecipitation: Int? = null,
    @Json(name = "dayMaxScreenGustSpeed10m") val dayMaxScreenGustSpeed10m: Double? = null,
    @Json(name = "nightMaxScreenGustSpeed10m") val nightMaxScreenGustSpeed10m: Double? = null,
    @Json(name = "dayWindSpeed10m") val dayWindSpeed10m: Double? = null,
    @Json(name = "nightWindSpeed10m") val nightWindSpeed10m: Double? = null,
    @Json(name = "dayWindDirectionFrom10m") val dayWindDirectionFrom10m: Int? = null,
    @Json(name = "nightWindDirectionFrom10m") val nightWindDirectionFrom10m: Int? = null,
    @Json(name = "dayRelativeHumidity") val dayRelativeHumidity: Double? = null,
    @Json(name = "nightRelativeHumidity") val nightRelativeHumidity: Double? = null,
    @Json(name = "middayUvIndex") val middayUvIndex: Int? = null
)
