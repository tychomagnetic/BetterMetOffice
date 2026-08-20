package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.data.local.PreferencesManager
import com.example.data.model.HourlyForecastItem
import com.example.data.model.LocationItem
import com.example.data.model.MetOfficeWeatherCode
import com.example.data.model.TemperatureUnit
import com.example.data.model.WeatherIconType
import com.example.data.model.WeatherReport
import com.example.data.repository.WeatherRepository
import com.example.data.util.TimezoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetKeys {
    val PAGE_OFFSET = intPreferencesKey("widget_page_offset")
    val REFRESH_TIMESTAMP = longPreferencesKey("widget_refresh_timestamp")
}

class HourlyForecastWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val glancePrefs = currentState<Preferences>()
            val pageOffset = glancePrefs[WidgetKeys.PAGE_OFFSET] ?: 0

            val appPrefs = PreferencesManager(context)
            val tempUnit = appPrefs.getTemperatureUnit()
            val resolvedLocation = WidgetLocationHelper.getWidgetDisplayLocation(context, appPrefs)
            val targetLocation = resolvedLocation ?: unavailableGpsLocation(context)
            val cachedReport = resolvedLocation?.let { location ->
                appPrefs.getCachedWidgetWeatherReport()?.takeIf { report ->
                    kotlin.math.abs(report.location.latitude - location.latitude) < 0.05 &&
                        kotlin.math.abs(report.location.longitude - location.longitude) < 0.05
                }
            }

            GlanceTheme {
                HourlyForecastWidgetContent(
                    context = context,
                    report = cachedReport,
                    selectedLocation = targetLocation,
                    tempUnit = tempUnit,
                    pageOffset = pageOffset
                )
            }
        }
    }
    companion object {
        suspend fun updateAllWidgets(context: Context, resetPage: Boolean = false) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(HourlyForecastWidget::class.java)
                val widget = HourlyForecastWidget()
                for (glanceId in glanceIds) {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { glancePrefs ->
                        glancePrefs.toMutablePreferences().apply {
                            if (resetPage) this[WidgetKeys.PAGE_OFFSET] = 0
                            this[WidgetKeys.REFRESH_TIMESTAMP] = System.currentTimeMillis()
                        }
                    }
                    widget.update(context, glanceId)
                }
            } catch (_: Exception) {
            }
        }
    }
}

@Composable
fun HourlyForecastWidgetContent(
    context: Context,
    report: WeatherReport?,
    selectedLocation: LocationItem,
    tempUnit: TemperatureUnit,
    pageOffset: Int
) {
    val locationName = report?.location?.name ?: selectedLocation.name
    val allHourly = report?.hourly ?: emptyList()

    // Derive Now from the clock each time Glance renders. The cached isNow flag
    // only describes the hour in which the forecast was downloaded.
    val nowIndex = if (allHourly.isNotEmpty()) {
        TimezoneUtils.findCurrentHourItemIndex(
            fullTimes = allHourly.map { it.fullTime },
            nowMillis = System.currentTimeMillis(),
            location = report?.location ?: selectedLocation
        ).coerceIn(0, allHourly.lastIndex)
    } else {
        0
    }
    val currentItem = allHourly.getOrNull(nowIndex)
    val currentTemp = currentItem?.let { tempUnit.format(it.temperatureCelsius) } ?: "--°"
    val conditionDesc = currentItem?.weatherCode?.description ?: when (selectedLocation.id) {
        "widget_gps_permission_required" -> "Open app to grant access"
        "widget_gps_location_unavailable" -> "Waiting for a location fix"
        else -> "Weather Forecast"
    }
    val maxOffset = (allHourly.size - nowIndex - 5).coerceAtLeast(0)
    val clampedOffset = pageOffset.coerceIn(0, maxOffset)
    val effectiveStartIndex = (nowIndex + clampedOffset).coerceIn(0, (allHourly.size - 1).coerceAtLeast(0))

    val visibleHourly = if (allHourly.isNotEmpty()) {
        allHourly.drop(effectiveStartIndex).take(5).mapIndexed { visibleIndex, item ->
            item.copy(isNow = effectiveStartIndex + visibleIndex == nowIndex)
        }
    } else {
        emptyList()
    }

    val canShiftLeft = clampedOffset > 0
    val canShiftRight = clampedOffset < maxOffset

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(20.dp)
            .background(ImageProvider(R.drawable.bg_widget_card))
            .padding(10.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize()
        ) {
            // Header Bar
            Row(
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Location Pin & Name & Condition (Tap to open app)
                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(end = 4.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_location),
                        contentDescription = "Location",
                        modifier = GlanceModifier.size(16.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = locationName,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFF8FAFC)),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = if (clampedOffset > 0) "+${clampedOffset}h" else "$currentTemp • $conditionDesc",
                        style = TextStyle(
                            color = ColorProvider(if (clampedOffset > 0) Color(0xFFA5B4FC) else Color(0xFF94A3B8)),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }

                // Shift & Refresh Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (allHourly.size > 5) {
                        // "Now" Button to the left of the left arrow to jump back to present time
                        Box(
                            modifier = GlanceModifier
                                .height(30.dp)
                                .cornerRadius(15.dp)
                                .background(ColorProvider(if (clampedOffset > 0) Color(0x33818CF8) else Color(0x15334155)))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .clickable(actionRunCallback<ResetToNowActionCallback>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Now",
                                style = TextStyle(
                                    color = ColorProvider(if (clampedOffset > 0) Color(0xFFA5B4FC) else Color(0xFF64748B)),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(4.dp))

                        // Shift Left
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_chevron_left),
                            contentDescription = "Earlier hours",
                            modifier = GlanceModifier
                                .size(30.dp)
                                .cornerRadius(15.dp)
                                .background(ColorProvider(if (canShiftLeft) Color(0x3364748B) else Color(0x15334155)))
                                .padding(7.dp)
                                .clickable(
                                    actionRunCallback<ShiftHoursActionCallback>(
                                        actionParametersOf(ShiftHoursActionCallback.OFFSET_DELTA_KEY to -3)
                                    )
                                )
                        )

                        Spacer(modifier = GlanceModifier.width(4.dp))

                        // Shift Right
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_chevron_right),
                            contentDescription = "Later hours",
                            modifier = GlanceModifier
                                .size(30.dp)
                                .cornerRadius(15.dp)
                                .background(ColorProvider(if (canShiftRight) Color(0x3364748B) else Color(0x15334155)))
                                .padding(7.dp)
                                .clickable(
                                    actionRunCallback<ShiftHoursActionCallback>(
                                        actionParametersOf(ShiftHoursActionCallback.OFFSET_DELTA_KEY to 3)
                                    )
                                )
                        )

                        Spacer(modifier = GlanceModifier.width(4.dp))
                    }

                    // Refresh Button
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_refresh),
                        contentDescription = "Refresh Forecast",
                        modifier = GlanceModifier
                            .size(30.dp)
                            .cornerRadius(15.dp)
                            .background(ColorProvider(Color(0x33818CF8)))
                            .padding(7.dp)
                            .clickable(actionRunCallback<RefreshWeatherActionCallback>())
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Horizontal Hourly Forecast Strip
            if (visibleHourly.isNotEmpty()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleHourly.forEachIndexed { index, item ->
                        HourlyWidgetCard(
                            item = item,
                            tempUnit = tempUnit,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        if (index < visibleHourly.size - 1) {
                            Spacer(modifier = GlanceModifier.width(4.dp))
                        }
                    }
                }
            } else {
                // Empty / Initial State
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tap to load hourly forecast",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF94A3B8)),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun HourlyWidgetCard(
    item: HourlyForecastItem,
    tempUnit: TemperatureUnit,
    modifier: GlanceModifier = GlanceModifier
) {
    val isNow = item.isNow
    val iconRes = getWidgetWeatherIcon(item.weatherCode)
    val hasPrecip = item.precipitationChance > 0
    val isHighPrecip = item.precipitationChance > 30

    val bgDrawable = if (isNow) R.drawable.bg_widget_hour_tile_active else R.drawable.bg_widget_hour_tile

    Box(
        modifier = modifier
            .fillMaxHeight()
            .cornerRadius(12.dp)
            .background(ImageProvider(bgDrawable))
            .clickable(actionStartActivity<MainActivity>())
            .padding(vertical = 4.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time
            Text(
                text = if (isNow) "Now" else item.timeLabel,
                style = TextStyle(
                    color = ColorProvider(if (isNow) Color(0xFFA5B4FC) else Color(0xFFE2E8F0)),
                    fontSize = 10.5.sp,
                    fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            // Icon
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = item.weatherCode.description,
                modifier = GlanceModifier.size(20.dp)
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            // Precipitation %
            if (hasPrecip) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_water_drop),
                        contentDescription = null,
                        modifier = GlanceModifier.size(8.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(1.dp))
                    Text(
                        text = "${item.precipitationChance}%",
                        style = TextStyle(
                            color = ColorProvider(if (isHighPrecip) Color(0xFF38BDF8) else Color(0xFF94A3B8)),
                            fontSize = 8.5.sp,
                            fontWeight = if (isHighPrecip) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            } else {
                Spacer(modifier = GlanceModifier.height(11.dp))
            }

            Spacer(modifier = GlanceModifier.height(2.dp))

            // Temperature
            Text(
                text = "${tempUnit.convert(item.temperatureCelsius).toInt()}°",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFF8FAFC)),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

fun getWidgetWeatherIcon(weatherCode: MetOfficeWeatherCode): Int {
    return when (weatherCode.iconType) {
        WeatherIconType.CLEAR_DAY -> R.drawable.ic_widget_sunny
        WeatherIconType.CLEAR_NIGHT -> R.drawable.ic_widget_night
        WeatherIconType.PARTLY_CLOUDY_DAY -> R.drawable.ic_widget_partly_cloudy
        WeatherIconType.PARTLY_CLOUDY_NIGHT -> R.drawable.ic_widget_partly_cloudy_night
        WeatherIconType.CLOUDY, WeatherIconType.OVERCAST -> R.drawable.ic_widget_cloudy
        WeatherIconType.MIST, WeatherIconType.FOG -> R.drawable.ic_widget_fog
        WeatherIconType.DRIZZLE -> R.drawable.ic_widget_drizzle
        WeatherIconType.LIGHT_RAIN -> R.drawable.ic_widget_rain
        WeatherIconType.RAIN_SHOWER_DAY -> R.drawable.ic_widget_rain_shower_day
        WeatherIconType.RAIN_SHOWER_NIGHT -> R.drawable.ic_widget_rain_shower_night
        WeatherIconType.HEAVY_RAIN_SHOWER_DAY -> R.drawable.ic_widget_heavy_rain_shower_day
        WeatherIconType.HEAVY_RAIN_SHOWER_NIGHT -> R.drawable.ic_widget_heavy_rain_shower_night
        WeatherIconType.HEAVY_RAIN -> R.drawable.ic_widget_heavy_rain
        WeatherIconType.SLEET, WeatherIconType.SLEET_DAY, WeatherIconType.SLEET_NIGHT, WeatherIconType.HAIL -> R.drawable.ic_widget_sleet
        WeatherIconType.SNOW, WeatherIconType.SNOW_DAY, WeatherIconType.SNOW_NIGHT -> R.drawable.ic_widget_snow
        WeatherIconType.HEAVY_SNOW, WeatherIconType.HEAVY_SNOW_DAY, WeatherIconType.HEAVY_SNOW_NIGHT -> R.drawable.ic_widget_heavy_snow
        WeatherIconType.THUNDERSTORM, WeatherIconType.THUNDERSTORM_DAY, WeatherIconType.THUNDERSTORM_NIGHT -> R.drawable.ic_widget_thunderstorm
    }
}

class RefreshWeatherActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = PreferencesManager(context)
                val location = WidgetLocationHelper.getWidgetLocation(context, prefs)
                    ?: return@withContext
                val repository = WeatherRepository(prefs)
                val result = repository.getSpotWidgetReport(location)
                result.onSuccess { report ->
                    prefs.setCachedWidgetWeatherReport(report)
                    WidgetLocationHelper.commitSuccessfulGpsLocation(prefs, location)
                    prefs.setWidgetPageOffset(0)
                }
            } catch (_: Exception) {
            }
        }
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { glancePrefs ->
            glancePrefs.toMutablePreferences().apply {
                this[WidgetKeys.PAGE_OFFSET] = 0
                this[WidgetKeys.REFRESH_TIMESTAMP] = System.currentTimeMillis()
            }
        }
        HourlyForecastWidget().update(context, glanceId)
    }
}

private fun unavailableGpsLocation(context: Context): LocationItem {
    val permissionGranted = WidgetLocationHelper.hasLocationPermission(context)
    return LocationItem(
        id = if (permissionGranted) {
            "widget_gps_location_unavailable"
        } else {
            "widget_gps_permission_required"
        },
        name = if (permissionGranted) {
            "Current location unavailable"
        } else {
            "Location permission required"
        },
        latitude = 1000.0,
        longitude = 1000.0,
        isCurrentLocation = true
    )
}

class ResetToNowActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = PreferencesManager(context)
        prefs.setWidgetPageOffset(0)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { glancePrefs ->
            glancePrefs.toMutablePreferences().apply {
                this[WidgetKeys.PAGE_OFFSET] = 0
                this[WidgetKeys.REFRESH_TIMESTAMP] = System.currentTimeMillis()
            }
        }
        HourlyForecastWidget().update(context, glanceId)
    }
}

class ShiftHoursActionCallback : ActionCallback {
    companion object {
        val OFFSET_DELTA_KEY = ActionParameters.Key<Int>("offset_delta")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val delta = parameters[OFFSET_DELTA_KEY] ?: 0
        val prefs = PreferencesManager(context)
        val report = prefs.getCachedWidgetWeatherReport()
        val allHourly = report?.hourly ?: emptyList()
        val nowIndex = if (allHourly.isNotEmpty()) {
            TimezoneUtils.findCurrentHourItemIndex(
                fullTimes = allHourly.map { it.fullTime },
                nowMillis = System.currentTimeMillis(),
                location = report?.location
            ).coerceIn(0, allHourly.lastIndex)
        } else {
            0
        }
        val maxOffset = (allHourly.size - nowIndex - 5).coerceAtLeast(0)

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { glancePrefs ->
            val currentOffset = glancePrefs[WidgetKeys.PAGE_OFFSET] ?: 0
            val newOffset = (currentOffset + delta).coerceIn(0, maxOffset)
            prefs.setWidgetPageOffset(newOffset)
            glancePrefs.toMutablePreferences().apply {
                this[WidgetKeys.PAGE_OFFSET] = newOffset
                this[WidgetKeys.REFRESH_TIMESTAMP] = System.currentTimeMillis()
            }
        }
        HourlyForecastWidget().update(context, glanceId)
    }
}
