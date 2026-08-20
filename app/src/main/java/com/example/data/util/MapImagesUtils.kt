package com.example.data.util

import com.example.data.model.MapFrame
import com.example.data.model.MapImageFile
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

object MapImagesUtils {
    private val immutableFilePattern = Regex("^(.+)_ts(\\d+)_(\\d{10})$")

    fun latestRunBoundaryMillis(nowMillis: Long): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC)
        val boundaryHour = if (now.hour < 12) 0 else 12
        return now.withHour(boundaryHour).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()
    }

    fun newestImmutableFrames(files: List<MapImageFile>): List<MapFrame> {
        val newestRun = files.mapNotNull { runCatching { Instant.parse(it.runDateTime) }.getOrNull() }.maxOrNull()
            ?: return emptyList()
        return files.mapNotNull { file ->
            if (runCatching { Instant.parse(file.runDateTime) }.getOrNull() != newestRun) return@mapNotNull null
            val match = immutableFilePattern.matchEntire(file.fileId) ?: return@mapNotNull null
            MapFrame(
                fileId = file.fileId,
                layerId = match.groupValues[1],
                leadTimeHours = match.groupValues[2].toInt(),
                runDateTime = file.runDateTime
            )
        }.distinctBy { it.fileId }.sortedWith(compareBy(MapFrame::layerId, MapFrame::leadTimeHours))
    }

    fun forecastTimeMillis(frame: MapFrame): Long =
        Instant.parse(frame.runDateTime).plusSeconds(frame.leadTimeHours * 3600L).toEpochMilli()
}
