package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Met Office Site-Specific Blended Probabilistic Forecast (BPF) EDR service.
 * Responses use CoverageJSON and are deliberately kept as raw bodies: the service
 * advertises application/prs.coverage+json, which Retrofit's JSON converter does
 * not consistently recognise on Android.
 */
interface MetOfficeBpfApiService {

    @GET("collections/uk-spot-percentiles/instances/blended/position")
    suspend fun getUkPercentiles(
        @Query("coords") coords: String,
        @Query("parameter-name") parameterNames: String,
        @Query("datetime") datetime: String,
        @Header("apikey") apiKey: String
    ): Response<ResponseBody>

    @GET("collections/uk-spot-probabilities/instances/blended/position")
    suspend fun getUkProbabilities(
        @Query("coords") coords: String,
        @Query("parameter-name") parameterNames: String,
        @Query("datetime") datetime: String,
        @Header("apikey") apiKey: String
    ): Response<ResponseBody>

    @GET("collections")
    suspend fun getCollections(
        @Header("apikey") apiKey: String
    ): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://data.hub.api.metoffice.gov.uk/mo-blended-prob-forecast-feature-svc/2.0.0/"
    }
}
