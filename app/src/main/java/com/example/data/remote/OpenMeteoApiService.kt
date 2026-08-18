package com.example.data.remote

import com.example.data.model.OpenMeteoResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApiService {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMETERS,
        @Query("hourly") hourly: String = HOURLY_PARAMETERS,
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,sunrise,sunset,uv_index_max,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("timezone") timezone: String = "auto",
        @Query("wind_speed_unit") windSpeedUnit: String = "mph"
    ): Response<OpenMeteoResponse>

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        const val CURRENT_PARAMETERS = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,precipitation_probability,weather_code,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility,uv_index,is_day"
        const val HOURLY_PARAMETERS = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation_probability,weather_code,pressure_msl,visibility,wind_speed_10m,wind_direction_10m,uv_index,is_day"
    }
}
