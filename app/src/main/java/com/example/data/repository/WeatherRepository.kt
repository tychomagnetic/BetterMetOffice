package com.example.data.repository

import com.example.data.local.PreferencesManager
import com.example.data.model.ApiDebugInfo
import com.example.data.model.CoordinateTestResult
import com.example.data.model.CurrentWeather
import com.example.data.model.DailyForecastItem
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
import com.example.data.remote.OpenMeteoApiService
import com.example.data.util.TimezoneUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

sealed class ApiKeyTestResult {
    data class Success(val message: String = "Success! Met Office API verified.") : ApiKeyTestResult()
    data class Error(val message: String) : ApiKeyTestResult()
}

class WeatherRepository(
    private val preferencesManager: PreferencesManager,
    private val metOfficeApi: MetOfficeApiService = createMetOfficeApi(),
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

    suspend fun getWeatherReport(location: LocationItem): Result<WeatherReport> = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.getApiKey()
        val clientSecret = preferencesManager.getClientSecret()
        val isMetOfficePreferred = preferencesManager.isMetOfficePreferred()
        val startTime = System.currentTimeMillis()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        if (isMetOfficePreferred && apiKey.isNotBlank()) {
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
                        requestUrl = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility,is_day&hourly=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation_probability,weather_code,surface_pressure,visibility,wind_speed_10m,wind_direction_10m,uv_index,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,sunrise,sunset,uv_index_max,precipitation_probability_max,wind_gusts_10m_max&timezone=auto&forecast_days=7",
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

        // Find current time point for the ongoing active hour
        val nowUtcMillis = System.currentTimeMillis()
        val currentItemIndex = TimezoneUtils.findCurrentHourItemIndex(
            combinedTimeSeries.map { it.time },
            nowUtcMillis,
            location
        )
        val currentItem = combinedTimeSeries.getOrNull(currentItemIndex)
            ?: combinedTimeSeries.firstOrNull()
            ?: MetOfficeHourlyTimeSeriesItem()

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
        val weatherCode = MetOfficeWeatherCode.fromCode(currentItem.significantWeatherCode, isNight)

        // Convert wind speed from m/s to mph (1 m/s = 2.23694 mph)
        val windSpeedMph = (currentItem.windSpeed10m ?: 3.0) * 2.23694
        val windGustMph = (currentItem.windGustSpeed10m ?: currentItem.windSpeed10m ?: 5.0) * 2.23694

        // Pressure mslp in Pa -> hPa
        val pressurePa = currentItem.mslp ?: 101325.0
        val pressureHpa = if (pressurePa > 50000) pressurePa / 100.0 else pressurePa

        val todayHours = combinedTimeSeries.filter { (it.time?.take(10) ?: "") == todayDateStr }
        val maxTemp = todayDaily?.dayMaxScreenTemperature
            ?: todayHours.mapNotNull { it.screenTemperature }.maxOrNull()
            ?: (currentItem.screenTemperature ?: 15.0)

        val minTemp = todayDaily?.nightMinScreenTemperature
            ?: todayHours.mapNotNull { it.screenTemperature }.minOrNull()
            ?: ((currentItem.screenTemperature ?: 15.0) - 4.0)

        val current = CurrentWeather(
            temperatureCelsius = currentItem.screenTemperature ?: 15.0,
            feelsLikeCelsius = currentItem.screenApparentTemperature ?: currentItem.screenTemperature ?: 15.0,
            weatherCode = weatherCode,
            maxTempCelsius = maxTemp,
            minTempCelsius = minTemp,
            humidityPercent = (currentItem.screenRelativeHumidity ?: 70.0).toInt(),
            windSpeedMph = windSpeedMph,
            windGustMph = windGustMph,
            windDirectionDegrees = currentItem.windDirectionFrom10m ?: 180,
            precipitationChance = currentItem.probOfPrecipitation ?: 0,
            uvIndex = currentItem.uvIndex ?: 2,
            visibilityMeters = currentItem.visibility ?: 15000,
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
        val current = res.current
        val hourly = res.hourly
        val daily = res.daily

        val isNight = if (current?.isDay != null) {
            current.isDay == 0
        } else {
            TimezoneUtils.isNightTime(current?.time, location)
        }
        val weatherCode = MetOfficeWeatherCode.fromWmoCode(current?.weatherCode ?: 0, isNight)

        val maxTemp = daily?.temperature2mMax?.firstOrNull() ?: (current?.temperature2m ?: 15.0)
        val minTemp = daily?.temperature2mMin?.firstOrNull() ?: ((current?.temperature2m ?: 15.0) - 5.0)

        val currentData = CurrentWeather(
            temperatureCelsius = current?.temperature2m ?: 15.0,
            feelsLikeCelsius = current?.apparentTemperature ?: current?.temperature2m ?: 15.0,
            weatherCode = weatherCode,
            maxTempCelsius = maxTemp,
            minTempCelsius = minTemp,
            humidityPercent = current?.relativeHumidity2m ?: 65,
            windSpeedMph = current?.windSpeed10m ?: 5.0,
            windGustMph = current?.windGusts10m ?: current?.windSpeed10m ?: 8.0,
            windDirectionDegrees = current?.windDirection10m ?: 180,
            precipitationChance = hourly?.precipitationProbability?.firstOrNull() ?: 0,
            uvIndex = (hourly?.uvIndex?.firstOrNull() ?: 3.0).toInt(),
            visibilityMeters = (current?.visibility ?: 20000.0).toInt(),
            pressureHpa = current?.surfacePressure ?: 1015.0,
            timestamp = current?.time ?: "",
            isNight = isNight
        )

        // Find index representing the ongoing active hour
        val nowUtcMillis = System.currentTimeMillis()
        val hourlyCount = hourly?.time?.size ?: 0
        val closestHourIndex = TimezoneUtils.findCurrentHourItemIndex(
            hourly?.time ?: emptyList(),
            nowUtcMillis,
            location
        )

        // Map hourly (all available hours across 7 days)
        val hourlyList = mutableListOf<HourlyForecastItem>()
        for (i in 0 until hourlyCount) {
            val time = hourly?.time?.getOrNull(i) ?: ""
            val itemIsNight = if (hourly?.isDay?.getOrNull(i) != null) {
                (hourly.isDay.getOrNull(i) ?: 1) == 0
            } else {
                TimezoneUtils.isNightTime(time, location)
            }
            val code = MetOfficeWeatherCode.fromWmoCode(hourly?.weatherCode?.getOrNull(i) ?: 0, itemIsNight)
            val isNowItem = i == closestHourIndex

            hourlyList.add(
                HourlyForecastItem(
                    timeLabel = TimezoneUtils.formatHourLabel(time, location, isNowItem),
                    fullTime = time,
                    date = time.take(10),
                    temperatureCelsius = hourly?.temperature2m?.getOrNull(i) ?: 15.0,
                    feelsLikeCelsius = hourly?.apparentTemperature?.getOrNull(i) ?: 15.0,
                    weatherCode = code,
                    precipitationChance = hourly?.precipitationProbability?.getOrNull(i) ?: 0,
                    windSpeedMph = hourly?.windSpeed10m?.getOrNull(i) ?: 5.0,
                    windDirectionDegrees = hourly?.windDirection10m?.getOrNull(i) ?: 180,
                    humidityPercent = hourly?.relativeHumidity2m?.getOrNull(i) ?: 65,
                    uvIndex = (hourly?.uvIndex?.getOrNull(i) ?: 0.0).toInt(),
                    pressureHpa = hourly?.surfacePressure?.getOrNull(i) ?: 1013.25,
                    isNow = isNowItem
                )
            )
        }

        // Map daily (7 days)
        val dailyList = mutableListOf<DailyForecastItem>()
        val dailyCount = daily?.time?.size ?: 0
        for (i in 0 until Math.min(dailyCount, 7)) {
            val dateStr = daily?.time?.getOrNull(i) ?: ""
            val dayInfo = TimezoneUtils.formatDayOfWeek(dateStr, location)
            val dayCode = MetOfficeWeatherCode.fromWmoCode(daily?.weatherCode?.getOrNull(i) ?: 0, isNight = false)
            val nightCode = MetOfficeWeatherCode.fromWmoCode(daily?.weatherCode?.getOrNull(i) ?: 0, isNight = true)

            dailyList.add(
                DailyForecastItem(
                    date = dateStr,
                    dayOfWeek = dayInfo.first,
                    dateFormatted = dayInfo.second,
                    maxTempCelsius = daily?.temperature2mMax?.getOrNull(i) ?: 18.0,
                    minTempCelsius = daily?.temperature2mMin?.getOrNull(i) ?: 10.0,
                    dayWeatherCode = dayCode,
                    nightWeatherCode = nightCode,
                    precipitationChance = daily?.precipitationProbabilityMax?.getOrNull(i) ?: 0,
                    uvIndex = (daily?.uvIndexMax?.getOrNull(i) ?: 3.0).toInt(),
                    maxWindGustMph = daily?.windGusts10mMax?.getOrNull(i) ?: 8.0,
                    sunrise = daily?.sunrise?.getOrNull(i)?.takeLast(5),
                    sunset = daily?.sunset?.getOrNull(i)?.takeLast(5)
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
            location = location,
            current = currentData,
            hourly = hourlyList,
            daily = synchronizedDailyList,
            dataSource = WeatherDataSource.OPEN_METEO_METEOROLOGICAL,
            modelRunTime = "Live Model Run"
        )
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
