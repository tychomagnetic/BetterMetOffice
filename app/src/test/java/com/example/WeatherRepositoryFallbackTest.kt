package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PreferencesManager
import com.example.data.model.ForecastSource
import com.example.data.model.LocationItem
import com.example.data.model.MetOfficeGeometry
import com.example.data.model.MetOfficeHourlyFeature
import com.example.data.model.MetOfficeHourlyProperties
import com.example.data.model.MetOfficeHourlyResponse
import com.example.data.model.MetOfficeHourlyTimeSeriesItem
import com.example.data.model.MetOfficeLocation
import com.example.data.model.OpenMeteoResponse
import com.example.data.model.OpenMeteoCurrent
import com.example.data.model.OpenMeteoDaily
import com.example.data.model.OpenMeteoHourly
import com.example.data.model.WeatherDataSource
import com.example.data.remote.MetOfficeApiService
import com.example.data.remote.MetOfficeBpfApiService
import com.example.data.remote.OpenMeteoApiService
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeatherRepositoryFallbackTest {

    @Test
    fun `isolated BPF hole is filled from Spot without replacing BPF report`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_BPF)
            setBpfApiKey("bpf-test-key")
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi()
        val openApi = RecordingOpenMeteoApi()
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = WeatherCodeHoleBpfApi(),
            openMeteoApi = openApi
        )

        val report = repository.getWeatherReport(LocationItem.DEFAULT_LOCATIONS.first()).getOrThrow()

        assertEquals(WeatherDataSource.MET_OFFICE_BPF, report.dataSource)
        assertEquals(WeatherDataSource.MET_OFFICE_DATAHUB, report.partialFallbackSource)
        assertEquals(7, report.hourly.single().weatherCode.code)
        assertEquals(80, report.hourly.single().precipitationChance)
        assertTrue(spotApi.hourlyRequested)
        assertFalse(openApi.requested)
    }

    @Test
    fun `incomplete BPF probability response falls back to Spot`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_BPF)
            setBpfApiKey("bpf-test-key")
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi()
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = MissingProbabilityBpfApi(),
            openMeteoApi = RecordingOpenMeteoApi()
        )

        val result = repository.getWeatherReport(LocationItem.DEFAULT_LOCATIONS.first())

        assertTrue(result.isSuccess)
        assertEquals(WeatherDataSource.MET_OFFICE_DATAHUB, result.getOrThrow().dataSource)
        assertTrue(spotApi.hourlyRequested)
    }

    @Test
    fun `Open-Meteo uses current probability UV MSL pressure and returned timezone`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.OPEN_METEO)
        }
        val requestedLocation = LocationItem(
            id = "gps_test",
            name = "GPS test",
            latitude = 35.0456,
            longitude = -85.3097,
            timezone = "Europe/London",
            isCurrentLocation = true
        )
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = SuccessfulSpotApi(),
            metOfficeBpfApi = FailingBpfApi(),
            openMeteoApi = SuccessfulOpenMeteoApi()
        )

        val report = repository.getWeatherReport(requestedLocation).getOrThrow()

        assertEquals(73, report.current.precipitationChance)
        assertEquals(3, report.current.uvIndex)
        assertEquals(1019.2, report.current.pressureHpa, 0.001)
        assertEquals("America/New_York", report.location.timezone)
        assertEquals(5, report.hourly.first().precipitationChance)
        assertEquals(4, report.hourly.first().uvIndex)
    }

    @Test
    fun `failed BPF request falls back to Spot before open data`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_BPF)
            setBpfApiKey("bpf-test-key")
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi()
        val openApi = RecordingOpenMeteoApi()
        val location = LocationItem(
            id = "outside_bpf",
            name = "Outside BPF",
            country = "France",
            latitude = 48.8566,
            longitude = 2.3522,
            timezone = "Europe/Paris"
        )
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = FailingBpfApi(),
            openMeteoApi = openApi
        )

        val result = repository.getWeatherReport(location)

        assertTrue(result.isSuccess)
        assertEquals(WeatherDataSource.MET_OFFICE_DATAHUB, result.getOrThrow().dataSource)
        assertEquals(location, result.getOrThrow().location)
        assertTrue(spotApi.hourlyRequested)
        assertFalse(openApi.requested)
    }

    @Test
    fun `distant Met Office grid response is rejected instead of relabelled`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_SPOT)
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi(
            resolvedLatitude = 51.5074,
            resolvedLongitude = -0.1278
        )
        val openApi = RecordingOpenMeteoApi()
        val chattanooga = LocationItem(
            id = "chattanooga_us",
            name = "Chattanooga",
            country = "United States",
            latitude = 35.0456,
            longitude = -85.3097,
            timezone = "America/New_York"
        )
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = FailingBpfApi(),
            openMeteoApi = openApi
        )

        val result = repository.getWeatherReport(chattanooga)

        assertTrue(result.isFailure)
        assertTrue(spotApi.hourlyRequested)
        assertTrue(openApi.requested)
    }

    @Test
    fun `sparse global Spot site within two hundred kilometres is accepted`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_SPOT)
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi(
            resolvedLatitude = 35.06,
            resolvedLongitude = -86.6
        )
        val openApi = RecordingOpenMeteoApi()
        val chattanooga = LocationItem(
            id = "chattanooga_us",
            name = "Chattanooga",
            country = "United States",
            latitude = 35.0456,
            longitude = -85.3097,
            timezone = "America/New_York"
        )
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = FailingBpfApi(),
            openMeteoApi = openApi
        )

        val result = repository.getWeatherReport(chattanooga)

        assertTrue(result.isSuccess)
        assertEquals(WeatherDataSource.MET_OFFICE_DATAHUB, result.getOrThrow().dataSource)
        assertFalse(openApi.requested)
    }

    private class FailingBpfApi : MetOfficeBpfApiService {
        private fun failure(): Response<ResponseBody> =
            Response.error(404, "Outside BPF coverage".toResponseBody())

        override suspend fun getUkPercentiles(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ) = failure()

        override suspend fun getUkProbabilities(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ) = failure()

        override suspend fun getCollections(apiKey: String) = failure()
    }

    private class MissingProbabilityBpfApi : MetOfficeBpfApiService {
        override suspend fun getUkPercentiles(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ): Response<ResponseBody> = Response.success("{}".toResponseBody())

        override suspend fun getUkProbabilities(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ): Response<ResponseBody> = Response.error(503, "probabilities unavailable".toResponseBody())

        override suspend fun getCollections(apiKey: String): Response<ResponseBody> =
            Response.success("{}".toResponseBody())
    }

    private class WeatherCodeHoleBpfApi : MetOfficeBpfApiService {
        private val time = "2026-08-17T12:00:00Z"

        override suspend fun getUkPercentiles(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ): Response<ResponseBody> = Response.success(
            """
            {
              "type":"CoverageCollection",
              "coverages":[
                ${coverage("airTemperature1p5m", 294.15, includeLocation = true)},
                ${coverage("feelsLikeTemperature1p5m", 293.15)},
                ${coverage("relativeHumidity1p5m", 0.61)},
                ${coverage("windSpeed10m", 3.0)},
                ${coverage("windSpeedOfGust10mMaximumPt01h", 5.0)},
                ${coverage("windSpeedOfGust10mMaximumPt03h", 5.0)},
                ${coverage("windFromDirection10mMean", 220.0)},
                ${coverage("airPressureAtSeaLevel", 101500.0)},
                ${coverage("visibilityInAir1p5m", 20000.0)},
                ${coverage("ultravioletIndex", 4.0)}
              ]
            }
            """.trimIndent().toResponseBody()
        )

        override suspend fun getUkProbabilities(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ): Response<ResponseBody> = Response.success(
            """
            {
              "type":"CoverageCollection",
              "coverages":[
                ${probabilityCoverage("probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt01h")},
                ${probabilityCoverage("probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt03h")}
              ]
            }
            """.trimIndent().toResponseBody()
        )

        override suspend fun getCollections(apiKey: String): Response<ResponseBody> =
            Response.success("{}".toResponseBody())

        private fun coverage(parameter: String, value: Double, includeLocation: Boolean = false): String {
            val locationAxes = if (includeLocation) {
                "\"x\":{\"values\":[0.0]},\"y\":{\"values\":[51.5]},\"locationId\":{\"values\":[\"test\"]},"
            } else ""
            return """
                {
                  "type":"Coverage",
                  "domain":{"axes":{$locationAxes"t":{"values":["$time"]}}},
                  "ranges":{"$parameter":{"axisNames":["t"],"shape":[1],"values":[$value]}}
                }
            """.trimIndent()
        }

        private fun probabilityCoverage(parameter: String): String {
            val intervalEnd = if (parameter.endsWith("Pt03h")) {
                "2026-08-17T15:00:00Z"
            } else {
                "2026-08-17T13:00:00Z"
            }
            return """
                {
                  "type":"Coverage",
                  "domain":{"axes":{"t":{"values":["$intervalEnd"],"bounds":["$time","$intervalEnd"]},"${parameter}Values":{"values":[">0.0"]}}},
                  "ranges":{"$parameter":{"axisNames":["${parameter}Values","t"],"shape":[1,1],"values":[0.8]}}
                }
            """.trimIndent()
        }
    }

    private class SuccessfulSpotApi(
        private val resolvedLatitude: Double? = null,
        private val resolvedLongitude: Double? = null
    ) : MetOfficeApiService {
        var hourlyRequested = false

        override suspend fun getPointHourly(
            latitude: Double,
            longitude: Double,
            includeLocationName: Boolean,
            excludeParameterMetadata: Boolean,
            apiKey: String,
            clientId: String?,
            clientSecret: String?
        ): Response<MetOfficeHourlyResponse> {
            hourlyRequested = true
            return Response.success(
                MetOfficeHourlyResponse(
                    features = listOf(
                        MetOfficeHourlyFeature(
                            geometry = MetOfficeGeometry(
                                coordinates = listOf(
                                    resolvedLongitude ?: longitude,
                                    resolvedLatitude ?: latitude
                                )
                            ),
                            properties = MetOfficeHourlyProperties(
                                location = MetOfficeLocation("Outside BPF"),
                                modelRunDate = "2026-08-17T00:00:00Z",
                                timeSeries = listOf(
                                    MetOfficeHourlyTimeSeriesItem(
                                        time = "2026-08-17T12:00:00Z",
                                        screenTemperature = 21.0,
                                        feelsLikeTemperature = 20.0,
                                        screenRelativeHumidity = 60.0,
                                        significantWeatherCode = 7,
                                        probOfPrecipitation = 20,
                                        windSpeed10m = 3.0,
                                        windGustSpeed10m = 5.0,
                                        windDirectionFrom10m = 220,
                                        visibility = 20_000,
                                        mslp = 101_500.0,
                                        uvIndex = 4
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }

        override suspend fun getPointThreeHourly(
            latitude: Double,
            longitude: Double,
            includeLocationName: Boolean,
            excludeParameterMetadata: Boolean,
            apiKey: String,
            clientId: String?,
            clientSecret: String?
        ): Response<MetOfficeHourlyResponse> = Response.error(404, "unused".toResponseBody())

        override suspend fun getPointDaily(
            latitude: Double,
            longitude: Double,
            includeLocationName: Boolean,
            excludeParameterMetadata: Boolean,
            apiKey: String,
            clientId: String?,
            clientSecret: String?
        ) = Response.error<com.example.data.model.MetOfficeDailyResponse>(404, "unused".toResponseBody())
    }

    private class RecordingOpenMeteoApi : OpenMeteoApiService {
        var requested = false

        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            current: String,
            hourly: String,
            daily: String,
            forecastDays: Int,
            timezone: String,
            windSpeedUnit: String
        ): Response<OpenMeteoResponse> {
            requested = true
            return Response.error(500, "should not be called".toResponseBody())
        }
    }

    private class SuccessfulOpenMeteoApi : OpenMeteoApiService {
        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            current: String,
            hourly: String,
            daily: String,
            forecastDays: Int,
            timezone: String,
            windSpeedUnit: String
        ): Response<OpenMeteoResponse> = Response.success(
            OpenMeteoResponse(
                latitude = latitude,
                longitude = longitude,
                timezone = "America/New_York",
                current = OpenMeteoCurrent(
                    time = "2026-08-18T12:15",
                    temperature2m = 24.0,
                    relativeHumidity2m = 60,
                    apparentTemperature = 25.0,
                    precipitationProbability = 73,
                    weatherCode = 2,
                    pressureMsl = 1019.2,
                    windSpeed10m = 8.0,
                    windDirection10m = 210,
                    windGusts10m = 14.0,
                    visibility = 20_000.0,
                    uvIndex = 2.6,
                    isDay = 1
                ),
                hourly = OpenMeteoHourly(
                    time = listOf("2026-08-18T00:00"),
                    temperature2m = listOf(20.0),
                    relativeHumidity2m = listOf(70),
                    apparentTemperature = listOf(20.0),
                    precipitationProbability = listOf(5),
                    weatherCode = listOf(1),
                    pressureMsl = listOf(1018.0),
                    visibility = listOf(20_000.0),
                    windSpeed10m = listOf(5.0),
                    windDirection10m = listOf(180),
                    uvIndex = listOf(3.6),
                    isDay = listOf(0)
                ),
                daily = OpenMeteoDaily(
                    time = listOf("2026-08-18"),
                    weatherCode = listOf(2),
                    temperature2mMax = listOf(28.0),
                    temperature2mMin = listOf(18.0),
                    sunrise = listOf("2026-08-18T06:10"),
                    sunset = listOf("2026-08-18T19:20"),
                    uvIndexMax = listOf(5.6),
                    precipitationProbabilityMax = listOf(73),
                    windGusts10mMax = listOf(18.0)
                )
            )
        )
    }
}
