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
            val intervalMillis = interval.intervalMillis
            val triggerAtMillis = System.currentTimeMillis() + intervalMillis
            alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                triggerAtMillis,
                intervalMillis,
                pendingIntent
            )
            Log.d(TAG, "Widget auto-refresh scheduled every ${interval.label} (trigger at +${interval.hours}h)")
        }
    }

    suspend fun performWidgetRefresh(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = PreferencesManager(context)
                val location = WidgetLocationHelper.getWidgetLocation(context, prefs)
                val repository = WeatherRepository(prefs)
                val result = repository.getWeatherReport(location)
                result.onSuccess { report ->
                    prefs.setCachedWeatherReport(report)
                    prefs.setWidgetPageOffset(0)
                    Log.d(TAG, "Widget background refresh succeeded for ${location.name}")
                }.onFailure { error ->
                    Log.w(TAG, "Widget background refresh failed: ${error.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing background widget refresh", e)
            }
        }
        HourlyForecastWidget.updateAllWidgets(context)
    }
}
