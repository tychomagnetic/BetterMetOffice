package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LocationItem(
    val id: String,
    val name: String,
    val region: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null,
    val isFavorite: Boolean = false,
    val isCurrentLocation: Boolean = false
) {
    val displayName: String
        get() = buildString {
            append(name)
            if (!region.isNullOrBlank() && region != name) {
                append(", ").append(region)
            } else if (!country.isNullOrBlank()) {
                append(", ").append(country)
            }
        }

    companion object {
        val DEFAULT_LOCATIONS = listOf(
            LocationItem("london_uk", "London", "Greater London", "United Kingdom", 51.5074, -0.1278, timezone = "Europe/London", isFavorite = true),
            LocationItem("edinburgh_uk", "Edinburgh", "Scotland", "United Kingdom", 55.9533, -3.1883, timezone = "Europe/London", isFavorite = true),
            LocationItem("manchester_uk", "Manchester", "Greater Manchester", "United Kingdom", 53.4808, -2.2426, timezone = "Europe/London", isFavorite = true),
            LocationItem("cardiff_uk", "Cardiff", "Wales", "United Kingdom", 51.4816, -3.1791, timezone = "Europe/London", isFavorite = true),
            LocationItem("belfast_uk", "Belfast", "Northern Ireland", "United Kingdom", 54.5973, -5.9301, timezone = "Europe/London", isFavorite = true),
            LocationItem("birmingham_uk", "Birmingham", "West Midlands", "United Kingdom", 52.4862, -1.8904, timezone = "Europe/London"),
            LocationItem("bristol_uk", "Bristol", "South West", "United Kingdom", 51.4545, -2.5879, timezone = "Europe/London"),
            LocationItem("glasgow_uk", "Glasgow", "Scotland", "United Kingdom", 55.8642, -4.2518, timezone = "Europe/London"),
            LocationItem("newcastle_uk", "Newcastle upon Tyne", "Tyne and Wear", "United Kingdom", 54.9783, -1.6178, timezone = "Europe/London"),
            LocationItem("oxford_uk", "Oxford", "Oxfordshire", "United Kingdom", 51.7520, -1.2577, timezone = "Europe/London"),
            LocationItem("cambridge_uk", "Cambridge", "Cambridgeshire", "United Kingdom", 52.2053, 0.1218, timezone = "Europe/London"),
            LocationItem("plymouth_uk", "Plymouth", "Devon", "United Kingdom", 50.3755, -4.1427, timezone = "Europe/London"),
            LocationItem("inverness_uk", "Inverness", "Highlands", "United Kingdom", 57.4778, -4.2247, timezone = "Europe/London"),
            LocationItem("york_uk", "York", "North Yorkshire", "United Kingdom", 53.9590, -1.0815, timezone = "Europe/London")
        )
    }
}
