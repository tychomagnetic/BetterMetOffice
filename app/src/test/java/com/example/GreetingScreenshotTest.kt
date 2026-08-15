package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.CurrentWeather
import com.example.data.model.MetOfficeWeatherCode
import com.example.data.model.TemperatureUnit
import com.example.data.model.WindSpeedUnit
import com.example.ui.components.HeroWeatherCard
import com.example.ui.theme.MetOfficeWeatherTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleCurrent = CurrentWeather(
      temperatureCelsius = 21.0,
      feelsLikeCelsius = 20.0,
      weatherCode = MetOfficeWeatherCode.SUNNY_INTERVALS,
      maxTempCelsius = 23.0,
      minTempCelsius = 14.0,
      humidityPercent = 60,
      windSpeedMph = 8.5,
      windGustMph = 14.0,
      windDirectionDegrees = 220,
      precipitationChance = 10,
      uvIndex = 5,
      visibilityMeters = 25000,
      pressureHpa = 1018.0,
      timestamp = "2026-08-13T12:00:00Z",
      isNight = false
    )

    composeTestRule.setContent {
      MetOfficeWeatherTheme {
        HeroWeatherCard(
          current = sampleCurrent,
          tempUnit = TemperatureUnit.CELSIUS,
          windUnit = WindSpeedUnit.MPH
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

