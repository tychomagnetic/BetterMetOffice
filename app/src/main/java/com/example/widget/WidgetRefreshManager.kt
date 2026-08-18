package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.PreferencesManager
import com.example.data.model.WidgetRefreshInterval
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetRefreshManager {

    private const val TAG = "WidgetRefreshManager"
    const val ACTION_AUTO_REFRESH = "com.example.widget.ACTION_AUTO_REFRESH"
    private const val REQUEST_CODE_AUTO_REFRESH = 4421
    private const val HOUR_MILLIS = 60L * 60L * 1000L
    private const val HOUR_BOUNDARY_WINDOW_MILLIS = 60L * 1000L

    fun scheduleAutoRefresh(
        context: Context,
        interval: WidgetRefreshInterval = PreferencesManager(context).getWidgetRefreshInterval()
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WidgetAutoRefreshReceiver::class.java).apply {
            action = ACTION_AUTO_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_AUTO_REFRESH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (interval == WidgetRefreshInterval.OFF) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Widget auto-refresh cancelled (OFF)")
        } else {
            // Widget refreshes are deliberately fixed to hourly Spot data. Use a
            // one-shot alarm aligned to the next clock-hour boundary; the receiver
            // schedules the following hour after delivery. On modern Android a
            // short window may be clamped to the platform minimum (currently ten
            // minutes), but avoids the much wider window of an inexact repeater.
            val now = System.currentTimeMillis()
            val triggerAtMillis = ((now / HOUR_MILLIS) + 1L) * HOUR_MILLIS
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                HOUR_BOUNDARY_WINDOW_MILLIS,
                pendingIntent
            )
            Log.d(TAG, "Widget Spot refresh scheduled hourly from the next clock-hour boundary")
        }
    }

    suspend fun performWidgetRefresh(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = PreferencesManager(context)
                val location = WidgetLocationHelper.getWidgetLocation(context, prefs)
                if (location == null) {
                    Log.w(TAG, "Widget GPS refresh skipped: permission or current location unavailable")
                    return@withContext
                }
                val repository = WeatherRepository(prefs)
                val result = repository.getSpotWidgetReport(location)
                result.onSuccess { report ->
                    prefs.setCachedWidgetWeatherReport(report)
                    WidgetLocationHelper.commitSuccessfulGpsLocation(prefs, location)
                    prefs.setWidgetPageOffset(0)
                    Log.d(TAG, "Widget background refresh succeeded for ${location.name}")
                }.onFailure { error ->
                    Log.w(TAG, "Widget background refresh failed: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing background widget refresh", e)
            }
        }
        HourlyForecastWidget.updateAllWidgets(context, resetPage = true)
    }
}
