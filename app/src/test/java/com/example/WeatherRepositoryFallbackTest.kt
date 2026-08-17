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
}
