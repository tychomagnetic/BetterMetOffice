package com.example.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class HourlyForecastWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HourlyForecastWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshManager.scheduleAutoRefresh(context.applicationContext)
    }
}
