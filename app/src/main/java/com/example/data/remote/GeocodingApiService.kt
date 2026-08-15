package com.example.data.remote

import com.example.data.model.GeocodingSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApiService {

    @GET("v1/search")
    suspend fun searchLocations(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): Response<GeocodingSearchResponse>

    companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/"
    }
}
