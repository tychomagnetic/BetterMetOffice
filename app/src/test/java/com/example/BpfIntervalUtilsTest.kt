package com.example

import com.example.data.util.BpfIntervalUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BpfIntervalUtilsTest {

    @Test
    fun `three-hour source timeline expands to hourly display timestamps`() {
        val expanded = BpfIntervalUtils.expandHourlyTimeline(
            listOf(
                "2026-08-22T00:00:00Z",
                "2026-08-22T03:00:00Z",
                "2026-08-22T06:00:00Z"
            )
        )

        assertEquals(7, expanded.size)
        assertEquals("2026-08-22T01:00:00Z", expanded[1])
        assertEquals("2026-08-22T02:00:00Z", expanded[2])
        assertEquals("2026-08-22T06:00:00Z", expanded.last())
    }

    @Test
    fun `PT01H values are aligned to the preceding displayed hour`() {
        val intervalEndValues = linkedMapOf(
            "2026-08-17T00:00:00Z" to 0.36,
            "2026-08-17T01:00:00Z" to 0.64,
            "2026-08-17T02:00:00Z" to 0.79
        )

        val aligned = BpfIntervalUtils.alignToIntervalStart(intervalEndValues, intervalHours = 1)

        assertEquals(0.36, aligned["2026-08-16T23:00:00Z"]!!, 0.0)
        assertEquals(0.64, aligned["2026-08-17T00:00:00Z"]!!, 0.0)
        assertEquals(0.79, aligned["2026-08-17T01:00:00Z"]!!, 0.0)
        assertFalse(aligned.containsKey("2026-08-17T02:00:00Z"))
    }

    @Test
    fun `CoverageJSON bounds align and expand a PT03H diagnostic`() {
        val intervalEndValues = linkedMapOf(
            "2026-08-21T03:00:00Z" to 0.30,
            "2026-08-21T06:00:00Z" to 0.50
        )
        val bounds = listOf(
            "2026-08-21T00:00:00Z", "2026-08-21T03:00:00Z",
            "2026-08-21T03:00:00Z", "2026-08-21T06:00:00Z"
        )

        val expanded = BpfIntervalUtils.alignToIntervalStart(
            series = intervalEndValues,
            intervalHours = 3,
            bounds = bounds,
            expandAcrossInterval = true
        )

        assertEquals(0.30, expanded["2026-08-21T00:00:00Z"]!!, 0.0)
        assertEquals(0.30, expanded["2026-08-21T01:00:00Z"]!!, 0.0)
        assertEquals(0.30, expanded["2026-08-21T02:00:00Z"]!!, 0.0)
        assertEquals(0.50, expanded["2026-08-21T03:00:00Z"]!!, 0.0)
        assertEquals(6, expanded.size)
        assertFalse(expanded.containsKey("2026-08-21T06:00:00Z"))
    }

    @Test
    fun `precipitation probability retains validity time`() {
        val validityValues = linkedMapOf(
            "2026-08-17T00:00:00Z" to 0.36,
            "2026-08-17T01:00:00Z" to 0.64
        )

        val retained = BpfIntervalUtils.expandFromValidityTime(validityValues, intervalHours = 1)

        assertEquals(validityValues, retained)
        assertFalse(retained.containsKey("2026-08-16T23:00:00Z"))
    }

    @Test
    fun `three-hour precipitation probability carries forward from validity time`() {
        val validityValues = linkedMapOf(
            "2026-08-21T03:00:00Z" to 0.30,
            "2026-08-21T06:00:00Z" to 0.50
        )

        val expanded = BpfIntervalUtils.expandFromValidityTime(validityValues, intervalHours = 3)

        assertEquals(0.30, expanded["2026-08-21T03:00:00Z"]!!, 0.0)
        assertEquals(0.30, expanded["2026-08-21T04:00:00Z"]!!, 0.0)
        assertEquals(0.30, expanded["2026-08-21T05:00:00Z"]!!, 0.0)
        assertEquals(0.50, expanded["2026-08-21T06:00:00Z"]!!, 0.0)
        assertFalse(expanded.containsKey("2026-08-21T02:00:00Z"))
    }
}
