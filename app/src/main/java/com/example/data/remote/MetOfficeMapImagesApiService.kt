package com.example.data.remote

import com.example.data.model.MapLatestResponse
import com.example.data.model.MapOrdersResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface MetOfficeMapImagesApiService {
    @GET("orders")
    suspend fun getOrders(@Header("apikey") apiKey: String): Response<MapOrdersResponse>

    @GET("orders/{orderId}/latest")
    suspend fun getLatest(
        @Path("orderId") orderId: String,
        @Header("apikey") apiKey: String
    ): Response<MapLatestResponse>

    @GET("orders/{orderId}/latest/{fileId}/data")
    suspend fun getImage(
        @Path("orderId") orderId: String,
        @Path("fileId") fileId: String,
        @Query("includeLand") includeLand: Boolean = true,
        @Query("legend") legend: Boolean = true,
        @Header("apikey") apiKey: String,
        @Header("Accept") accept: String = "application/x-grib"
    ): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://data.hub.api.metoffice.gov.uk/map-images/1.0.0/"
    }
}
