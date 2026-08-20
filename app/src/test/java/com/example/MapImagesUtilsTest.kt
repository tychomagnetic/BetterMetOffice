package com.example

import com.example.data.model.MapImageFile
import com.example.data.util.MapImagesUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MapImagesUtilsTest {
    @Test
    fun `run boundary changes only at midnight and noon UTC`() {
        assertEquals(
            Instant.parse("2026-08-19T00:00:00Z").toEpochMilli(),
            MapImagesUtils.latestRunBoundaryMillis(Instant.parse("2026-08-19T11:59:59Z").toEpochMilli())
        )
        assertEquals(
            Instant.parse("2026-08-19T12:00:00Z").toEpochMilli(),
            MapImagesUtils.latestRunBoundaryMillis(Instant.parse("2026-08-19T12:00:00Z").toEpochMilli())
        )
        assertEquals(
            Instant.parse("2026-08-20T00:00:00Z").toEpochMilli(),
            MapImagesUtils.latestRunBoundaryMillis(Instant.parse("2026-08-20T00:00:01Z").toEpochMilli())
        )
    }

    @Test
    fun `parser keeps immutable files from newest run and ignores moving aliases`() {
        val files = listOf(
            MapImageFile("total_precipitation_rate_ts24_2026081900", "2026-08-19T00:00:00Z", "00"),
            MapImageFile("total_precipitation_rate_ts24_+12", "2026-08-19T12:00:00Z", "12"),
            MapImageFile("total_precipitation_rate_ts24_2026081912", "2026-08-19T12:00:00Z", "12"),
            MapImageFile("cloud_amount_total_ts168_2026081912", "2026-08-19T12:00:00Z", "12")
        )

        val frames = MapImagesUtils.newestImmutableFrames(files)

        assertEquals(2, frames.size)
        assertTrue(frames.all { it.runDateTime == "2026-08-19T12:00:00Z" })
        assertEquals(24, frames.first { it.layerId == "total_precipitation_rate" }.leadTimeHours)
        assertEquals(168, frames.first { it.layerId == "cloud_amount_total" }.leadTimeHours)
    }
}
