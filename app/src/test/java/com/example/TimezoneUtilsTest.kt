package com.example

import com.example.data.model.LocationItem
import com.example.data.util.TimezoneUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimezoneUtilsTest {

    @Test
    fun `test UK London DST and GMT conversion`() {
        val londonLocation = LocationItem(
            id = "london",
            name = "London",
            latitude = 51.5074,
            longitude = -0.1278,
            country = "United Kingdom",
            timezone = "Europe/London"
        )

        // 10:00Z on July 15 (BST - Daylight Saving Time, UTC+1) -> 11 AM
        val summerIso = "2026-07-15T10:00:00Z"
        val summerLabel = TimezoneUtils.formatHourLabel(summerIso, londonLocation, isNow = false)
        assertEquals("11 AM", summerLabel)

        // 10:00Z on January 15 (GMT - Standard Time, UTC+0) -> 10 AM
        val winterIso = "2026-01-15T10:00:00Z"
        val winterLabel = TimezoneUtils.formatHourLabel(winterIso, londonLocation, isNow = false)
        assertEquals("10 AM", winterLabel)
    }

    @Test
    fun `test US Eastern Time EDT and EST conversion`() {
        val newYorkLocation = LocationItem(
            id = "nyc",
            name = "New York",
            latitude = 40.7128,
            longitude = -74.0060,
            country = "United States",
            timezone = "America/New_York"
        )

        // 10:00Z on July 15 (EDT, UTC-4) -> 6 AM
        val summerIso = "2026-07-15T10:00:00Z"
        val summerLabel = TimezoneUtils.formatHourLabel(summerIso, newYorkLocation, isNow = false)
        assertEquals("6 AM", summerLabel)

        // 10:00Z on January 15 (EST, UTC-5) -> 5 AM
        val winterIso = "2026-01-15T10:00:00Z"
        val winterLabel = TimezoneUtils.formatHourLabel(winterIso, newYorkLocation, isNow = false)
        assertEquals("5 AM", winterLabel)
    }

    @Test
    fun `test Tokyo JST conversion`() {
        val tokyoLocation = LocationItem(
            id = "tokyo",
            name = "Tokyo",
            latitude = 35.6762,
            longitude = 139.6503,
            country = "Japan",
            timezone = "Asia/Tokyo"
        )

        // 10:00Z (JST is UTC+9) -> 19:00 -> 7 PM
        val iso = "2026-07-15T10:00:00Z"
        val label = TimezoneUtils.formatHourLabel(iso, tokyoLocation, isNow = false)
        assertEquals("7 PM", label)
    }

    @Test
    fun `test fallback when timezone is empty resolves from coordinates`() {
        val locationWithoutTz = LocationItem(
            id = "sydney",
            name = "Sydney",
            latitude = -33.8688,
            longitude = 151.2093,
            country = "Australia",
            timezone = null
        )

        val tz = TimezoneUtils.getTimeZoneForLocation(locationWithoutTz)
        // Longitude ~151.2 corresponds to approximate offset +10 hours
        val offsetHours = tz.rawOffset / (1000 * 60 * 60)
        assertEquals(10, offsetHours)
    }

    @Test
    fun `test isNightTime in local timezone`() {
        val newYorkLocation = LocationItem(
            id = "nyc",
            name = "New York",
            latitude = 40.7128,
            longitude = -74.0060,
            country = "United States",
            timezone = "America/New_York"
        )

        // 03:00Z in Summer is 23:00 (11 PM) EDT in New York -> Night
        val nightIso = "2026-07-15T03:00:00Z"
        assertTrue(TimezoneUtils.isNightTime(nightIso, newYorkLocation))

        // 18:00Z in Summer is 14:00 (2 PM) EDT in New York -> Day
        val dayIso = "2026-07-15T18:00:00Z"
        assertFalse(TimezoneUtils.isNightTime(dayIso, newYorkLocation))
    }

    @Test
    fun `test Open-Meteo local timestamps are not shifted by DST`() {
        val londonLocation = LocationItem(
            id = "london",
            name = "London",
            latitude = 51.5074,
            longitude = -0.1278,
            country = "United Kingdom",
            timezone = "Europe/London"
        )

        // Open-Meteo timezone=auto returns this as local midnight, not UTC.
        val localMidnight = "2026-08-16T00:00"
        assertEquals("12 AM", TimezoneUtils.formatHourLabel(localMidnight, londonLocation))
        assertEquals(0, TimezoneUtils.getLocalHour(localMidnight, londonLocation))
        assertEquals("2026-08-16", TimezoneUtils.getForecastLocalDate(localMidnight, londonLocation))

        // Met Office timestamps are UTC and cross into the following local day
        // at 23:00Z during British Summer Time.
        assertEquals("2026-08-16", TimezoneUtils.getForecastLocalDate("2026-08-15T23:00:00Z", londonLocation))

        val localTimes = listOf("2026-08-15T13:00", "2026-08-15T14:00", "2026-08-15T15:00")
        val nowAt1430Bst = TimezoneUtils.parseIsoToMillis("2026-08-15T13:30:00Z")!!
        assertEquals(1, TimezoneUtils.findCurrentHourItemIndex(localTimes, nowAt1430Bst, londonLocation))
    }

    @Test
    fun `test findCurrentHourItemIndex at 12_35 BST selects 11_00Z (12 PM) as Now and next item is 1 PM`() {
        val londonLocation = LocationItem(
            id = "london",
            name = "London",
            latitude = 51.5074,
            longitude = -0.1278,
            country = "United Kingdom",
            timezone = "Europe/London"
        )

        // Time series of UTC forecast points:
        // 09:00Z = 10 AM BST
        // 10:00Z = 11 AM BST
        // 11:00Z = 12 PM BST
        // 12:00Z = 1 PM BST
        // 13:00Z = 2 PM BST
        // 14:00Z = 3 PM BST
        val times = listOf(
            "2026-07-15T09:00:00Z",
            "2026-07-15T10:00:00Z",
            "2026-07-15T11:00:00Z",
            "2026-07-15T12:00:00Z",
            "2026-07-15T13:00:00Z",
            "2026-07-15T14:00:00Z"
        )

        // Current test time: 12:35 BST on July 15 (which is 11:35:00 UTC)
        val testCurrentTimeMillis = TimezoneUtils.parseIsoToMillis("2026-07-15T11:35:00Z")!!

        val nowIndex = TimezoneUtils.findCurrentHourItemIndex(times, testCurrentTimeMillis)

        // Index 2 is "2026-07-15T11:00:00Z" (12 PM BST - the ongoing active hour)
        assertEquals(2, nowIndex)
        assertEquals("2026-07-15T11:00:00Z", times[nowIndex])

        // The item at nowIndex is labeled "Now"
        val nowLabel = TimezoneUtils.formatHourLabel(times[nowIndex], londonLocation, isNow = true)
        assertEquals("Now", nowLabel)

        // The NEXT item at nowIndex + 1 (12:00:00Z) is correctly labeled "1 PM" (not 2 PM!)
        val nextHourLabel = TimezoneUtils.formatHourLabel(times[nowIndex + 1], londonLocation, isNow = false)
        assertEquals("1 PM", nextHourLabel)

        // The subsequent item at nowIndex + 2 (13:00:00Z) is labeled "2 PM"
        val nextNextHourLabel = TimezoneUtils.formatHourLabel(times[nowIndex + 2], londonLocation, isNow = false)
        assertEquals("2 PM", nextNextHourLabel)
    }

    @Test
    fun `test findCurrentHourItemIndex throughout hour 12_00 to 12_59 stays on current hour`() {
        val times = listOf(
            "2026-07-15T10:00:00Z", // 11 AM BST
            "2026-07-15T11:00:00Z", // 12 PM BST
            "2026-07-15T12:00:00Z", // 1 PM BST
            "2026-07-15T13:00:00Z"  // 2 PM BST
        )

        // 12:00:00 BST = 11:00:00 UTC -> Index 1 (11:00Z / 12 PM BST)
        val at1200 = TimezoneUtils.parseIsoToMillis("2026-07-15T11:00:00Z")!!
        assertEquals(1, TimezoneUtils.findCurrentHourItemIndex(times, at1200))

        // 12:45:00 BST = 11:45:00 UTC -> Index 1 (11:00Z / 12 PM BST)
        val at1245 = TimezoneUtils.parseIsoToMillis("2026-07-15T11:45:00Z")!!
        assertEquals(1, TimezoneUtils.findCurrentHourItemIndex(times, at1245))

        // 12:59:59 BST = 11:59:59 UTC -> Index 1 (11:00Z / 12 PM BST)
        val at1259 = TimezoneUtils.parseIsoToMillis("2026-07-15T11:59:59Z")!!
        assertEquals(1, TimezoneUtils.findCurrentHourItemIndex(times, at1259))

        // 13:00:00 BST = 12:00:00 UTC -> Rolls over to Index 2 (12:00Z / 1 PM BST)
        val at1300 = TimezoneUtils.parseIsoToMillis("2026-07-15T12:00:00Z")!!
        assertEquals(2, TimezoneUtils.findCurrentHourItemIndex(times, at1300))
    }
}
