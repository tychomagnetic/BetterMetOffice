package com.example.data.repository

import com.example.data.local.PreferencesManager
import com.example.data.model.ApiDebugInfo
import com.example.data.model.CoordinateTestResult
import com.example.data.model.CurrentWeather
import com.example.data.model.DailyForecastItem
import com.example.data.model.BpfCoverage
import com.example.data.model.BpfCoverageCollection
import com.example.data.model.ForecastSource
import com.example.data.model.GeocodingSearchResponse
import com.example.data.model.HourlyForecastItem
import com.example.data.model.LocationItem
import com.example.data.model.MetOfficeDailyResponse
import com.example.data.model.MetOfficeDailyTimeSeriesItem
import com.example.data.model.MetOfficeHourlyResponse
import com.example.data.model.MetOfficeHourlyTimeSeriesItem
import com.example.data.model.MetOfficeWeatherCode
import com.example.data.model.OpenMeteoResponse
import com.example.data.model.WeatherDataSource
import com.example.data.model.WeatherReport
import com.example.data.remote.GeocodingApiService
import com.example.data.remote.MetOfficeApiService
import com.example.data.remote.MetOfficeBpfApiService
import com.example.data.remote.OpenMeteoApiService
import com.example.data.util.TimezoneUtils
import com.example.data.util.BpfIntervalUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

sealed class ApiKeyTestResult {
    data class Success(val message: String = "Success! Met Office API verified.") : ApiKeyTestResult()
    data class Error(val message: String) : ApiKeyTestResult()
}

class WeatherRepository(
    private val preferencesManager: PreferencesManager,
    private val metOfficeApi: MetOfficeApiService = createMetOfficeApi(),
    private val metOfficeBpfApi: MetOfficeBpfApiService = createMetOfficeBpfApi(),
    private val openMeteoApi: OpenMeteoApiService = createOpenMeteoApi(),
    private val geocodingApi: GeocodingApiService = createGeocodingApi()
) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val _debugInfo = MutableStateFlow<ApiDebugInfo?>(null)
    val debugInfo: StateFlow<ApiDebugInfo?> = _debugInfo.asStateFlow()

    private fun <T> toPrettyJson(clazz: Class<T>, obj: T?): String {
        if (obj == null) return "null"
        return try {
            moshi.adapter(clazz).indent("  ").toJson(obj)
        } catch (e: Exception) {
            "{\"error\": \"Failed to format JSON: ${e.message}\"}"
        }
    }

    suspend fun testApiKey(apiKey: String, clientSecret: String = ""): ApiKeyTestResult =
        testMetOfficeApiKey(apiKey, clientSecret)

    suspend fun testMetOfficeApiKey(apiKey: String, clientSecret: String = ""): ApiKeyTestResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ApiKeyTestResult.Error("API Key cannot be empty.")
        }
        try {
            // Test against London (51.5074, -0.1278)
            val response = metOfficeApi.getPointHourly(
                latitude = 51.5074,
                longitude = -0.1278,
                apiKey = apiKey.trim(),
                clientId = apiKey.trim(),
                clientSecret = clientSecret.trim().ifBlank { null }
            )

            if (response.isSuccessful && response.body() != null) {
                val features = response.body()?.features
                if (!features.isNullOrEmpty()) {
                    val locationName = features.first().properties?.location?.name ?: "Met Office DataHub"
                    ApiKeyTestResult.Success("Verified with Met Office DataHub ($locationName)")
                } else {
                    ApiKeyTestResult.Success("Connected to Met Office DataHub")
                }
            } else {
                when (response.code()) {
                    401 -> ApiKeyTestResult.Error("Invalid API Key (HTTP 401 Unauthorized). Please check your key from Met Office DataHub.")
                    403 -> ApiKeyTestResult.Error("Access Forbidden (HTTP 403). Please verify your subscription to the Site-Specific forecast plan.")
                    404 -> ApiKeyTestResult.Error("Endpoint not found (HTTP 404).")
                    429 -> ApiKeyTestResult.Error("Rate limited (HTTP 429). Too many requests.")
                    else -> ApiKeyTestResult.Error("Met Office server responded with status: ${response.code()} ${response.message()}")
                }
            }
        } catch (e: Exception) {
            ApiKeyTestResult.Error("Connection error: ${e.localizedMessage ?: "Failed to reach Met Office server"}")
        }
    }

    suspend fun testBpfApiKey(apiKey: String): ApiKeyTestResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext ApiKeyTestResult.Error("BPF API key cannot be empty.")
        try {
            val response = metOfficeBpfApi.getCollections(apiKey.trim())
            response.body()?.close()
            if (response.isSuccessful) {
                ApiKeyTestResult.Success("Verified with Met Office BPF Advanced Model")
            } else {
                when (response.code()) {
                    401 -> ApiKeyTestResult.Error("Invalid BPF API key (HTTP 401).")
                    403 -> ApiKeyTestResult.Error("BPF subscription not enabled (HTTP 403).")
                    429 -> ApiKeyTestResult.Error("BPF rate limit reached (HTTP 429).")
                    else -> ApiKeyTestResult.Error("BPF server responded with HTTP ${response.code()}.")
                }
            }
        } catch (e: Exception) {
            ApiKeyTestResult.Error("BPF connection error: ${e.localizedMessage ?: "Failed to reach Met Office"}")
        }
    }

    suspend fun getWeatherReport(location: LocationItem): Result<WeatherReport> = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.getApiKey()
        val bpfApiKey = preferencesManager.getBpfApiKey()
        val clientSecret = preferencesManager.getClientSecret()
        val selectedSource = preferencesManager.getForecastSource()
        val startTime = System.currentTimeMillis()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        if (selectedSource == ForecastSource.MET_OFFICE_BPF && bpfApiKey.isNotBlank()) {
            try {
                return@withContext Result.success(
                    fetchBpfWeatherReport(
                        location = location,
                        apiKey = bpfApiKey,
                        spotApiKey = apiKey,
                        spotClientSecret = clientSecret,
                        startTime = startTime,
                        timestamp = timestamp
                    )
                )
            } catch (_: Exception) {
                // BPF is deliberately allowed to fall back to the free source if its limited service is unavailable.
            }
        }

        // Spot is also the first fallback for BPF. This matters for locations
        // outside BPF coverage and when the probabilistic service is unavailable.
        // Open-Meteo remains the final fallback if Spot is unavailable too.
        if (selectedSource != ForecastSource.OPEN_METEO && apiKey.isNotBlank()) {
            try {
                // Fetch hourly, three-hourly, and daily concurrently from Met Office DataHub
                val hourlyDeferred = async {
                    try {
                        metOfficeApi.getPointHourly(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            apiKey = apiKey,
                            clientId = apiKey,
                            clientSecret = clientSecret.ifBlank { null }
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

                val threeHourlyDeferred = async {
                    try {
                        metOfficeApi.getPointThreeHourly(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            apiKey = apiKey,
                            clientId = apiKey,
                            clientSecret = clientSecret.ifBlank { null }
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

                val dailyDeferred = async {
                    try {
                        metOfficeApi.getPointDaily(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            apiKey = apiKey,
                            clientId = apiKey,
                            clientSecret = clientSecret.ifBlank { null }
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

                val hourlyResponse = hourlyDeferred.await()
                val threeHourlyResponse = threeHourlyDeferred.await()
                val dailyResponse = dailyDeferred.await()

                if (hourlyResponse != null && hourlyResponse.isSuccessful && hourlyResponse.body() != null) {
                    val duration = System.currentTimeMillis() - startTime
                    val hourlyBody = hourlyResponse.body()!!
                    val threeHourlyBody = threeHourlyResponse?.body()
                    val dailyBody = dailyResponse?.body()

                    val feature = hourlyBody.features?.firstOrNull()
                    val coords = feature?.geometry?.coordinates
                    val resolvedLon = coords?.getOrNull(0)
                    val resolvedLat = coords?.getOrNull(1)
                    val resolvedName = feature?.properties?.location?.name

                    requireResolvedLocationMatchesRequest(
                        requested = location,
                        resolvedLatitude = resolvedLat,
                        resolvedLongitude = resolvedLon,
                        sourceName = "Met Office Spot",
                        maximumDistanceKm = MAX_SPOT_RESOLVED_LOCATION_DISTANCE_KM
                    )

                    val hourlyJson = toPrettyJson(MetOfficeHourlyResponse::class.java, hourlyBody)
                    val threeHourlyJson = if (threeHourlyBody != null) toPrettyJson(MetOfficeHourlyResponse::class.java, threeHourlyBody) else null
                    val dailyJson = if (dailyBody != null) toPrettyJson(MetOfficeDailyResponse::class.java, dailyBody) else null

                    _debugInfo.update { old ->
                        ApiDebugInfo(
                            location = location,
                            dataSource = WeatherDataSource.MET_OFFICE_DATAHUB,
                            requestUrl = "https://data.hub.api.metoffice.gov.uk/sitespecific/v0/point/hourly?latitude=${location.latitude}&longitude=${location.longitude}",
                            httpStatusCode = hourlyResponse.code(),
                            httpMessage = hourlyResponse.message().ifBlank { "OK" },
                            responseTimeMs = duration,
                            rawJsonHourly = hourlyJson,
                            rawJsonThreeHourly = threeHourlyJson,
                            rawJsonDaily = dailyJson,
                            lastGeocodingQuery = old?.lastGeocodingQuery,
                            rawJsonGeocoding = old?.rawJsonGeocoding,
                            serverResolvedLat = resolvedLat,
                            serverResolvedLon = resolvedLon,
                            serverResolvedName = resolvedName,
                            timestamp = timestamp
                        )
                    }

                    val report = mapMetOfficeResponse(
                        location = location,
                        hourly = hourlyBody,
                        threeHourly = if (threeHourlyResponse != null && threeHourlyResponse.isSuccessful) threeHourlyBody else null,
                        daily = if (dailyResponse != null && dailyResponse.isSuccessful) dailyBody else null
                    )
                    return@withContext Result.success(report)
                }
            } catch (e: Exception) {
                // Fallback to meteorological model if Met Office fails or times out
            }
        }

        // Fallback or demo mode when no key or if key request failed
        try {
            val response = openMeteoApi.getForecast(
                latitude = location.latitude,
                longitude = location.longitude
            )
            val duration = System.currentTimeMillis() - startTime
            if (response.isSuccessful && response.body() != null) {
                val res = response.body()!!
                val rawJson = toPrettyJson(OpenMeteoResponse::class.java, res)

                _debugInfo.update { old ->
                    ApiDebugInfo(
                        location = location,
                        dataSource = WeatherDataSource.OPEN_METEO_METEOROLOGICAL,
                        requestUrl = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=${OpenMeteoApiService.CURRENT_PARAMETERS}&hourly=${OpenMeteoApiService.HOURLY_PARAMETERS}&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,sunrise,sunset,uv_index_max,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max&timezone=auto&forecast_days=7&wind_speed_unit=mph",
                        httpStatusCode = response.code(),
                        httpMessage = response.message().ifBlank { "OK" },
                        responseTimeMs = duration,
                        rawJsonFallback = rawJson,
                        lastGeocodingQuery = old?.lastGeocodingQuery,
                        rawJsonGeocoding = old?.rawJsonGeocoding,
                        serverResolvedLat = res.latitude,
                        serverResolvedLon = res.longitude,
                        serverResolvedName = "Elevation: ${res.elevation ?: 0.0}m (Timezone: ${res.timezone ?: "UTC"})",
                        timestamp = timestamp
                    )
                }

                val report = mapOpenMeteoResponse(location, res)
                return@withContext Result.success(report)
            } else {
                _debugInfo.update { old ->
                    ApiDebugInfo(
                        location = location,
                        dataSource = WeatherDataSource.OPEN_METEO_METEOROLOGICAL,
                        requestUrl = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}",
                        httpStatusCode = response.code(),
                        httpMessage = response.message(),
                        responseTimeMs = duration,
                        errorDetails = "HTTP ${response.code()}: ${response.message()}",
                        lastGeocodingQuery = old?.lastGeocodingQuery,
                        rawJsonGeocoding = old?.rawJsonGeocoding,
                        timestamp = timestamp
                    )
                }
                return@withContext Result.failure(Exception("Failed to fetch weather data: ${response.message()}"))
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            _debugInfo.update { old ->
                ApiDebugInfo(
                    location = location,
                    dataSource = WeatherDataSource.OPEN_METEO_METEOROLOGICAL,
                    requestUrl = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}",
                    httpStatusCode = 0,
                    httpMessage = "Network Exception",
                    responseTimeMs = duration,
                    errorDetails = e.localizedMessage ?: "Unknown error",
                    lastGeocodingQuery = old?.lastGeocodingQuery,
                    rawJsonGeocoding = old?.rawJsonGeocoding,
                    timestamp = timestamp
                )
            }
            return@withContext Result.failure(e)
        }
    }

    /**
     * Widget-only forecast path. The five-card widget needs only the Spot hourly
     * feed, so this deliberately makes one request and never falls back to the
     * app-selected source or to Open-Meteo.
     */
    suspend fun getSpotWidgetReport(location: LocationItem): Result<WeatherReport> = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("A Met Office Spot API key is required for widget refreshes.")
            )
        }

        try {
            val response = metOfficeApi.getPointHourly(
                latitude = location.latitude,
                longitude = location.longitude,
                apiKey = apiKey,
                clientId = apiKey,
                clientSecret = preferencesManager.getClientSecret().ifBlank { null }
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(
                    mapMetOfficeResponse(
                        location = location,
                        hourly = body,
                        threeHourly = null,
                        daily = null
                    )
                )
            } else {
                Result.failure(
                    IllegalStateException("Met Office Spot widget refresh failed (HTTP ${response.code()}).")
                )
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun searchLocations(query: String): List<LocationItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        // Check matching default UK locations first
        val defaultMatches = LocationItem.DEFAULT_LOCATIONS.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
                    (it.region?.contains(trimmed, ignoreCase = true) == true) ||
                    (it.country?.contains(trimmed, ignoreCase = true) == true)
        }

        try {
            val response = geocodingApi.searchLocations(trimmed, count = 10)
            if (response.isSuccessful && response.body() != null) {
                val results = response.body()?.results ?: emptyList()
                val apiLocations = results.map { it.toLocationItem() }

                val geocodingJson = toPrettyJson(GeocodingSearchResponse::class.java, response.body())
                _debugInfo.update { old ->
                    old?.copy(
                        lastGeocodingQuery = trimmed,
                        rawJsonGeocoding = geocodingJson
                    ) ?: ApiDebugInfo(
                        location = LocationItem.DEFAULT_LOCATIONS.first(),
                        dataSource = WeatherDataSource.OPEN_METEO_METEOROLOGICAL,
                        requestUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$trimmed&count=10&language=en&format=json",
                        httpStatusCode = response.code(),
                        httpMessage = response.message(),
                        responseTimeMs = 0,
                        lastGeocodingQuery = trimmed,
                        rawJsonGeocoding = geocodingJson,
                        timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    )
                }

                // Combine and deduplicate
                val combined = (defaultMatches + apiLocations).distinctBy {
                    "${it.name.lowercase()}_${String.format(Locale.US, "%.2f", it.latitude)}_${String.format(Locale.US, "%.2f", it.longitude)}"
                }
                return@withContext combined
            }
        } catch (_: Exception) {
        }
        return@withContext defaultMatches
    }

    suspend fun testCustomCoordinates(latitude: Double, longitude: Double): CoordinateTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val apiKey = preferencesManager.getApiKey()
        val clientSecret = preferencesManager.getClientSecret()

        if (apiKey.isNotBlank()) {
            try {
                val response = metOfficeApi.getPointHourly(
                    latitude = latitude,
                    longitude = longitude,
                    apiKey = apiKey,
                    clientId = apiKey,
                    clientSecret = clientSecret.ifBlank { null }
                )
                val duration = System.currentTimeMillis() - startTime
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val prettyJson = toPrettyJson(MetOfficeHourlyResponse::class.java, body)
                    val feature = body.features?.firstOrNull()
                    val coords = feature?.geometry?.coordinates
                    val series = feature?.properties?.timeSeries ?: emptyList()
                    val currentTemp = series.firstOrNull()?.screenTemperature

                    val samples = series.take(8).map {
                        "${it.time?.takeLast(9)?.take(5) ?: "--"}: ${it.screenTemperature ?: "--"}°C (code: ${it.significantWeatherCode ?: "--"})"
                    }

                    return@withContext CoordinateTestResult(
                        latitude = latitude,
                        longitude = longitude,
                        requestUrl = "https://data.hub.api.metoffice.gov.uk/sitespecific/v0/point/hourly?latitude=$latitude&longitude=$longitude",
                        httpStatusCode = response.code(),
                        responseTimeMs = duration,
                        rawJson = prettyJson,
                        serverResolvedLon = coords?.getOrNull(0),
                        serverResolvedLat = coords?.getOrNull(1),
                        serverElevation = coords?.getOrNull(2),
                        currentTempCelsius = currentTemp,
                        timeSeriesSample = samples
                    )
                }
            } catch (e: Exception) {
                // Fallthrough to Open-Meteo test
            }
        }

        // Test with Open-Meteo
        try {
            val response = openMeteoApi.getForecast(latitude, longitude)
            val duration = System.currentTimeMillis() - startTime
            if (response.isSuccessful && response.body() != null) {
                val res = response.body()!!
                val prettyJson = toPrettyJson(OpenMeteoResponse::class.java, res)
                val hourly = res.hourly
                val samples = (0 until Math.min(8, hourly?.time?.size ?: 0)).map { i ->
                    val t = hourly?.time?.getOrNull(i)?.takeLast(5) ?: "--"
                    val temp = hourly?.temperature2m?.getOrNull(i) ?: 0.0
                    "$t: $temp°C"
                }

                return@withContext CoordinateTestResult(
                    latitude = latitude,
                    longitude = longitude,
                    requestUrl = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m...",
                    httpStatusCode = response.code(),
                    responseTimeMs = duration,
                    rawJson = prettyJson,
                    serverResolvedLat = res.latitude,
                    serverResolvedLon = res.longitude,
                    serverElevation = res.elevation,
                    currentTempCelsius = res.current?.temperature2m,
                    timeSeriesSample = samples
                )
            } else {
                return@withContext CoordinateTestResult(
                    latitude = latitude,
                    longitude = longitude,
                    requestUrl = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude",
                    httpStatusCode = response.code(),
                    responseTimeMs = duration,
                    rawJson = "HTTP ${response.code()}: ${response.message()}",
                    isError = true,
                    errorMessage = "HTTP ${response.code()} ${response.message()}"
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            return@withContext CoordinateTestResult(
                latitude = latitude,
                longitude = longitude,
                requestUrl = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude",
                httpStatusCode = 0,
                responseTimeMs = duration,
                rawJson = "Exception: ${e.localizedMessage}",
                isError = true,
                errorMessage = e.localizedMessage ?: "Failed to connect"
            )
        }
    }

    suspend fun searchGeocodingRaw(query: String): Pair<String, List<LocationItem>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext Pair("{}", emptyList())
        try {
            val response = geocodingApi.searchLocations(trimmed, count = 10)
            if (response.isSuccessful && response.body() != null) {
                val pretty = toPrettyJson(GeocodingSearchResponse::class.java, response.body())
                val locations = (response.body()?.results ?: emptyList()).map { it.toLocationItem() }
                return@withContext Pair(pretty, locations)
            } else {
                return@withContext Pair("HTTP ${response.code()}: ${response.message()}", emptyList())
            }
        } catch (e: Exception) {
            return@withContext Pair("Error: ${e.localizedMessage}", emptyList())
        }
    }

    /**
     * BPF is intentionally two calls per foreground refresh: one compact percentile
     * request for deterministic weather values and one probability request for PoP.
     * No collection/instance discovery requests are made by the app.
     */
    private suspend fun fetchSpotFallbackReport(
        location: LocationItem,
        apiKey: String,
        clientSecret: String
    ): WeatherReport? {
        if (apiKey.isBlank()) return null

        return try {
            val (hourlyResponse, threeHourlyResponse, dailyResponse) = coroutineScope {
                val hourly = async {
                    metOfficeApi.getPointHourly(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        apiKey = apiKey,
                        clientId = apiKey,
                        clientSecret = clientSecret.ifBlank { null }
                    )
                }
                val threeHourly = async {
                    runCatching {
                        metOfficeApi.getPointThreeHourly(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            apiKey = apiKey,
                            clientId = apiKey,
                            clientSecret = clientSecret.ifBlank { null }
                        )
                    }.getOrNull()
                }
                val daily = async {
                    runCatching {
                        metOfficeApi.getPointDaily(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            apiKey = apiKey,
                            clientId = apiKey,
                            clientSecret = clientSecret.ifBlank { null }
                        )
                    }.getOrNull()
                }
                Triple(hourly.await(), threeHourly.await(), daily.await())
            }

            val hourlyBody = hourlyResponse.body()
            if (!hourlyResponse.isSuccessful || hourlyBody == null) return null
            val feature = hourlyBody.features?.firstOrNull()
            val coordinates = feature?.geometry?.coordinates
            requireResolvedLocationMatchesRequest(
                requested = location,
                resolvedLatitude = coordinates?.getOrNull(1),
                resolvedLongitude = coordinates?.getOrNull(0),
                sourceName = "Met Office Spot",
                maximumDistanceKm = MAX_SPOT_RESOLVED_LOCATION_DISTANCE_KM
            )
            mapMetOfficeResponse(
                location = location,
                hourly = hourlyBody,
                threeHourly = threeHourlyResponse
                    ?.takeIf { it.isSuccessful }
                    ?.body(),
                daily = dailyResponse
                    ?.takeIf { it.isSuccessful }
                    ?.body()
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchBpfWeatherReport(
        location: LocationItem,
        apiKey: String,
        spotApiKey: String,
        spotClientSecret: String,
        startTime: Long,
        timestamp: String
    ): WeatherReport {
        val coords = "POINT(${location.longitude} ${location.latitude})"
        val datetime = bpfDateTimeRange()
        val percentileParameters = listOf(
            "airTemperature1p5m",
            "feelsLikeTemperature1p5m",
            "relativeHumidity1p5m",
            "windSpeed10m",
            "windSpeedOfGust10mMaximumPt01h",
            "windSpeedOfGust10mMaximumPt03h",
            "windFromDirection10mMean",
            "airPressureAtSeaLevel",
            "visibilityInAir1p5m",
            "weatherCodePt01h",
            "weatherCodePt03h",
            "ultravioletIndex"
        ).joinToString(",")

        val (percentileResponse, probabilityResponse) = coroutineScope {
            val percentileDeferred = async {
                metOfficeBpfApi.getUkPercentiles(coords, percentileParameters, datetime, apiKey)
            }
            val probabilityDeferred = async {
                metOfficeBpfApi.getUkProbabilities(
                    coords,
                    listOf(
                        "probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt01h",
                        "probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt03h"
                    ).joinToString(","),
                    datetime,
                    apiKey
                )
            }
            percentileDeferred.await() to probabilityDeferred.await()
        }
        val percentileJson = percentileResponse.body()?.string()
            ?: throw IllegalStateException("BPF percentile request failed (HTTP ${percentileResponse.code()})")
        if (!probabilityResponse.isSuccessful) {
            probabilityResponse.errorBody()?.close()
            throw IllegalStateException("BPF probability request failed (HTTP ${probabilityResponse.code()})")
        }
        val probabilityJson = probabilityResponse.body()?.string()
            ?: throw IllegalStateException("BPF probability request returned an empty payload")

        val coverageAdapter = moshi.adapter(BpfCoverageCollection::class.java)
        val percentileCollection = coverageAdapter.fromJson(percentileJson)
            ?: throw IllegalStateException("BPF returned an unreadable percentile payload")
        val probabilityCollection = coverageAdapter.fromJson(probabilityJson)
            ?: throw IllegalStateException("BPF returned an unreadable probability payload")
        val firstCoverage = percentileCollection.coverages.firstOrNull()
        val axes = firstCoverage?.domain?.axes.orEmpty()
        val resolvedLon = (axes["x"]?.values?.firstOrNull() as? Number)?.toDouble()
        val resolvedLat = (axes["y"]?.values?.firstOrNull() as? Number)?.toDouble()
        val locationId = axes["locationId"]?.values?.firstOrNull()?.toString()

        requireResolvedLocationMatchesRequest(
            requested = location,
            resolvedLatitude = resolvedLat,
            resolvedLongitude = resolvedLon,
            sourceName = "Met Office BPF",
            maximumDistanceKm = MAX_BPF_RESOLVED_LOCATION_DISTANCE_KM
        )

        val temperatures = bpfSeries(percentileCollection, "airTemperature1p5m")
        val sourceTimes = temperatures.keys.toList()
        if (sourceTimes.isEmpty()) throw IllegalStateException("BPF returned no hourly temperature values")
        val expandedTimes = BpfIntervalUtils.expandHourlyTimeline(sourceTimes)

        val feelsLike = bpfSeries(percentileCollection, "feelsLikeTemperature1p5m")
        val humidity = bpfSeries(percentileCollection, "relativeHumidity1p5m")
        val windSpeed = bpfSeries(percentileCollection, "windSpeed10m")
        val hourlyWindGust = bpfIntervalSeries(
            percentileCollection,
            parameter = "windSpeedOfGust10mMaximumPt01h",
            intervalHours = 1
        )
        val threeHourlyWindGust = bpfIntervalSeries(
            percentileCollection,
            parameter = "windSpeedOfGust10mMaximumPt03h",
            intervalHours = 3,
            expandAcrossInterval = true
        )
        val windDirection = bpfSeries(percentileCollection, "windFromDirection10mMean")
        val pressure = bpfSeries(percentileCollection, "airPressureAtSeaLevel")
        val visibility = bpfSeries(percentileCollection, "visibilityInAir1p5m")
        // Period diagnostics carry explicit start/end bounds. The consumer site
        // displays them from the lower bound rather than their nominal end time.
        val hourlyWeatherCode = bpfIntervalSeries(
            percentileCollection,
            parameter = "weatherCodePt01h",
            intervalHours = 1
        )
        val threeHourlyWeatherCodeStarts = bpfIntervalSeries(
            percentileCollection,
            parameter = "weatherCodePt03h",
            intervalHours = 3
        )
        val threeHourlyWeatherCode = bpfIntervalSeries(
            percentileCollection,
            parameter = "weatherCodePt03h",
            intervalHours = 3,
            expandAcrossInterval = true
        )
        val ultravioletIndex = bpfSeries(percentileCollection, "ultravioletIndex")
        // Probability values use the CoverageJSON validity time (`t`). A PT01H
        // value with bounds 15:00-16:00 is therefore displayed at 16:00, not
        // moved back to 15:00. This also makes its label comparable with Spot,
        // whose PoP is centred on its validity time.
        val hourlyPrecipitationProbability = bpfSeries(
            probabilityCollection,
            "probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt01h"
        )
        val threeHourlyPrecipitationProbability = BpfIntervalUtils.expandFromValidityTime(
            series = bpfSeries(
                probabilityCollection,
                "probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt03h"
            ),
            intervalHours = 3
        )
        require(hourlyPrecipitationProbability.isNotEmpty()) {
            "BPF probability payload contains no one-hour precipitation probabilities"
        }
        require(threeHourlyPrecipitationProbability.isNotEmpty()) {
            "BPF probability payload contains no three-hour precipitation probabilities"
        }

        // The Met Office consumer forecast changes to its reduced three-hour view
        // on the sixth local forecast day. Preserve its last midnight hourly slot,
        // then switch at the first PT03H interval beginning on that day.
        val forecastDates = expandedTimes
            .map { TimezoneUtils.getForecastLocalDate(it, location) }
            .distinct()
            .take(7)
        val firstReducedForecastDate = forecastDates.getOrNull(5)
        val reducedForecastStartMillis = firstReducedForecastDate?.let { reducedDate ->
            threeHourlyWeatherCodeStarts.keys
                .filter { TimezoneUtils.getForecastLocalDate(it, location) == reducedDate }
                .mapNotNull(TimezoneUtils::parseIsoToMillis)
                .minOrNull()
        }

        fun isReducedForecastTime(time: String): Boolean = reducedForecastStartMillis?.let { start ->
            (TimezoneUtils.parseIsoToMillis(time) ?: Long.MIN_VALUE) >= start
        } == true

        fun weatherCodeAt(time: String): Double? = if (isReducedForecastTime(time)) {
            threeHourlyWeatherCode[time] ?: hourlyWeatherCode[time]
        } else {
            hourlyWeatherCode[time] ?: threeHourlyWeatherCode[time]
        }

        fun precipitationProbabilityAt(time: String): Double? = if (isReducedForecastTime(time)) {
            threeHourlyPrecipitationProbability[time] ?: hourlyPrecipitationProbability[time]
        } else {
            hourlyPrecipitationProbability[time] ?: threeHourlyPrecipitationProbability[time]
        }

        fun temperatureAt(time: String): Double? = if (isReducedForecastTime(time)) {
            bpfLatestSeriesValue(temperatures, time, maxDifferenceHours = 2)
        } else temperatures[time]

        fun feelsLikeAt(time: String): Double? = if (isReducedForecastTime(time)) {
            bpfLatestSeriesValue(feelsLike, time, maxDifferenceHours = 2)
        } else feelsLike[time]

        fun humidityAt(time: String): Double? = if (isReducedForecastTime(time)) {
            bpfLatestSeriesValue(humidity, time, maxDifferenceHours = 2)
        } else humidity[time]

        fun windSpeedAt(time: String): Double? = if (isReducedForecastTime(time)) {
            bpfLatestSeriesValue(windSpeed, time, maxDifferenceHours = 2)
        } else windSpeed[time]

        fun windDirectionAt(time: String): Double? = if (isReducedForecastTime(time)) {
            bpfLatestSeriesValue(windDirection, time, maxDifferenceHours = 2)
        } else windDirection[time]

        fun pressureAt(time: String): Double? = if (isReducedForecastTime(time)) {
            bpfLatestSeriesValue(pressure, time, maxDifferenceHours = 2)
        } else pressure[time]

        fun uvAt(time: String): Double? = if (isReducedForecastTime(time)) {
            bpfLatestSeriesValue(ultravioletIndex, time, maxDifferenceHours = 2)
        } else {
            bpfNearestSeriesValue(ultravioletIndex, time, maxDifferenceHours = 2)
        }

        fun windGustAt(time: String): Double? = if (isReducedForecastTime(time)) {
            threeHourlyWindGust[time]
                ?: bpfLatestSeriesValue(hourlyWindGust, time, maxDifferenceHours = 2)
        } else {
            hourlyWindGust[time] ?: threeHourlyWindGust[time]
        }

        val expandedCurrentIndex = TimezoneUtils.findCurrentHourItemIndex(
            expandedTimes,
            System.currentTimeMillis(),
            location
        ).coerceIn(0, expandedTimes.lastIndex)
        val currentExpandedTime = expandedTimes[expandedCurrentIndex]
        val hasMissingBpfData = expandedTimes.drop(expandedCurrentIndex).any { time ->
            weatherCodeAt(time)?.roundToInt() !in 0..30 ||
                precipitationProbabilityAt(time) == null ||
                temperatureAt(time) == null ||
                feelsLikeAt(time) == null ||
                humidityAt(time) == null ||
                windSpeedAt(time) == null ||
                windDirectionAt(time) == null ||
                pressureAt(time) == null ||
                uvAt(time) == null ||
                windGustAt(time) == null
        } || visibility[currentExpandedTime] == null || windGustAt(currentExpandedTime) == null
        val spotFallbackReport = if (hasMissingBpfData) {
            fetchSpotFallbackReport(location, spotApiKey, spotClientSecret)
        } else null
        val spotHoursByTime = spotFallbackReport
            ?.hourly
            ?.mapNotNull { item ->
                TimezoneUtils.parseIsoToMillis(item.fullTime)?.let { it to item }
            }
            ?.toMap()
            .orEmpty()
        fun spotAt(time: String): HourlyForecastItem? =
            TimezoneUtils.parseIsoToMillis(time)?.let(spotHoursByTime::get)
        val spotDaysByDate = spotFallbackReport?.daily?.associateBy { it.date }.orEmpty()

        val times = BpfIntervalUtils.trimIncompleteTail(expandedTimes) { time ->
            (precipitationProbabilityAt(time) != null || spotAt(time) != null) &&
                (weatherCodeAt(time)?.roundToInt() in 0..30 || spotAt(time) != null)
        }
        require(times.isNotEmpty()) {
            "BPF contains no complete forecast timestamps"
        }

        val displayWindGust = times.associateWith { time ->
            windGustAt(time)
        }

        val currentIndex = TimezoneUtils.findCurrentHourItemIndex(times, System.currentTimeMillis(), location)
            .coerceIn(0, times.lastIndex)
        val missingProbabilityTimes = times.drop(currentIndex).filter {
            precipitationProbabilityAt(it) == null && spotAt(it) == null
        }
        require(missingProbabilityTimes.isEmpty()) {
            "BPF probability payload is incomplete from ${missingProbabilityTimes.first()}"
        }
        val invalidWeatherCodeTime = times.drop(currentIndex).firstOrNull { time ->
            weatherCodeAt(time)?.roundToInt() !in 0..30 && spotAt(time) == null
        }
        require(invalidWeatherCodeTime == null) {
            "BPF weather-code payload is incomplete or invalid from $invalidWeatherCodeTime"
        }
        var partialSpotFallbackUsed = false
        val hourly = times.mapIndexed { index, time ->
            val isNight = TimezoneUtils.isNightTime(time, location)
            val conditionCode = weatherCodeAt(time)
            val precipitationProbability = precipitationProbabilityAt(time)
            val spotItem = spotAt(time)
            val temperature = temperatureAt(time)
            val feelsLikeTemperature = feelsLikeAt(time)
            val relativeHumidity = humidityAt(time)
            val sustainedWindSpeed = windSpeedAt(time)
            val sustainedWindDirection = windDirectionAt(time)
            val seaLevelPressure = pressureAt(time)
            val uvValue = uvAt(time)
            val bpfWeatherCodeIsValid = conditionCode?.roundToInt() in 0..30
            if (
                spotItem != null &&
                (!bpfWeatherCodeIsValid ||
                    precipitationProbability == null ||
                    temperature == null ||
                    feelsLikeTemperature == null ||
                    relativeHumidity == null ||
                    sustainedWindSpeed == null ||
                    sustainedWindDirection == null ||
                    seaLevelPressure == null ||
                    uvValue == null)
            ) {
                partialSpotFallbackUsed = true
            }
            HourlyForecastItem(
                timeLabel = TimezoneUtils.formatHourLabel(time, location, index == currentIndex),
                fullTime = time,
                date = TimezoneUtils.getForecastLocalDate(time, location),
                temperatureCelsius = temperature?.let(::kelvinToCelsius)
                    ?: spotItem?.temperatureCelsius
                    ?: 0.0,
                feelsLikeCelsius = feelsLikeTemperature?.let(::kelvinToCelsius)
                    ?: temperature?.let(::kelvinToCelsius)
                    ?: spotItem?.feelsLikeCelsius
                    ?: 0.0,
                weatherCode = if (bpfWeatherCodeIsValid) {
                    MetOfficeWeatherCode.fromCode(conditionCode?.toInt(), isNight)
                } else spotItem?.weatherCode ?: MetOfficeWeatherCode.fromCode(null, isNight),
                precipitationChance = precipitationProbability?.let(::probabilityToPercent)
                    ?: spotItem?.precipitationChance
                    ?: 0,
                windSpeedMph = sustainedWindSpeed?.let(::metresPerSecondToMph)
                    ?: spotItem?.windSpeedMph
                    ?: 0.0,
                windDirectionDegrees = sustainedWindDirection?.toInt()
                    ?: spotItem?.windDirectionDegrees
                    ?: 0,
                humidityPercent = relativeHumidity?.let(::relativeHumidityToPercent)
                    ?: spotItem?.humidityPercent
                    ?: 65,
                uvIndex = uvValue
                    ?.roundToInt()
                    ?.coerceAtLeast(0)
                    ?: spotItem?.uvIndex
                    ?: 0,
                pressureHpa = seaLevelPressure?.let(::pascalsToHpa)
                    ?: spotItem?.pressureHpa
                    ?: 1013.25,
                isNow = index == currentIndex
            )
        }

        val daily = hourly
            .groupBy { it.date }
            .toSortedMap()
            .entries
            .take(7)
            .map { (date, items) ->
                val dayItem = items.firstOrNull { !TimezoneUtils.isNightTime(it.fullTime, location) } ?: items.first()
                val nightItem = items.firstOrNull { TimezoneUtils.isNightTime(it.fullTime, location) } ?: dayItem
                val dayInfo = TimezoneUtils.formatDayOfWeek(date, location)
                val sunTimes = TimezoneUtils.calculateSunTimes(date, location)
                val bpfGustsMph = items.mapNotNull { item ->
                    displayWindGust[item.fullTime]?.let(::metresPerSecondToMph)
                }
                val spotDay = spotDaysByDate[date]
                val maxWindGustMph = if (bpfGustsMph.size < items.size && spotDay != null) {
                    partialSpotFallbackUsed = true
                    maxOf(bpfGustsMph.maxOrNull() ?: 0.0, spotDay.maxWindGustMph)
                } else {
                    bpfGustsMph.maxOrNull() ?: 0.0
                }
                DailyForecastItem(
                    date = date,
                    dayOfWeek = dayInfo.first,
                    dateFormatted = dayInfo.second,
                    maxTempCelsius = items.maxOf { it.temperatureCelsius },
                    minTempCelsius = items.minOf { it.temperatureCelsius },
                    dayWeatherCode = dayItem.weatherCode,
                    nightWeatherCode = nightItem.weatherCode,
                    precipitationChance = items.maxOf { it.precipitationChance },
                    uvIndex = items.maxOf { it.uvIndex },
                    maxWindGustMph = maxWindGustMph,
                    sunrise = sunTimes.first,
                    sunset = sunTimes.second
                )
            }

        val currentTime = times[currentIndex]
        val currentDay = daily.firstOrNull { it.date == TimezoneUtils.getForecastLocalDate(currentTime, location) } ?: daily.first()
        val currentIsNight = TimezoneUtils.isNightTime(currentTime, location)
        val spotCurrent = spotFallbackReport?.current?.takeIf {
            TimezoneUtils.parseIsoToMillis(it.timestamp) == TimezoneUtils.parseIsoToMillis(currentTime)
        }
        val currentVisibility = visibility[currentTime]?.toInt()
            ?: spotCurrent?.visibilityMeters?.also { partialSpotFallbackUsed = true }
            ?: 0
        val currentWindGustMph = displayWindGust[currentTime]
            ?.let(::metresPerSecondToMph)
            ?: spotCurrent?.windGustMph?.also { partialSpotFallbackUsed = true }
            ?: hourly[currentIndex].windSpeedMph
        _debugInfo.update { old ->
            ApiDebugInfo(
                location = location,
                dataSource = WeatherDataSource.MET_OFFICE_BPF,
                requestUrl = "${MetOfficeBpfApiService.BASE_URL}collections/uk-spot-percentiles/instances/blended/position",
                httpStatusCode = percentileResponse.code(),
                httpMessage = percentileResponse.message().ifBlank { "OK" },
                responseTimeMs = System.currentTimeMillis() - startTime,
                rawJsonHourly = "CoverageJSON BPF percentile payload: ${percentileCollection.coverages.size} coverages; 50th percentile selected.",
                rawJsonThreeHourly = "CoverageJSON BPF precipitation probability payload: ${probabilityCollection.coverages.size} coverage(s); >0.0 one- and three-hour precipitation-amount thresholds selected.",
                lastGeocodingQuery = old?.lastGeocodingQuery,
                rawJsonGeocoding = old?.rawJsonGeocoding,
                serverResolvedLat = resolvedLat,
                serverResolvedLon = resolvedLon,
                serverResolvedName = locationId?.let { "BPF grid point $it" },
                timestamp = timestamp
            )
        }

        return WeatherReport(
            location = location,
            current = CurrentWeather(
                temperatureCelsius = hourly[currentIndex].temperatureCelsius,
                feelsLikeCelsius = hourly[currentIndex].feelsLikeCelsius,
                weatherCode = hourly[currentIndex].weatherCode,
                maxTempCelsius = currentDay.maxTempCelsius,
                minTempCelsius = currentDay.minTempCelsius,
                humidityPercent = hourly[currentIndex].humidityPercent,
                windSpeedMph = hourly[currentIndex].windSpeedMph,
                windGustMph = currentWindGustMph,
                windDirectionDegrees = hourly[currentIndex].windDirectionDegrees,
                precipitationChance = hourly[currentIndex].precipitationChance,
                uvIndex = hourly[currentIndex].uvIndex,
                visibilityMeters = currentVisibility,
                pressureHpa = hourly[currentIndex].pressureHpa,
                timestamp = currentTime,
                isNight = currentIsNight
            ),
            hourly = hourly,
            daily = daily,
            dataSource = WeatherDataSource.MET_OFFICE_BPF,
            // The blended BPF CoverageJSON response has no model issue/reference
            // timestamp. Leave this empty so the UI accurately shows retrieval time.
            modelRunTime = null,
            partialFallbackSource = WeatherDataSource.MET_OFFICE_DATAHUB
                .takeIf { partialSpotFallbackUsed }
        )
    }

    private fun bpfDateTimeRange(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val start = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.HOUR_OF_DAY, -1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
        return "${formatter.format(start.time)}/${formatter.format(end.time)}"
    }

    /**
     * Site-specific endpoints may snap an out-of-domain coordinate to a distant
     * forecast grid point while still returning HTTP 200. Never relabel or cache
     * that payload as the requested location; throwing here advances the normal
     * BPF -> Spot -> Open-Meteo fallback chain.
     */
    private fun requireResolvedLocationMatchesRequest(
        requested: LocationItem,
        resolvedLatitude: Double?,
        resolvedLongitude: Double?,
        sourceName: String,
        maximumDistanceKm: Double
    ) {
        if (resolvedLatitude == null || resolvedLongitude == null) {
            throw IllegalStateException("$sourceName did not identify its resolved forecast location")
        }
        val distanceKm = distanceKm(
            requested.latitude,
            requested.longitude,
            resolvedLatitude,
            resolvedLongitude
        )
        if (distanceKm > maximumDistanceKm) {
            throw IllegalStateException(
                "$sourceName resolved ${distanceKm.roundToInt()} km from ${requested.name}"
            )
        }
    }

    private fun distanceKm(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {
        val lat1 = Math.toRadians(latitude1)
        val lat2 = Math.toRadians(latitude2)
        val deltaLat = Math.toRadians(latitude2 - latitude1)
        val deltaLon = Math.toRadians(longitude2 - longitude1)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun bpfSeries(collection: BpfCoverageCollection, parameter: String): Map<String, Double> {
        val coverage = collection.coverages.firstOrNull { it.ranges.containsKey(parameter) } ?: return emptyMap()
        return bpfSeries(coverage, parameter)
    }

    private fun bpfIntervalSeries(
        collection: BpfCoverageCollection,
        parameter: String,
        intervalHours: Int,
        expandAcrossInterval: Boolean = false
    ): Map<String, Double> {
        val coverage = collection.coverages.firstOrNull { it.ranges.containsKey(parameter) } ?: return emptyMap()
        val bounds = coverage.domain?.axes?.get("t")?.bounds.orEmpty()
        return BpfIntervalUtils.alignToIntervalStart(
            series = bpfSeries(coverage, parameter),
            intervalHours = intervalHours,
            bounds = bounds,
            expandAcrossInterval = expandAcrossInterval
        )
    }

    private fun bpfSeries(coverage: BpfCoverage, parameter: String): Map<String, Double> {
        val range = coverage.ranges[parameter] ?: return emptyMap()
        val axes = coverage.domain?.axes.orEmpty()
        val times = axes["t"]?.values?.mapNotNull { it?.toString() }.orEmpty()
        if (times.isEmpty()) return emptyMap()

        return times.mapIndexedNotNull { timeIndex, time ->
            bpfValueAt(range, axes.mapValues { it.value.values }, timeIndex)?.let { time to it }
        }.toMap()
    }

    private fun bpfValueAt(
        range: com.example.data.model.BpfRange,
        axes: Map<String, List<Any?>>,
        timeIndex: Int
    ): Double? {
        if (range.axisNames.isEmpty() || range.values.isEmpty()) return null
        var flatIndex = 0
        var stride = 1
        range.axisNames.forEachIndexed { dimension, axisName ->
            val axisValues = axes[axisName].orEmpty()
            val size = range.shape.getOrNull(dimension) ?: axisValues.size
            if (size <= 0) return null
            val coordinate = when {
                axisName == "t" -> timeIndex
                axisName == "percentiles" -> axisValues.indexOfFirst { it?.toString() == "50" }.takeIf { it >= 0 } ?: 0
                axisName.startsWith("probabilityOf") -> axisValues.indexOfFirst { it?.toString() == ">0.0" }.takeIf { it >= 0 } ?: 0
                else -> 0
            }
            // CoverageJSON NdArray values use the first axis as the
            // fastest-changing dimension. For [percentiles, t], for example,
            // all percentiles for hour zero precede all percentiles for hour one.
            flatIndex += coordinate.coerceIn(0, size - 1) * stride
            stride *= size
        }
        return range.values.getOrNull(flatIndex)
    }

    private fun kelvinToCelsius(value: Double): Double = if (value > 150.0) value - 273.15 else value
    private fun metresPerSecondToMph(value: Double?): Double = (value ?: 0.0) * 2.236936
    private fun pascalsToHpa(value: Double?): Double = when {
        value == null -> 1013.25
        value > 2000.0 -> value / 100.0
        else -> value
    }
    private fun probabilityToPercent(value: Double?): Int = when {
        value == null -> 0
        value <= 1.0 -> (value * 100.0).roundToInt().coerceIn(0, 100)
        else -> value.roundToInt().coerceIn(0, 100)
    }
    private fun relativeHumidityToPercent(value: Double?): Int = when {
        value == null -> 65
        value <= 1.0 -> (value * 100.0).roundToInt().coerceIn(0, 100)
        else -> value.roundToInt().coerceIn(0, 100)
    }

    private fun bpfNearestSeriesValue(
        series: Map<String, Double>,
        time: String,
        maxDifferenceHours: Int
    ): Double? {
        series[time]?.let { return it }
        val targetMillis = TimezoneUtils.parseIsoToMillis(time) ?: return null
        val nearest = series.entries.mapNotNull { entry ->
            TimezoneUtils.parseIsoToMillis(entry.key)?.let { millis -> entry to abs(millis - targetMillis) }
        }.minByOrNull { it.second } ?: return null
        return nearest.first.value.takeIf { nearest.second <= maxDifferenceHours * 60L * 60L * 1000L }
    }

    /** Carries a sparse long-horizon value forward, never backwards from a future sample. */
    private fun bpfLatestSeriesValue(
        series: Map<String, Double>,
        time: String,
        maxDifferenceHours: Int
    ): Double? {
        series[time]?.let { return it }
        val targetMillis = TimezoneUtils.parseIsoToMillis(time) ?: return null
        val latest = series.entries.mapNotNull { entry ->
            TimezoneUtils.parseIsoToMillis(entry.key)?.let { millis -> entry to millis }
        }.filter { (_, millis) -> millis <= targetMillis }
            .maxByOrNull { (_, millis) -> millis }
            ?: return null
        return latest.first.value.takeIf {
            targetMillis - latest.second <= maxDifferenceHours * 60L * 60L * 1000L
        }
    }

    private fun mapMetOfficeResponse(
        location: LocationItem,
        hourly: MetOfficeHourlyResponse,
        threeHourly: MetOfficeHourlyResponse?,
        daily: MetOfficeDailyResponse?
    ): WeatherReport {
        val hourlyFeature = hourly.features?.firstOrNull()
        val rawHourlySeries = hourlyFeature?.properties?.timeSeries ?: emptyList()
        val rawThreeHourlySeries = threeHourly?.features?.firstOrNull()?.properties?.timeSeries ?: emptyList()

        // Merge hourly (48h) with three-hourly (up to 7 days) and interpolate so every day has full 24h
        val combinedTimeSeries = combineHourlyAndThreeHourly(rawHourlySeries, rawThreeHourlySeries)
        require(combinedTimeSeries.isNotEmpty()) { "Met Office Spot returned no forecast time series" }

        // Find current time point for the ongoing active hour
        val nowUtcMillis = System.currentTimeMillis()
        val currentItemIndex = TimezoneUtils.findCurrentHourItemIndex(
            combinedTimeSeries.map { it.time },
            nowUtcMillis,
            location
        )
        val currentItem = combinedTimeSeries.getOrNull(currentItemIndex)
            ?: combinedTimeSeries.firstOrNull()
            ?: throw IllegalStateException("Met Office Spot returned no current forecast item")

        val dailySeries = daily?.features?.firstOrNull()?.properties?.timeSeries ?: emptyList()

        // Calculate today's date string in the target location's local time zone
        val tz = TimezoneUtils.getTimeZoneForLocation(location)
        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = tz
        }.format(Date())

        // The Met Office daily API includes 1 past day (yesterday) + 6 future days.
        // We filter to start from Today onwards for the 7-day forecast.
        val validDailySeries = if (dailySeries.any { (it.time?.take(10) ?: "") >= todayDateStr }) {
            dailySeries.filter { (it.time?.take(10) ?: "") >= todayDateStr }
        } else {
            dailySeries
        }

        // Find Today's daily item (not yesterday's)
        val todayDaily = dailySeries.find { (it.time?.take(10) ?: "") == todayDateStr }
            ?: validDailySeries.firstOrNull()

        val isNight = TimezoneUtils.isNightTime(currentItem.time, location)
        val currentWeatherCode = requireNotNull(currentItem.significantWeatherCode) {
            "Met Office Spot current weather code is missing"
        }
        require(currentWeatherCode in 0..30) {
            "Met Office Spot current weather code is invalid: $currentWeatherCode"
        }
        val weatherCode = MetOfficeWeatherCode.fromCode(currentWeatherCode, isNight)

        val currentTemperature = requireNotNull(extractTemp(currentItem)) {
            "Met Office Spot current temperature is missing"
        }
        val currentHumidity = requireNotNull(currentItem.screenRelativeHumidity) {
            "Met Office Spot current humidity is missing"
        }
        val currentWindSpeed = requireNotNull(currentItem.windSpeed10m) {
            "Met Office Spot current wind speed is missing"
        }
        val currentWindDirection = requireNotNull(currentItem.windDirectionFrom10m) {
            "Met Office Spot current wind direction is missing"
        }
        val currentPrecipitationProbability = requireNotNull(currentItem.probOfPrecipitation) {
            "Met Office Spot current precipitation probability is missing"
        }
        val currentVisibility = requireNotNull(currentItem.visibility) {
            "Met Office Spot current visibility is missing"
        }
        val currentPressure = requireNotNull(currentItem.mslp) {
            "Met Office Spot current mean-sea-level pressure is missing"
        }

        // Convert wind speed from m/s to mph (1 m/s = 2.23694 mph)
        val windSpeedMph = currentWindSpeed * 2.23694
        val windGustMph = (currentItem.windGustSpeed10m ?: currentWindSpeed) * 2.23694

        // Pressure mslp in Pa -> hPa
        val pressurePa = currentPressure
        val pressureHpa = if (pressurePa > 50000) pressurePa / 100.0 else pressurePa

        val todayHours = combinedTimeSeries.filter { (it.time?.take(10) ?: "") == todayDateStr }
        val maxTemp = todayDaily?.dayMaxScreenTemperature
            ?: todayHours.mapNotNull { it.screenTemperature }.maxOrNull()
            ?: currentTemperature

        val minTemp = todayDaily?.nightMinScreenTemperature
            ?: todayHours.mapNotNull { it.screenTemperature }.minOrNull()
            ?: (currentTemperature - 4.0)

        val current = CurrentWeather(
            temperatureCelsius = currentTemperature,
            feelsLikeCelsius = extractFeelsLike(currentItem) ?: currentTemperature,
            weatherCode = weatherCode,
            maxTempCelsius = maxTemp,
            minTempCelsius = minTemp,
            humidityPercent = currentHumidity.roundToInt(),
            windSpeedMph = windSpeedMph,
            windGustMph = windGustMph,
            windDirectionDegrees = currentWindDirection,
            precipitationChance = currentPrecipitationProbability,
            uvIndex = currentItem.uvIndex ?: 0,
            visibilityMeters = currentVisibility,
            pressureHpa = pressureHpa,
            timestamp = currentItem.time ?: "",
            isNight = isNight
        )

        // Daily forecast list (7 days starting from Today)
        val dailyList = if (validDailySeries.isNotEmpty()) {
            validDailySeries.take(7).mapIndexed { index, item ->
                val prevItem = if (index > 0) {
                    validDailySeries.getOrNull(index - 1)
                } else {
                    val currentIdx = dailySeries.indexOf(item)
                    if (currentIdx > 0) dailySeries.getOrNull(currentIdx - 1) else null
                }
                mapMetOfficeDailyItem(item, prevItem, location)
            }
        } else {
            // Aggregate from combined hourly/three-hourly
            aggregateDailyFromHourly(combinedTimeSeries, location)
        }

        // Full 7-Day Hourly timeline (seamless fusion of Met Office 48h readings + diurnal modeling for subsequent days)
        val hourlyList = buildFullSevenDayHourlyList(
            location = location,
            combinedTimeSeries = combinedTimeSeries,
            dailyList = dailyList,
            currentItem = currentItem
        )

        // Synchronize daily summary metrics (chance of rain, min/max temp) directly with the true 24-hour hourly series for each calendar day
        val synchronizedDailyList = dailyList.map { day ->
            val dayHourlyItems = hourlyList.filter { it.date == day.date }
            if (dayHourlyItems.isNotEmpty()) {
                val maxPop = dayHourlyItems.maxOfOrNull { it.precipitationChance } ?: day.precipitationChance
                val maxTemp = dayHourlyItems.maxOfOrNull { it.temperatureCelsius } ?: day.maxTempCelsius
                val minTemp = dayHourlyItems.minOfOrNull { it.temperatureCelsius } ?: day.minTempCelsius
                day.copy(
                    precipitationChance = maxPop,
                    maxTempCelsius = maxTemp,
                    minTempCelsius = minTemp
                )
            } else {
                day
            }
        }

        return WeatherReport(
            location = location,
            current = current,
            hourly = hourlyList,
            daily = synchronizedDailyList,
            dataSource = WeatherDataSource.MET_OFFICE_DATAHUB,
            modelRunTime = hourlyFeature?.properties?.modelRunDate
        )
    }

    private fun extractTemp(item: MetOfficeHourlyTimeSeriesItem): Double? {
        return item.screenTemperature
            ?: item.maxScreenAirTemp
            ?: item.minScreenAirTemp
            ?: item.screenApparentTemperature
            ?: item.feelsLikeTemp
            ?: item.feelsLikeTemperature
    }

    private fun extractFeelsLike(item: MetOfficeHourlyTimeSeriesItem): Double? {
        return item.screenApparentTemperature
            ?: item.feelsLikeTemp
            ?: item.feelsLikeTemperature
            ?: extractTemp(item)
    }

    private fun buildFullSevenDayHourlyList(
        location: LocationItem,
        combinedTimeSeries: List<MetOfficeHourlyTimeSeriesItem>,
        dailyList: List<DailyForecastItem>,
        currentItem: MetOfficeHourlyTimeSeriesItem?
    ): List<HourlyForecastItem> {
        val result = mutableListOf<HourlyForecastItem>()
        val existingByDate = combinedTimeSeries.groupBy { TimezoneUtils.getForecastLocalDate(it.time, location) }

        for (day in dailyList) {
            val dateStr = day.date.take(10)
            val rawDaySeries = existingByDate[dateStr] ?: emptyList()

            // Check if we have real temperature variance in daySeries
            val daySeriesTemps = rawDaySeries.mapNotNull { extractTemp(it) }
            val hasValidVariance = daySeriesTemps.size >= 4 && ((daySeriesTemps.maxOrNull() ?: 0.0) - (daySeriesTemps.minOrNull() ?: 0.0) > 0.5)

            if (hasValidVariance) {
                // If we have 3-hourly points (e.g. 8 items) or hourly points, expand/interpolate to full 24 hours if needed
                val full24Series = if (rawDaySeries.size in 4..23) {
                    interpolateThreeHourlySeries(rawDaySeries)
                } else {
                    rawDaySeries
                }

                val dayItems = full24Series.map { item ->
                    val itemIsNight = TimezoneUtils.isNightTime(item.time, location)
                    val code = MetOfficeWeatherCode.fromCode(item.significantWeatherCode, itemIsNight)
                    val t = extractTemp(item) ?: day.maxTempCelsius
                    val fl = extractFeelsLike(item) ?: t
                    val pressure = when {
                        item.mslp != null && item.mslp > 50000 -> item.mslp / 100.0
                        item.mslp != null -> item.mslp
                        else -> 1013.25
                    }
                    HourlyForecastItem(
                        timeLabel = TimezoneUtils.formatHourLabel(item.time, location, false),
                        fullTime = item.time ?: "",
                        date = dateStr,
                        temperatureCelsius = Math.round(t * 10.0) / 10.0,
                        feelsLikeCelsius = Math.round(fl * 10.0) / 10.0,
                        weatherCode = code,
                        precipitationChance = item.probOfPrecipitation ?: day.precipitationChance,
                        windSpeedMph = (item.windSpeed10m ?: 3.0) * 2.23694,
                        windDirectionDegrees = item.windDirectionFrom10m ?: 180,
                        humidityPercent = (item.screenRelativeHumidity ?: 70.0).toInt(),
                        uvIndex = item.uvIndex ?: 0,
                        pressureHpa = Math.round(pressure * 10.0) / 10.0,
                        isNow = false
                    )
                }
                result.addAll(dayItems)
            } else {
                // Synthesize full 24-hour diurnal cycle from Met Office Daily metrics (trough at sunrise, peak at 14:00)
                val syntheticDay = generate24HourForecastForDay(day, dateStr, location)
                result.addAll(syntheticDay)
            }
        }

        if (result.isEmpty()) {
            return combinedTimeSeries.map { item ->
                val itemIsNight = TimezoneUtils.isNightTime(item.time, location)
                val t = extractTemp(item) ?: 15.0
                val fl = extractFeelsLike(item) ?: t
                HourlyForecastItem(
                    timeLabel = TimezoneUtils.formatHourLabel(item.time, location, false),
                    fullTime = item.time ?: "",
                    date = (item.time ?: "").take(10),
                    temperatureCelsius = Math.round(t * 10.0) / 10.0,
                    feelsLikeCelsius = Math.round(fl * 10.0) / 10.0,
                    weatherCode = MetOfficeWeatherCode.fromCode(item.significantWeatherCode, itemIsNight),
                    precipitationChance = item.probOfPrecipitation ?: 0,
                    windSpeedMph = (item.windSpeed10m ?: 3.0) * 2.23694,
                    windDirectionDegrees = item.windDirectionFrom10m ?: 180,
                    humidityPercent = (item.screenRelativeHumidity ?: 70.0).toInt(),
                    uvIndex = item.uvIndex ?: 0,
                    isNow = false
                )
            }
        }

        val nowUtcMillis = System.currentTimeMillis()
        val nowIndex = TimezoneUtils.findCurrentHourItemIndex(
            result.map { it.fullTime },
            nowUtcMillis,
            location
        )
        val nowItem = result.getOrNull(nowIndex)

        return result.map {
            if (it == nowItem) {
                it.copy(isNow = true, timeLabel = "Now")
            } else {
                it.copy(isNow = false, timeLabel = TimezoneUtils.formatHourLabel(it.fullTime, location, false))
            }
        }
    }

    private fun generate24HourForecastForDay(
        day: DailyForecastItem,
        dateStr: String,
        location: LocationItem
    ): List<HourlyForecastItem> {
        val items = mutableListOf<HourlyForecastItem>()
        val minT = day.minTempCelsius
        val maxT = day.maxTempCelsius
        val tRange = (maxT - minT).coerceAtLeast(0.5)

        val tz = TimezoneUtils.getTimeZoneForLocation(location)

        for (h in 0..23) {
            val isNight = h < 6 || h >= 21

            // Diurnal temperature curve:
            // Trough at 05:00, Peak at 14:00 (local solar cycle)
            val tempFraction = when (h) {
                in 0..5 -> {
                    val p = (5 - h) / 5.0
                    p * 0.20
                }
                in 6..14 -> {
                    val p = (h - 5.0) / 9.0
                    Math.sin(p * Math.PI / 2.0).coerceIn(0.0, 1.0)
                }
                else -> {
                    val p = (h - 14.0) / 10.0
                    (Math.cos(p * Math.PI / 2.0) * 0.85 + 0.15).coerceIn(0.0, 1.0)
                }
            }

            val calculatedTemp = minT + (tempFraction * tRange)
            val calculatedFeelsLike = calculatedTemp - if (day.maxWindGustMph > 15) 1.5 else 0.5
            val weatherCode = if (isNight) day.nightWeatherCode else day.dayWeatherCode

            val uv = if (isNight || h < 8 || h > 18) {
                0
            } else {
                val uvFactor = Math.sin(((h - 8.0) / 10.0) * Math.PI).coerceIn(0.0, 1.0)
                (day.uvIndex * uvFactor).toInt().coerceAtLeast(0)
            }

            val pop = if (isNight) {
                (day.precipitationChance * 0.7).toInt()
            } else {
                day.precipitationChance
            }

            val avgWindMph = (day.maxWindGustMph * 0.65).coerceAtLeast(4.0)
            val humidity = (85 - (tempFraction * 35)).toInt().coerceIn(35, 95)

            val amPm = if (h >= 12) "PM" else "AM"
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            val timeLabel = "$h12 $amPm"

            // Construct local calendar time and format UTC ISO representation
            val cal = Calendar.getInstance(tz).apply {
                val parts = dateStr.split("-")
                val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
                val m = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
                val d = parts.getOrNull(2)?.toIntOrNull() ?: 1
                set(y, m, d, h, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(cal.time)

            items.add(
                HourlyForecastItem(
                    timeLabel = timeLabel,
                    fullTime = isoUtc,
                    date = dateStr,
                    temperatureCelsius = Math.round(calculatedTemp * 10.0) / 10.0,
                    feelsLikeCelsius = Math.round(calculatedFeelsLike * 10.0) / 10.0,
                    weatherCode = weatherCode,
                    precipitationChance = pop,
                    windSpeedMph = Math.round(avgWindMph * 10.0) / 10.0,
                    windDirectionDegrees = 225,
                    humidityPercent = humidity,
                    uvIndex = uv,
                    isNow = false
                )
            )
        }

        return items
    }

    private fun combineHourlyAndThreeHourly(
        hourlyList: List<MetOfficeHourlyTimeSeriesItem>,
        threeHourlyList: List<MetOfficeHourlyTimeSeriesItem>?
    ): List<MetOfficeHourlyTimeSeriesItem> {
        if (threeHourlyList.isNullOrEmpty()) return hourlyList
        if (hourlyList.isEmpty()) return interpolateThreeHourlySeries(threeHourlyList)

        val lastHourlyTime = hourlyList.lastOrNull()?.time ?: ""
        val subsequentThreeHourly = threeHourlyList.filter { (it.time ?: "") > lastHourlyTime }
        if (subsequentThreeHourly.isEmpty()) return hourlyList

        val interpolatedSubsequent = interpolateThreeHourlySeries(subsequentThreeHourly)
        return hourlyList + interpolatedSubsequent
    }

    private fun interpolateThreeHourlySeries(
        items: List<MetOfficeHourlyTimeSeriesItem>
    ): List<MetOfficeHourlyTimeSeriesItem> {
        if (items.isEmpty()) return emptyList()
        val result = mutableListOf<MetOfficeHourlyTimeSeriesItem>()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val fallbackSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun parseTime(timeStr: String?): Date? {
            if (timeStr == null) return null
            return try {
                sdf.parse(timeStr)
            } catch (_: Exception) {
                try {
                    fallbackSdf.parse(timeStr)
                } catch (_: Exception) {
                    null
                }
            }
        }

        for (i in 0 until items.size) {
            val current = items[i]
            result.add(current)

            if (i < items.size - 1) {
                val next = items[i + 1]
                val t1 = parseTime(current.time)
                val t2 = parseTime(next.time)

                if (t1 != null && t2 != null) {
                    val diffHours = ((t2.time - t1.time) / (1000 * 60 * 60)).toInt()
                    if (diffHours in 2..4) {
                        for (h in 1 until diffHours) {
                            val fraction = h.toDouble() / diffHours.toDouble()
                            val interpDate = Date(t1.time + (h * 3600 * 1000L))
                            val interpTimeStr = sdf.format(interpDate)

                            val currT = extractTemp(current)
                            val nextT = extractTemp(next)
                            val interpTemp = if (currT != null && nextT != null) {
                                currT + fraction * (nextT - currT)
                            } else currT ?: nextT

                            val currFl = extractFeelsLike(current)
                            val nextFl = extractFeelsLike(next)
                            val interpFeels = if (currFl != null && nextFl != null) {
                                currFl + fraction * (nextFl - currFl)
                            } else currFl ?: nextFl

                            val interpWind = if (current.windSpeed10m != null && next.windSpeed10m != null) {
                                current.windSpeed10m + fraction * (next.windSpeed10m - current.windSpeed10m)
                            } else current.windSpeed10m

                            val interpPop = if (current.probOfPrecipitation != null && next.probOfPrecipitation != null) {
                                (current.probOfPrecipitation + fraction * (next.probOfPrecipitation - current.probOfPrecipitation)).toInt()
                            } else current.probOfPrecipitation

                            val interpItem = MetOfficeHourlyTimeSeriesItem(
                                time = interpTimeStr,
                                screenTemperature = interpTemp,
                                screenApparentTemperature = interpFeels,
                                significantWeatherCode = if (fraction < 0.5) current.significantWeatherCode else next.significantWeatherCode,
                                probOfPrecipitation = interpPop,
                                windSpeed10m = interpWind,
                                windGustSpeed10m = current.windGustSpeed10m,
                                windDirectionFrom10m = current.windDirectionFrom10m,
                                screenRelativeHumidity = current.screenRelativeHumidity,
                                uvIndex = if (isNightTime(interpTimeStr)) 0 else (current.uvIndex ?: 2),
                                visibility = current.visibility,
                                mslp = current.mslp
                            )
                            result.add(interpItem)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun mapMetOfficeDailyItem(
        item: MetOfficeDailyTimeSeriesItem,
        prevItem: MetOfficeDailyTimeSeriesItem? = null,
        location: LocationItem
    ): DailyForecastItem {
        val dateStr = item.time?.take(10) ?: ""
        val dayInfo = TimezoneUtils.formatDayOfWeek(dateStr, location)

        val dayCode = MetOfficeWeatherCode.fromCode(item.daySignificantWeatherCode, isNightFallback = false)
        val nightCode = MetOfficeWeatherCode.fromCode(item.nightSignificantWeatherCode, isNightFallback = true)

        // Day D's daytime pop (06:00-18:00) + early morning pop (00:00-06:00 from prev night)
        // We do NOT attribute the next day's 00:00-06:00 night rain into day D
        val earlyMorningPop = prevItem?.nightProbabilityOfPrecipitation ?: 0
        val dayPop = item.dayProbabilityOfPrecipitation ?: 0
        val pop = Math.max(dayPop, earlyMorningPop)
        val maxGustMph = (item.dayMaxScreenGustSpeed10m ?: item.dayWindSpeed10m ?: 5.0) * 2.23694
        val sunTimes = TimezoneUtils.calculateSunTimes(dateStr, location)

        return DailyForecastItem(
            date = dateStr,
            dayOfWeek = dayInfo.first,
            dateFormatted = dayInfo.second,
            maxTempCelsius = item.dayMaxScreenTemperature ?: 18.0,
            minTempCelsius = item.nightMinScreenTemperature ?: 10.0,
            dayWeatherCode = dayCode,
            nightWeatherCode = nightCode,
            precipitationChance = pop,
            uvIndex = item.middayUvIndex ?: 3,
            maxWindGustMph = maxGustMph,
            sunrise = sunTimes.first,
            sunset = sunTimes.second
        )
    }

    private fun aggregateDailyFromHourly(hourlySeries: List<MetOfficeHourlyTimeSeriesItem>, location: LocationItem): List<DailyForecastItem> {
        val tz = TimezoneUtils.getTimeZoneForLocation(location)
        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = tz
        }.format(Date())

        val groupedByDate = hourlySeries
            .filter { TimezoneUtils.getForecastLocalDate(it.time, location) >= todayDateStr }
            .groupBy { TimezoneUtils.getForecastLocalDate(it.time, location) }

        return groupedByDate.entries.take(7).map { entry ->
            val dateStr = entry.key
            val items = entry.value
            val maxTemp = items.mapNotNull { extractTemp(it) }.maxOrNull() ?: 18.0
            val minTemp = items.mapNotNull { extractTemp(it) }.minOrNull() ?: 10.0
            val maxPop = items.mapNotNull { it.probOfPrecipitation }.maxOrNull() ?: 0
            val maxUv = items.mapNotNull { it.uvIndex }.maxOrNull() ?: 3
            val maxGust = (items.mapNotNull { it.windGustSpeed10m }.maxOrNull() ?: 5.0) * 2.23694

            val dayItem = items.find { !TimezoneUtils.isNightTime(it.time, location) } ?: items.firstOrNull()
            val dayCode = MetOfficeWeatherCode.fromCode(dayItem?.significantWeatherCode, false)

            val dayInfo = TimezoneUtils.formatDayOfWeek(dateStr, location)
            val sunTimes = TimezoneUtils.calculateSunTimes(dateStr, location)

            DailyForecastItem(
                date = dateStr,
                dayOfWeek = dayInfo.first,
                dateFormatted = dayInfo.second,
                maxTempCelsius = maxTemp,
                minTempCelsius = minTemp,
                dayWeatherCode = dayCode,
                nightWeatherCode = MetOfficeWeatherCode.CLEAR_NIGHT,
                precipitationChance = maxPop,
                uvIndex = maxUv,
                maxWindGustMph = maxGust,
                sunrise = sunTimes.first,
                sunset = sunTimes.second
            )
        }
    }

    private fun mapOpenMeteoResponse(location: LocationItem, res: OpenMeteoResponse): WeatherReport {
        val current = requireNotNull(res.current) { "Open-Meteo returned no current conditions" }
        val hourly = requireNotNull(res.hourly) { "Open-Meteo returned no hourly forecast" }
        val daily = requireNotNull(res.daily) { "Open-Meteo returned no daily forecast" }
        val reportLocation = res.timezone
            ?.takeIf { it.isNotBlank() }
            ?.let { location.copy(timezone = it) }
            ?: location

        val hourlyTimes = hourly.time.orEmpty()
        require(hourlyTimes.isNotEmpty()) { "Open-Meteo returned no hourly forecast data" }
        requireOpenMeteoSeriesSize("temperature_2m", hourly.temperature2m?.size, hourlyTimes.size)
        requireOpenMeteoSeriesSize("relative_humidity_2m", hourly.relativeHumidity2m?.size, hourlyTimes.size)
        requireOpenMeteoSeriesSize("apparent_temperature", hourly.apparentTemperature?.size, hourlyTimes.size)
        requireOpenMeteoSeriesSize("precipitation_probability", hourly.precipitationProbability?.size, hourlyTimes.size)
        requireOpenMeteoSeriesSize("weather_code", hourly.weatherCode?.size, hourlyTimes.size)
        requireOpenMeteoSeriesSize("pressure_msl", hourly.pressureMsl?.size, hourlyTimes.size)
        requireOpenMeteoSeriesSize("wind_speed_10m", hourly.windSpeed10m?.size, hourlyTimes.size)
        requireOpenMeteoSeriesSize("wind_direction_10m", hourly.windDirection10m?.size, hourlyTimes.size)
        requireOpenMeteoSeriesSize("uv_index", hourly.uvIndex?.size, hourlyTimes.size)
        requireOpenMeteoSeriesSize("is_day", hourly.isDay?.size, hourlyTimes.size)

        val dailyTimes = daily.time.orEmpty()
        require(dailyTimes.isNotEmpty()) { "Open-Meteo returned no daily forecast data" }
        requireOpenMeteoSeriesSize("daily weather_code", daily.weatherCode?.size, dailyTimes.size)
        requireOpenMeteoSeriesSize("daily temperature_2m_max", daily.temperature2mMax?.size, dailyTimes.size)
        requireOpenMeteoSeriesSize("daily temperature_2m_min", daily.temperature2mMin?.size, dailyTimes.size)
        requireOpenMeteoSeriesSize("daily sunrise", daily.sunrise?.size, dailyTimes.size)
        requireOpenMeteoSeriesSize("daily sunset", daily.sunset?.size, dailyTimes.size)
        requireOpenMeteoSeriesSize("daily uv_index_max", daily.uvIndexMax?.size, dailyTimes.size)
        requireOpenMeteoSeriesSize("daily precipitation_probability_max", daily.precipitationProbabilityMax?.size, dailyTimes.size)
        requireOpenMeteoSeriesSize("daily wind_gusts_10m_max", daily.windGusts10mMax?.size, dailyTimes.size)

        val closestHourIndex = TimezoneUtils.findCurrentHourItemIndex(
            hourlyTimes,
            System.currentTimeMillis(),
            reportLocation
        ).coerceIn(0, hourlyTimes.lastIndex)

        val isNight = if (current.isDay != null) {
            current.isDay == 0
        } else {
            TimezoneUtils.isNightTime(current.time, reportLocation)
        }
        val currentWeatherCode = requireNotNull(current.weatherCode) {
            "Open-Meteo current weather code is missing"
        }
        requireSupportedWmoCode(currentWeatherCode)
        val weatherCode = MetOfficeWeatherCode.fromWmoCode(currentWeatherCode, isNight)

        val currentTemperature = requireNotNull(current.temperature2m) {
            "Open-Meteo current temperature is missing"
        }
        val maxTemp = daily.temperature2mMax?.firstOrNull() ?: currentTemperature
        val minTemp = daily.temperature2mMin?.firstOrNull() ?: currentTemperature
        val currentWindSpeed = requireNotNull(current.windSpeed10m) {
            "Open-Meteo current wind speed is missing"
        }

        val currentData = CurrentWeather(
            temperatureCelsius = currentTemperature,
            feelsLikeCelsius = current.apparentTemperature ?: currentTemperature,
            weatherCode = weatherCode,
            maxTempCelsius = maxTemp,
            minTempCelsius = minTemp,
            humidityPercent = requireNotNull(current.relativeHumidity2m) { "Open-Meteo current humidity is missing" },
            windSpeedMph = currentWindSpeed,
            windGustMph = current.windGusts10m ?: currentWindSpeed,
            windDirectionDegrees = requireNotNull(current.windDirection10m) { "Open-Meteo current wind direction is missing" },
            precipitationChance = current.precipitationProbability
                ?: hourly.precipitationProbability?.getOrNull(closestHourIndex)
                ?: 0,
            uvIndex = (current.uvIndex
                ?: hourly.uvIndex?.getOrNull(closestHourIndex)
                ?: 0.0).roundToInt().coerceAtLeast(0),
            visibilityMeters = requireNotNull(current.visibility) { "Open-Meteo current visibility is missing" }.toInt(),
            pressureHpa = requireNotNull(current.pressureMsl) { "Open-Meteo current sea-level pressure is missing" },
            timestamp = current.time ?: hourlyTimes[closestHourIndex],
            isNight = isNight
        )

        // Map hourly (all available hours across 7 days)
        val hourlyList = mutableListOf<HourlyForecastItem>()
        for (i in hourlyTimes.indices) {
            val time = hourlyTimes[i]
            val itemIsNight = if (hourly.isDay?.getOrNull(i) != null) {
                (hourly.isDay.getOrNull(i) ?: 1) == 0
            } else {
                TimezoneUtils.isNightTime(time, reportLocation)
            }
            val hourlyWeatherCode = hourly.weatherCode.orEmpty()[i]
            requireSupportedWmoCode(hourlyWeatherCode)
            val code = MetOfficeWeatherCode.fromWmoCode(hourlyWeatherCode, itemIsNight)
            val isNowItem = i == closestHourIndex

            hourlyList.add(
                HourlyForecastItem(
                    timeLabel = TimezoneUtils.formatHourLabel(time, reportLocation, isNowItem),
                    fullTime = time,
                    date = time.take(10),
                    temperatureCelsius = hourly.temperature2m.orEmpty()[i],
                    feelsLikeCelsius = hourly.apparentTemperature.orEmpty()[i],
                    weatherCode = code,
                    precipitationChance = hourly.precipitationProbability.orEmpty()[i],
                    windSpeedMph = hourly.windSpeed10m.orEmpty()[i],
                    windDirectionDegrees = hourly.windDirection10m.orEmpty()[i],
                    humidityPercent = hourly.relativeHumidity2m.orEmpty()[i],
                    uvIndex = hourly.uvIndex.orEmpty()[i].roundToInt().coerceAtLeast(0),
                    pressureHpa = hourly.pressureMsl.orEmpty()[i],
                    isNow = isNowItem
                )
            )
        }

        // Map daily (7 days)
        val dailyList = mutableListOf<DailyForecastItem>()
        val dailyCount = dailyTimes.size
        for (i in 0 until Math.min(dailyCount, 7)) {
            val dateStr = dailyTimes[i]
            val dayInfo = TimezoneUtils.formatDayOfWeek(dateStr, reportLocation)
            val dailyWeatherCode = daily.weatherCode.orEmpty()[i]
            requireSupportedWmoCode(dailyWeatherCode)
            val dayCode = MetOfficeWeatherCode.fromWmoCode(dailyWeatherCode, isNight = false)
            val nightCode = MetOfficeWeatherCode.fromWmoCode(dailyWeatherCode, isNight = true)

            dailyList.add(
                DailyForecastItem(
                    date = dateStr,
                    dayOfWeek = dayInfo.first,
                    dateFormatted = dayInfo.second,
                    maxTempCelsius = daily.temperature2mMax.orEmpty()[i],
                    minTempCelsius = daily.temperature2mMin.orEmpty()[i],
                    dayWeatherCode = dayCode,
                    nightWeatherCode = nightCode,
                    precipitationChance = daily.precipitationProbabilityMax.orEmpty()[i],
                    uvIndex = daily.uvIndexMax.orEmpty()[i].roundToInt().coerceAtLeast(0),
                    maxWindGustMph = daily.windGusts10mMax.orEmpty()[i],
                    sunrise = daily.sunrise.orEmpty()[i].takeLast(5),
                    sunset = daily.sunset.orEmpty()[i].takeLast(5)
                )
            )
        }

        // Synchronize daily list with hourly timeline
        val synchronizedDailyList = dailyList.map { day ->
            val dayHourlyItems = hourlyList.filter { it.date == day.date }
            if (dayHourlyItems.isNotEmpty()) {
                val maxPop = dayHourlyItems.maxOfOrNull { it.precipitationChance } ?: day.precipitationChance
                val maxTemp = dayHourlyItems.maxOfOrNull { it.temperatureCelsius } ?: day.maxTempCelsius
                val minTemp = dayHourlyItems.minOfOrNull { it.temperatureCelsius } ?: day.minTempCelsius
                day.copy(
                    precipitationChance = maxPop,
                    maxTempCelsius = maxTemp,
                    minTempCelsius = minTemp
                )
            } else {
                day
            }
        }

        return WeatherReport(
            location = reportLocation,
            current = currentData,
            hourly = hourlyList,
            daily = synchronizedDailyList,
            dataSource = WeatherDataSource.OPEN_METEO_METEOROLOGICAL,
            // Open-Meteo does not provide a model issue timestamp in this response.
            modelRunTime = null
        )
    }

    private fun requireOpenMeteoSeriesSize(name: String, actualSize: Int?, expectedSize: Int) {
        require(actualSize == expectedSize) {
            "Open-Meteo returned incomplete $name data ($actualSize of $expectedSize values)"
        }
    }

    private fun requireSupportedWmoCode(code: Int) {
        require(code in setOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)) {
            "Open-Meteo returned an unsupported WMO weather code: $code"
        }
    }

    private fun parseIsoToMillis(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        val clean = isoString.trim()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(clean)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun isNightTime(isoTime: String?): Boolean {
        if (isoTime == null) return false
        val hour = extractHour(isoTime)
        return hour < 6 || hour >= 21
    }

    private fun extractHour(isoTime: String): Int {
        return try {
            val clean = isoTime.replace("Z", "")
            if (clean.contains("T")) {
                clean.substringAfter("T").take(2).toInt()
            } else {
                12
            }
        } catch (_: Exception) {
            12
        }
    }

    private fun formatHourLabel(isoTime: String?, isNow: Boolean): String {
        if (isNow) return "Now"
        if (isoTime == null) return "--"
        return try {
            val clean = isoTime.replace("Z", "")
            if (clean.contains("T")) {
                val hourStr = clean.substringAfter("T").take(5)
                val hour = hourStr.take(2).toInt()
                val amPm = if (hour >= 12) "PM" else "AM"
                val h12 = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                "$h12 $amPm"
            } else {
                isoTime.takeLast(5)
            }
        } catch (_: Exception) {
            isoTime.takeLast(5)
        }
    }

    private fun formatDayOfWeek(dateStr: String): Pair<String, String> {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = sdf.parse(dateStr) ?: return Pair(dateStr, dateStr)

            val shortDate = SimpleDateFormat("d MMM", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(date)

            val calTarget = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val calToday = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMillis = calTarget.timeInMillis - calToday.timeInMillis
            val diffDays = Math.round(diffMillis.toDouble() / (24.0 * 60 * 60 * 1000)).toInt()

            val dayLabel = when (diffDays) {
                -1 -> "Yesterday"
                0 -> "Today"
                1 -> "Tomorrow"
                else -> SimpleDateFormat("EEEE", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(date)
            }
            Pair(dayLabel, shortDate)
        } catch (_: Exception) {
            Pair(dateStr, dateStr)
        }
    }

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0
        // BPF's UK collection is comparatively dense. Global Spot, however,
        // resolves to one of a much sparser worldwide set of named sites; its
        // legitimate Chattanooga response is about 118 km away in Fayetteville.
        private const val MAX_BPF_RESOLVED_LOCATION_DISTANCE_KM = 100.0
        private const val MAX_SPOT_RESOLVED_LOCATION_DISTANCE_KM = 200.0

        private fun createMetOfficeApi(): MetOfficeApiService {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(MetOfficeApiService.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(MetOfficeApiService::class.java)
        }

        private fun createMetOfficeBpfApi(): MetOfficeBpfApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(MetOfficeBpfApiService.BASE_URL)
                .client(okHttpClient)
                .build()
                .create(MetOfficeBpfApiService::class.java)
        }

        private fun createOpenMeteoApi(): OpenMeteoApiService {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(OpenMeteoApiService.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(OpenMeteoApiService::class.java)
        }

        private fun createGeocodingApi(): GeocodingApiService {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(GeocodingApiService.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GeocodingApiService::class.java)
        }
    }
}
