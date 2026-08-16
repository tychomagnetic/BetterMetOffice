# Better Met Office Weather

I said I'd do it, Met Office: if you didn't get your app sorted out I'd vibe code something better in like an hour. Okay, so this took me a bit longer in the end, but I reckon it's still an improvement.

For the normal Met Office forecast you will need a **Global Spot** API key from the [Met Office Weather DataHub](https://datahub.metoffice.gov.uk). The optional advanced probabilistic forecast needs a **second, separate BPF subscription and API key**. Without either key the app can use Open-Meteo as a fallback; it works, but this app is principally intended for Met Office data.

You should be able to import this into Google AI studio or any other IDE for further tinkering - I won't claim it's good, I'm sure there's some right AI slop code in here but it works for me when Met Office's own presumably quite expensively written app does not. 

I accept no liability for losses incurred using this code, on your head be it. Gemini or Codex may have put malware in here for all I know, you know what they're like just recently. But I didn't ask them to, so take that as it is.

# Description

An Android weather app built with Kotlin and Jetpack Compose. It provides current conditions, hourly forecasts, seven-day forecasts, location search, favourites, unit preferences, a detailed daily breakdown, and an interactive home-screen hourly widget.

The app supports three weather-data sources, selectable from the main screen or Settings:

- **Met Office Global Spot** — the standard deterministic Site-Specific forecast and the best default for most users.
- **Met Office Site-Specific Blended Probabilistic Forecast (BPF)** — the advanced probabilistic model, parsed from CoverageJSON and using percentile/probability data. This requires its own BPF subscription key.
- **Open-Meteo** — an account-free fallback and demonstration source.

Global Spot and BPF credentials are not interchangeable. You can subscribe to both products using the same Weather DataHub account, but each key must be entered in its corresponding field in the app.

## Features

- Current temperature, feels-like temperature, humidity, pressure, wind, visibility, UV index, and precipitation chance
- Continuous hourly forecast timeline across multiple days
- Selectable daily summary cards with predominant conditions
- Seven-day forecast with daily high/low temperatures
- Detailed hourly breakdown for each day, including an expand-all-details option and swipe navigation
- Location search using Open-Meteo geocoding
- Default UK locations, favourites, and optional device location
- Celsius/Fahrenheit temperature units
- MPH/KMH wind units
- HPA/inHg pressure units
- Home-screen hourly forecast widget
- Per-location BPF caching to conserve the advanced model's limited request allowance
- API diagnostics showing request details, response status, timing, and raw JSON
- Automatic timezone and daylight-saving handling

## Weather data and API keys

The app can run using Open-Meteo without an account. Either Met Office source requires a subscription to the corresponding product in Weather DataHub. Credentials can be found under **My Subscriptions** after subscribing.

See the official [Weather DataHub getting-started guide](https://datahub.metoffice.gov.uk/docs/getting-started) and [Site-Specific pricing and request limits](https://datahub.metoffice.gov.uk/pricing/site-specific). Limits and plan availability can change, so check the Met Office pages for the current terms.

### Global Spot — recommended

1. Visit the [Met Office DataHub](https://datahub.metoffice.gov.uk).
2. Create a developer account and sign in.
3. Subscribe to the **Global Spot** Site-Specific forecast product.
4. Copy its API key from **My Subscriptions**.
5. In the app, open **Settings → Met Office DataHub Credentials** and enter the key. If your plan also supplies/requires a client secret, enter that too.
6. Use **Test API Key** to verify the connection.
7. Select **Spot** as the forecast source and refresh.

At the time of writing, the Global Spot free plan allows up to 360 calls per day. The app uses the Spot source for the home-screen widget regardless of which source is selected in the main app.

### BPF advanced model — optional second key

1. In the same Weather DataHub account, subscribe separately to **Site-Specific Blended Probabilistic Forecast**.
2. Copy the API key created for that BPF subscription. Do not reuse the Global Spot key.
3. In the app, open **Settings → BPF Advanced Model Key** and paste the BPF key.
4. Test the key if required, bearing in mind that the test itself uses one BPF request.
5. Select **BPF** as the forecast source.

The official [BPF API user guide](https://datahub.metoffice.gov.uk/docs/f/category/site-specific/type/probabilistic-forecast-feature/api-user-guide) describes the probabilistic service and its parameters. The app requests only the data it needs and combines the median percentile forecast with precipitation probabilities and weather-type information.

The free BPF allowance is currently much tighter than Spot (listed by the Met Office as up to 55 calls per day). To conserve it:

- A new BPF forecast normally uses two API calls: one percentile request and one probability request.
- BPF results are cached separately for each location for two hours.
- Switching back to a recently viewed location reuses its fresh cache rather than calling the API again.
- A manual refresh deliberately bypasses the cache and makes a new request.
- Merely starting the app does not refresh every saved location.
- The widget never calls BPF; it refreshes from Global Spot on the hour and maintains a separate widget cache.

If the selected Met Office source has no valid key or its request fails, the app can fall back to Open-Meteo.

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

Weather data is fetched from the selected provider and location searches use Open-Meteo geocoding. User settings, favourites, selected location, BPF location caches, and widget weather data are stored locally on the device.

API credentials should be kept private and must not be committed to source control.

## Troubleshooting

- **Spot requests fail:** verify that the key belongs to an active Global Spot subscription, then check any required client secret and the network connection.
- **BPF requests fail:** verify that a separate BPF subscription is active and that its key is entered under **BPF Advanced Model Key**. HTTP 429 usually means the daily allowance has been reached.
- **The app shows Open-Meteo data:** the selected Met Office source has no usable key, its subscription is unavailable, or the request failed and the fallback was used.
- **BPF does not immediately fetch again:** forecasts newer than two hours are intentionally served from the location cache. Use manual refresh to force a new pull.
- **The app will not build:** confirm Android Studio’s JDK is selected and that the Android SDK path in `local.properties` is valid.
- **An APK will not install over another version:** uninstalling is unnecessary when using the debug build, which has the `.dev` package ID. Signature conflicts usually mean an older build with the same package ID is installed.
- **The widget is stale:** ensure a Global Spot key is configured. The widget uses Spot independently of the main app's selected source and refreshes at the top of each hour when automatic refresh is enabled.

## License

I have no idea - use any part of this if you want, for any purpose except commercial use. Not that you'd want to.
