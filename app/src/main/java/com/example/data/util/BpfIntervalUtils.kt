package com.example.data.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Helpers for BPF diagnostics whose timestamp marks the end of an interval. */
object BpfIntervalUtils {

    fun expandHourlyTimeline(timestamps: List<String>): List<String> {
        if (timestamps.size < 2) return timestamps

        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val firstMillis = TimezoneUtils.parseIsoToMillis(timestamps.first()) ?: return timestamps
        val lastMillis = TimezoneUtils.parseIsoToMillis(timestamps.last()) ?: return timestamps
        if (lastMillis <= firstMillis) return timestamps

        return buildList {
            var hourMillis = firstMillis
            while (hourMillis <= lastMillis) {
                add(formatter.format(Date(hourMillis)))
                hourMillis += 60L * 60L * 1000L
            }
        }
    }

    fun alignToIntervalStart(
        series: Map<String, Double>,
        intervalHours: Int,
        bounds: List<Any?> = emptyList(),
        expandAcrossInterval: Boolean = false
    ): Map<String, Double> {
        if (intervalHours <= 0 || series.isEmpty()) return series

        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val offsetMillis = intervalHours * 60L * 60L * 1000L
        val hasCompleteBounds = bounds.size == series.size * 2
        val aligned = linkedMapOf<String, Double>()

        series.entries.forEachIndexed { index, (intervalEnd, value) ->
            val endMillis = if (hasCompleteBounds) {
                TimezoneUtils.parseIsoToMillis(bounds[index * 2 + 1]?.toString().orEmpty())
            } else {
                TimezoneUtils.parseIsoToMillis(intervalEnd)
            } ?: return@forEachIndexed
            val startMillis = if (hasCompleteBounds) {
                TimezoneUtils.parseIsoToMillis(bounds[index * 2]?.toString().orEmpty())
            } else {
                endMillis - offsetMillis
            } ?: return@forEachIndexed

            if (expandAcrossInterval) {
                var hourMillis = startMillis
                while (hourMillis < endMillis) {
                    aligned[formatter.format(Date(hourMillis))] = value
                    hourMillis += 60L * 60L * 1000L
                }
            } else {
                aligned[formatter.format(Date(startMillis))] = value
            }
        }
        return aligned
    }

    /**
     * Retains the API's validity timestamp and optionally carries a sparse
     * period value forward until the next validity time. This is appropriate
     * for precipitation probabilities shown beside Spot values, whose labels
     * are also their validity times.
     */
    fun expandFromValidityTime(
        series: Map<String, Double>,
        intervalHours: Int
    ): Map<String, Double> {
        if (intervalHours <= 1 || series.isEmpty()) return series

        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val expanded = linkedMapOf<String, Double>()
        series.forEach { (validityTime, value) ->
            val validityMillis = TimezoneUtils.parseIsoToMillis(validityTime) ?: return@forEach
            repeat(intervalHours) { hourOffset ->
                expanded[formatter.format(Date(validityMillis + hourOffset * 60L * 60L * 1000L))] = value
            }
        }
        return expanded
    }
}
