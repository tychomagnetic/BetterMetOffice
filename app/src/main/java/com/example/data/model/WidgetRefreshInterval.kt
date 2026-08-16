package com.example.data.model

enum class WidgetRefreshInterval(
    val hours: Int,
    val label: String,
    val description: String
) {
    OFF(
        hours = 0,
        label = "Off",
        description = "Automatic background refresh disabled"
    ),
    ONE_HOUR(
        hours = 1,
        label = "1 Hour",
        description = "Refreshes forecast every hour (Default)"
    ),
    TWO_HOURS(
        hours = 2,
        label = "2 Hours",
        description = "Refreshes forecast every 2 hours"
    ),
    FOUR_HOURS(
        hours = 4,
        label = "4 Hours",
        description = "Refreshes forecast every 4 hours"
    );

    val intervalMillis: Long
        get() = hours * 60 * 60 * 1000L
}
