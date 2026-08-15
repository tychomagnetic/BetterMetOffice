I said I'd do it, Met Office, if you didn't get your app sorted out I'd vibe code something better in like an hour. Okay, so this took me a couple of hours of back and forth in the end, but I reckon it's still an improvement. 

If you want to use this, you'll need an API key for site-specific weather data, free for personal use, from the Met Office - https://datahub.metoffice.gov.uk/profile/subscriptions and input it into this app once you've built it using the Android SDK. Without an API key the app defaults to some horrible open data which doesn't appear to be particularly good quality, but YMMV, it does at least match the day/night cycle so you can use it with that input if you really want.

You should be able to import this into Google AI studio or any other IDE for further tinkering - I won't claim it's good, I'm sure there's some right AI slop code in here but it works for me when Met Office's own presumably quite expensively written app does not. 

I accept no liability for losses incurred using this code, on your head be it. Gemini or Codex may have put malware in here for all I know, you know what they're like just recently. But I didn't ask them to, so take that as it is.

# Better Met Office Weather

An Android weather app built with Kotlin and Jetpack Compose. It provides current conditions, hourly forecasts, seven-day forecasts, location search, favourites, unit preferences, a detailed daily breakdown, and an interactive home-screen hourly widget.

The app supports two weather-data sources:

- **Met Office DataHub** for official UK forecasts
- **Open-Meteo** as a fallback and demonstration source when no Met Office credentials are configured

## Features

- Current temperature, feels-like temperature, humidity, pressure, wind, visibility, UV index, and precipitation chance
- Continuous hourly forecast timeline across multiple days
- Seven-day forecast with daily high/low temperatures
- Detailed hourly breakdown for each day
- Location search using Open-Meteo geocoding
- Default UK locations, favourites, and optional device location
- Celsius/Fahrenheit temperature units
- MPH/KMH wind units
- HPA/inHg pressure units
- Home-screen hourly forecast widget
- API diagnostics showing request details, response status, timing, and raw JSON
- Automatic timezone and daylight-saving handling

## Getting a Met Office API account

The app can run using Open-Meteo without an account, but the Met Office source requires credentials.

1. Visit the [Met Office DataHub](https://datahub.metoffice.gov.uk/).
2. Create a developer account and sign in.
3. Create an application.
4. Subscribe the application to the **Site-Specific** forecast API plan.
5. Copy the application’s API key/client ID.
6. If DataHub provides a client secret, copy that as well.
7. Enter the credentials in the app’s Settings screen.
8. Use **Test API Key** to verify the connection.
9. Enable the Met Office data source and refresh the forecast.

The app falls back to Open-Meteo if the Met Office key is missing or a Met Office request fails.

## Running locally

### Requirements

- Android Studio
- Android SDK
- Java 11-compatible JDK (Android Studio’s bundled JDK is suitable)
- An Android emulator or physical Android device

### Open the project

Open the project directory in Android Studio:

```text
C:\Users\[Your User]\Documents\Codex\Weather
```

Allow Android Studio to complete Gradle synchronisation and install any requested SDK components.

### Environment configuration

Create a `.env` file in the project root if required by the build configuration. Use `.env.example` as a template.

Never commit `.env`, API keys, keystores, or `local.properties`.

### Build and run

From the project directory:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is created at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

You can run the app directly from Android Studio or install the APK using Android Debug Bridge:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Debug builds use a separate development package ID:

```text
com.aistudio.metoffice.wxqaz.dev
```

This allows the development build to coexist with a production installation.

## Permissions

The app may request:

- Internet access for forecast and geocoding requests
- Approximate or precise location access for current-location forecasts

Location access is optional; locations can also be searched or selected manually.

## Project structure

```text
app/src/main/java/com/example/
├── data/
│   ├── local/       Local preferences and cached reports
│   ├── model/       Weather and location models
│   ├── remote/      Retrofit API services
│   ├── repository/  Data-source selection and response mapping
│   └── util/        Timezone and forecast utilities
├── ui/
│   ├── components/  Weather cards, timelines, dialogs, and detail sheets
│   ├── theme/       Compose theme and colours
│   ├── WeatherScreen.kt
│   └── WeatherViewModel.kt
└── widget/          Home-screen hourly forecast widget
```

## Data and privacy

Weather data is fetched from the selected provider and location searches use Open-Meteo geocoding. User settings, favourites, selected location, and cached weather data are stored locally on the device.

API credentials should be kept private and must not be committed to source control.

## Troubleshooting

- **Met Office requests fail:** verify the API key, client ID, client secret, subscription plan, and network connection.
- **The app shows Open-Meteo data:** the Met Office source is disabled, no key is configured, or the Met Office request failed.
- **The app will not build:** confirm Android Studio’s JDK is selected and that the Android SDK path in `local.properties` is valid.
- **An APK will not install over another version:** uninstalling is unnecessary when using the debug build, which has the `.dev` package ID. Signature conflicts usually mean an older build with the same package ID is installed.
- **The widget is stale:** refresh the forecast in the app or use the widget refresh action.

## License

I have no idea - use any part of this if you want, for any purpose except commercial use. Not that you'd want to.
