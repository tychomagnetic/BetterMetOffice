package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeocodingSearchResponse(
    @Json(name = "results") val results: List<GeocodingResultItem>? = null
)

@JsonClass(generateAdapter = true)
data class GeocodingResultItem(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "name") val name: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "elevation") val elevation: Double? = null,
    @Json(name = "country_code") val countryCode: String? = null,
    @Json(name = "timezone") val timezone: String? = null,
    @Json(name = "country") val country: String? = null,
    @Json(name = "admin1") val admin1: String? = null,
    @Json(name = "admin2") val admin2: String? = null,
    @Json(name = "postcodes") val postcodes: List<String>? = null
) {
    fun toLocationItem(): LocationItem {
        return LocationItem(
            id = "${id ?: "${latitude}_${longitude}"}",
            name = name,
            region = admin1 ?: admin2,
            country = country ?: countryCode,
            latitude = latitude,
            longitude = longitude,
            timezone = timezone
        )
    }
}
