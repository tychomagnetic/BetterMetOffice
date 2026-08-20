package com.example.data.repository

import android.content.Context
import com.example.data.local.PreferencesManager
import com.example.data.model.MapCatalogResult
import com.example.data.model.MapManifestCache
import com.example.data.model.MapOrder
import com.example.data.remote.MetOfficeMapImagesApiService
import com.example.data.util.MapImagesUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class MapImagesRepository(
    context: Context,
    private val preferences: PreferencesManager,
    private val api: MetOfficeMapImagesApiService = createApi(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val imageCacheDirectory = File(context.cacheDir, "map-images").apply { mkdirs() }

    suspend fun testApiKey(apiKey: String): ApiKeyTestResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext ApiKeyTestResult.Error("Map Images API key cannot be empty.")
        try {
            val response = api.getOrders(apiKey.trim())
            when {
                response.isSuccessful && response.body()?.orders?.any { it.isCompatiblePngOrder() } == true ->
                    ApiKeyTestResult.Success("Verified with Met Office Map Images")
                response.isSuccessful -> ApiKeyTestResult.Error("No compatible PNG map order was found for this key.")
                response.code() == 401 -> ApiKeyTestResult.Error("Invalid Map Images API key (HTTP 401).")
                response.code() == 403 -> ApiKeyTestResult.Error("Map Images subscription not enabled (HTTP 403).")
                else -> ApiKeyTestResult.Error("Map Images server responded with HTTP ${response.code()}.")
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            ApiKeyTestResult.Error("Map Images connection error: ${error.localizedMessage ?: "request failed"}")
        }
    }

    suspend fun loadCatalog(
        apiKey: String,
        forceRefresh: Boolean = false,
        requestedOrderId: String? = null
    ): MapCatalogResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add a Met Office Map Images API key in Settings first." }
        val boundary = MapImagesUtils.latestRunBoundaryMillis(nowMillis())
        val cached = preferences.getMapManifestCache()
        val wantedOrder = requestedOrderId?.takeIf { it.isNotBlank() }
            ?: preferences.getSelectedMapOrderId().takeIf { it.isNotBlank() }
        if (!forceRefresh && cached != null && cached.checkedBoundaryMillis == boundary &&
            MapImagesUtils.runMeetsBoundary(cached.runDateTime, boundary) &&
            cached.frames.isNotEmpty() && (wantedOrder == null || cached.orderId == wantedOrder)
        ) {
            return@withContext MapCatalogResult(cached)
        }

        try {
            val ordersResponse = api.getOrders(apiKey.trim())
            if (!ordersResponse.isSuccessful) error("Map order request failed (HTTP ${ordersResponse.code()}).")
            val orders = ordersResponse.body()?.orders.orEmpty().filter { it.isCompatiblePngOrder() }
            if (orders.isEmpty()) error("No compatible PNG map order is available for this API key.")
            val order = orders.firstOrNull { it.orderId.equals(wantedOrder, ignoreCase = true) }
                ?: orders.singleOrNull()
                ?: orders.first()

            val latestResponse = api.getLatest(order.orderId, apiKey.trim())
            if (!latestResponse.isSuccessful) error("Latest map manifest failed (HTTP ${latestResponse.code()}).")
            val frames = MapImagesUtils.newestImmutableFrames(latestResponse.body()?.orderDetails?.files.orEmpty())
            if (frames.isEmpty()) error("The latest map run contains no usable PNG frames.")
            val catalog = MapManifestCache(
                checkedBoundaryMillis = boundary,
                orderId = order.orderId,
                orderName = order.name.ifBlank { order.orderId },
                availableOrders = orders,
                runDateTime = frames.first().runDateTime,
                frames = frames
            )
            preferences.setSelectedMapOrderId(order.orderId)
            preferences.setMapManifestCache(catalog)
            val latestRunPending = !MapImagesUtils.runMeetsBoundary(catalog.runDateTime, boundary)
            MapCatalogResult(
                catalog,
                warning = if (latestRunPending) {
                    "The latest 00:00/12:00 UTC map run is not available yet. It will be checked again next time you open Weather Maps."
                } else null
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (cached != null && cached.frames.isNotEmpty() && (wantedOrder == null || cached.orderId == wantedOrder)) {
                MapCatalogResult(cached, "Could not check the latest run. Showing the last cached map run.")
            } else {
                throw error
            }
        }
    }

    suspend fun loadImage(apiKey: String, orderId: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val safeName = fileId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val cacheFile = File(imageCacheDirectory, "${orderId}_${safeName}_land_legend.png")
        if (cacheFile.isFile && cacheFile.length() > 0) return@withContext cacheFile.readBytes()

        var attempt = 0
        while (true) {
            val response = api.getImage(orderId, fileId, apiKey = apiKey.trim())
            if (response.isSuccessful) {
                val bytes = response.body()?.bytes() ?: error("Map image response was empty.")
                if (bytes.size < 8 || bytes[0] != 0x89.toByte() || bytes[1] != 0x50.toByte()) {
                    error("Map image response was not a PNG.")
                }
                cacheFile.writeBytes(bytes)
                return@withContext bytes
            }

            response.errorBody()?.close()
            val retryAfterMillis = response.headers()["Retry-After"]
                ?.toLongOrNull()
                ?.coerceIn(1L, 60L)
                ?.times(1_000L)
            val retryableGatewayError = response.code() in setOf(502, 503, 504)
            val retryableRateLimit = response.code() == 429 && retryAfterMillis != null
            if (attempt >= MAX_IMAGE_RETRIES || (!retryableGatewayError && !retryableRateLimit)) {
                error("Map image request failed (HTTP ${response.code()}).")
            }

            val backoffMillis = retryAfterMillis ?: (1_000L shl attempt)
            attempt++
            delay(backoffMillis)
        }
        @Suppress("UNREACHABLE_CODE")
        error("Map image retry loop ended unexpectedly.")
    }

    private fun MapOrder.isCompatiblePngOrder(): Boolean =
        format.equals("PNG", ignoreCase = true) && orderId.isNotBlank()

    companion object {
        private const val MAX_IMAGE_RETRIES = 2

        private fun createApi(): MetOfficeMapImagesApiService {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(MetOfficeMapImagesApiService.BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(MetOfficeMapImagesApiService::class.java)
        }
    }
}
