package com.example.data.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Helpers for BPF diagnostics whose timestamp marks the end of an interval. */
object BpfIntervalUtils {

    /**
     * BPF parameters can end on different validity boundaries. Remove only the
     * unusable suffix so a single unsupported terminal hour does not discard an
     * otherwise complete forecast; callers still validate gaps within the result.
     */
    fun trimIncompleteTail(
        timestamps: List<String>,
        hasCompleteDisplayData: (String) -> Boolean
    ): List<String> = timestamps.dropLastWhile { !hasCompleteDisplayData(it) }

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

}
