package com.example.data.util

import com.example.data.model.LocationItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utility for robust, location-aware timezone and DST conversions.
 * Ensures forecast timestamps (from Met Office DataHub UTC 'Z', Open-Meteo, etc.)
 * are accurately formatted in the target location's local time including any active DST.
 */
object TimezoneUtils {

    /**
     * Resolves the [TimeZone] for a given [LocationItem].
     * 1. If explicit [LocationItem.timezone] is provided and valid, use it.
     * 2. If country is UK / GB or coordinates are within the UK bounding box, use "Europe/London" (auto GMT / BST).
     * 3. Approximates standard world timezones by latitude/longitude.
     */
    fun getTimeZoneForLocation(location: LocationItem): TimeZone {
        // Explicit timezone field if present
        if (!location.timezone.isNullOrBlank()) {
            val tz = TimeZone.getTimeZone(location.timezone)
            if (tz.id == location.timezone || tz.id != "GMT") {
                return tz
            }
        }

        // UK / Great Britain check -> Europe/London (DST = BST UTC+1, Winter = GMT UTC+0)
        val isUk = location.country.equals("United Kingdom", ignoreCase = true) ||
                location.country.equals("UK", ignoreCase = true) ||
                location.country.equals("GB", ignoreCase = true) ||
                (location.latitude in 49.5..61.0 && location.longitude in -11.0..2.5)

        if (isUk) {
            return TimeZone.getTimeZone("Europe/London")
        }

        // Approximate geographic timezone based on longitude & latitude
        return findApproximateTimeZone(location.latitude, location.longitude, location.country)
    }

    /**
     * Approximates TimeZone from latitude/longitude and optional country context.
     */
    fun findApproximateTimeZone(lat: Double, lon: Double, country: String?): TimeZone {
        // USA / Canada longitude spans
        if (country.equals("United States", ignoreCase = true) || country.equals("USA", ignoreCase = true) || country.equals("US", ignoreCase = true)) {
            return when {
                lon < -140.0 -> TimeZone.getTimeZone("America/Anchorage") // Alaska
                lon < -114.0 -> TimeZone.getTimeZone("America/Los_Angeles") // Pacific
                lon < -102.0 -> TimeZone.getTimeZone("America/Denver") // Mountain
                lon < -85.0 -> TimeZone.getTimeZone("America/Chicago") // Central
                else -> TimeZone.getTimeZone("America/New_York") // Eastern
            }
        }

        if (country.equals("Canada", ignoreCase = true) || country.equals("CA", ignoreCase = true)) {
            return when {
                lon < -120.0 -> TimeZone.getTimeZone("America/Vancouver")
                lon < -102.0 -> TimeZone.getTimeZone("America/Edmonton")
                lon < -85.0 -> TimeZone.getTimeZone("America/Winnipeg")
                lon < -67.0 -> TimeZone.getTimeZone("America/Toronto")
                else -> TimeZone.getTimeZone("America/Halifax")
            }
        }

        if (country.equals("Australia", ignoreCase = true) || country.equals("AU", ignoreCase = true)) {
            return when {
                lon < 129.0 -> TimeZone.getTimeZone("Australia/Perth")
                lon < 140.0 -> TimeZone.getTimeZone("Australia/Adelaide")
                else -> TimeZone.getTimeZone("Australia/Sydney")
            }
        }

        // Europe continent check
        if (lat in 35.0..72.0) {
            when {
                lon in -11.0..2.0 -> return TimeZone.getTimeZone("Europe/London") // UK, Ireland, Portugal
                lon in 2.0..28.0 -> return TimeZone.getTimeZone("Europe/Paris") // Central European Time (CET/CEST)
                lon in 28.0..40.0 -> return TimeZone.getTimeZone("Europe/Athens") // Eastern European Time (EET/EEST)
                lon in 40.0..60.0 -> return TimeZone.getTimeZone("Europe/Moscow")
            }
        }

        // Japan / Korea
        if (lat in 24.0..46.0 && lon in 122.0..146.0) {
            return TimeZone.getTimeZone("Asia/Tokyo")
        }

        // China / Hong Kong / Singapore
        if (lat in 1.0..54.0 && lon in 73.0..135.0 && (country?.contains("China", true) == true || country?.contains("Hong Kong", true) == true || country?.contains("Singapore", true) == true)) {
            return TimeZone.getTimeZone("Asia/Shanghai")
        }

        // India
        if (lat in 8.0..37.0 && lon in 68.0..97.0 && country?.contains("India", true) == true) {
            return TimeZone.getTimeZone("Asia/Kolkata")
        }

        // Longitude offset fallback: 1 hour per 15 degrees longitude
        val rawOffsetHours = Math.round(lon / 15.0).toInt()
        val customTzId = when {
            rawOffsetHours == 0 -> "UTC"
            rawOffsetHours > 0 -> "GMT+$rawOffsetHours"
            else -> "GMT$rawOffsetHours"
        }
        return TimeZone.getTimeZone(customTzId)
    }

    /**
     * Parses ISO-8601 timestamp string (e.g. "2026-08-15T10:00:00Z" or "2026-08-15T10:00")
     * into unix epoch milliseconds.
     */
    fun parseIsoToMillis(isoTime: String?): Long? {
        if (isoTime.isNullOrBlank()) return null
        val clean = isoTime.trim()
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mmXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(clean)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Formats an ISO UTC timestamp (or local hour) into a user-facing hour label (e.g. "11 AM", "Now")
     * in the target [location]'s local timezone, accounting for Daylight Saving Time.
     *
     * Example: "2026-08-15T10:00:00Z" in London during summer (BST, UTC+1) -> "11 AM"
     * Example: "2026-08-15T10:00:00Z" in New York (EDT, UTC-4) -> "6 AM"
     */
    fun formatHourLabel(isoTime: String?, location: LocationItem, isNow: Boolean = false): String {
        if (isNow) return "Now"
        if (isoTime == null) return "--"

        // Open-Meteo returns local timestamps without a zone when timezone=auto.
        // They must be displayed as supplied, rather than converted as UTC.
        if (isLocalTimestamp(isoTime)) {
            return formatLocalHourLabel(isoTime)
        }

        val millis = parseIsoToMillis(isoTime)
        if (millis != null) {
            val tz = getTimeZoneForLocation(location)
            val sdf = SimpleDateFormat("h a", Locale.US).apply {
                timeZone = tz
            }
            return sdf.format(Date(millis))
        }

        // Fallback simple parsing
        return try {
            val clean = isoTime.replace("Z", "")
            if (clean.contains("T")) {
                val hour = clean.substringAfter("T").take(2).toInt()
                val amPm = if (hour >= 12) "PM" else "AM"
                val h12 = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                "$h12 $amPm"
            } else {
                isoTime.takeLast(5)
            }
        } catch (_: Exception) {
            isoTime.takeLast(5)
        }
    }

    /**
     * Returns the 24-hour hour of day (0..23) in the target [location]'s local time zone.
     */
    fun getLocalHour(isoTime: String?, location: LocationItem): Int {
        if (isoTime == null) return 12
        if (isLocalTimestamp(isoTime)) {
            return hourFromTimestamp(isoTime) ?: 12
        }
        val millis = parseIsoToMillis(isoTime)
        if (millis != null) {
            val tz = getTimeZoneForLocation(location)
            val cal = Calendar.getInstance(tz).apply {
                timeInMillis = millis
            }
            return cal.get(Calendar.HOUR_OF_DAY)
        }
        return 12
    }

    /**
     * Returns the calendar date represented by a forecast timestamp at [location].
     * Zone-less provider timestamps are already local; UTC/offset timestamps are
     * converted to the location's timezone first.
     */
    fun getForecastLocalDate(isoTime: String?, location: LocationItem): String {
        if (isoTime.isNullOrBlank()) return ""
        if (isLocalTimestamp(isoTime)) return isoTime.take(10)

        val millis = parseIsoToMillis(isoTime) ?: return isoTime.take(10)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = getTimeZoneForLocation(location)
        }.format(Date(millis))
    }

    /**
     * Determines whether the given time represents nighttime in the target [location]'s local time.
     */
    fun isNightTime(isoTime: String?, location: LocationItem): Boolean {
        val localHour = getLocalHour(isoTime, location)
        return localHour < 6 || localHour >= 21
    }

    /**
     * Formats date string and day-of-week relative to target [location]'s local calendar.
     */
    fun formatDayOfWeek(
        dateStr: String,
        location: LocationItem,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<String, String> {
        return try {
            val tz = getTimeZoneForLocation(location)
            val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = tz
            }
            val date = sdfInput.parse(dateStr) ?: return Pair(dateStr, dateStr)

            val shortDate = SimpleDateFormat("d MMM", Locale.US).apply {
                timeZone = tz
            }.format(date)

            val calTarget = Calendar.getInstance(tz).apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val calToday = Calendar.getInstance(tz).apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMillis = calTarget.timeInMillis - calToday.timeInMillis
            val diffDays = Math.round(diffMillis.toDouble() / (24.0 * 60 * 60 * 1000)).toInt()

            val dayLabel = when (diffDays) {
                -1 -> "Yesterday"
                0 -> "Today"
                1 -> "Tomorrow"
                else -> SimpleDateFormat("EEEE", Locale.US).apply {
                    timeZone = tz
                }.format(date)
            }
            Pair(dayLabel, shortDate)
        } catch (_: Exception) {
            Pair(dateStr, dateStr)
        }
    }

    /**
     * Finds the index of the forecast item representing the current ongoing hour (the "Now" item).
     *
     * For a chronologically ordered series of hourly forecasts:
     * - At 12:35 BST (11:35 UTC), the current hour interval is 12:00-13:00 BST (11:00-12:00 UTC).
     * - The forecast item starting at 11:00:00Z is the "Now" item.
     * - The next item at 12:00:00Z is 1 PM (+1h), then 13:00:00Z is 2 PM (+2h).
     *
     * This ensures "Now" represents the current active hour rather than jumping ahead to the next hour
     * when the clock passes the half-hour mark (e.g. 12:35).
     */
    fun findCurrentHourItemIndex(
        fullTimes: List<String?>,
        nowMillis: Long = System.currentTimeMillis(),
        location: LocationItem? = null
    ): Int {
        if (fullTimes.isEmpty()) return 0

        // Find the latest forecast item whose start time is <= nowMillis
        var latestPastIndex = -1
        var latestPastMillis = Long.MIN_VALUE

        for (i in fullTimes.indices) {
            val t = fullTimes[i]
            val m = (if (location != null) parseForecastTimeToMillis(t, location) else parseIsoToMillis(t)) ?: continue
            if (m <= nowMillis && m > latestPastMillis) {
                latestPastMillis = m
                latestPastIndex = i
            }
        }

        if (latestPastIndex >= 0) {
            return latestPastIndex
        }

        // Fallback: if all forecast items start in the future, return 0 (the first available item)
        return 0
    }

    private fun isLocalTimestamp(time: String): Boolean {
        return !time.endsWith("Z", ignoreCase = true) &&
                !Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(time)
    }

    private fun hourFromTimestamp(time: String): Int? {
        return try {
            time.substringAfter("T", "").take(2).toInt()
        } catch (_: Exception) {
            null
        }
    }

    private fun formatLocalHourLabel(time: String): String {
        val hour = hourFromTimestamp(time) ?: return time.takeLast(5)
        val amPm = if (hour >= 12) "PM" else "AM"
        val hour12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "$hour12 $amPm"
    }

    private fun parseForecastTimeToMillis(time: String?, location: LocationItem): Long? {
        if (time.isNullOrBlank()) return null
        if (!isLocalTimestamp(time)) return parseIsoToMillis(time)

        val localFormats = arrayOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm")
        for (pattern in localFormats) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = getTimeZoneForLocation(location)
                }
                val date = parser.parse(time)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Formats the timezone code / name (e.g. "BST (UTC+1)", "EDT (UTC-4)", "GMT") for a location at the current time.
     */
    fun getTimeZoneDisplayName(location: LocationItem, atMillis: Long = System.currentTimeMillis()): String {
        val tz = getTimeZoneForLocation(location)
        val inDst = tz.inDaylightTime(Date(atMillis))
        val shortName = tz.getDisplayName(inDst, TimeZone.SHORT, Locale.US)
        val offsetHours = tz.getOffset(atMillis) / (1000 * 60 * 60)
        val offsetSign = if (offsetHours >= 0) "+$offsetHours" else "$offsetHours"
        return "$shortName (UTC$offsetSign)"
    }

    /**
     * Calculates astronomical sunrise and sunset times (local 24h string HH:mm)
     * for a given location and calendar date using the NOAA standard solar algorithm.
     */
    fun calculateSunTimes(dateStr: String, location: LocationItem): Pair<String, String> {
        return try {
            val tz = getTimeZoneForLocation(location)
            val parts = dateStr.split("-")
            val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
            val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
            val day = parts.getOrNull(2)?.toIntOrNull() ?: 1

            val cal = Calendar.getInstance(tz).apply {
                set(year, month, day, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)

            val lat = location.latitude
            val lon = location.longitude

            // NOAA solar algorithm
            val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1 + (12.0 - 12.0) / 24.0)
            val eqtime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma)
                    - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma))
            val decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
                    - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
                    - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma)

            val latRad = Math.toRadians(lat)
            val zenithRad = Math.toRadians(90.833) // Standard solar zenith accounting for atmospheric refraction

            val cosH = (Math.cos(zenithRad) - Math.sin(latRad) * Math.sin(decl)) / (Math.cos(latRad) * Math.cos(decl))

            val (sunriseMinutesUtc, sunsetMinutesUtc) = if (cosH > 1.0) {
                Pair(720.0, 720.0) // Polar night
            } else if (cosH < -1.0) {
                Pair(0.0, 1440.0) // Midnight sun
            } else {
                val hDeg = Math.toDegrees(Math.acos(cosH))
                val rise = 720.0 - 4.0 * (lon + hDeg) - eqtime
                val set = 720.0 - 4.0 * (lon - hDeg) - eqtime
                Pair(rise, set)
            }

            val offsetMillis = tz.getOffset(cal.timeInMillis)
            val offsetMinutes = offsetMillis / (60 * 1000)

            fun formatMinutes(minutesUtc: Double): String {
                var totalMinutes = (minutesUtc + offsetMinutes).toInt()
                while (totalMinutes < 0) totalMinutes += 1440
                while (totalMinutes >= 1440) totalMinutes -= 1440
                val h = totalMinutes / 60
                val m = totalMinutes % 60
                return String.format(Locale.US, "%02d:%02d", h, m)
            }

            Pair(formatMinutes(sunriseMinutesUtc), formatMinutes(sunsetMinutesUtc))
        } catch (_: Exception) {
            Pair("06:00", "20:00")
        }
    }

    /**
     * Formats sun time (e.g. "05:48" or "2026-08-16T05:48") into 12-hour display string (e.g. "5:48 AM").
     */
    fun formatDisplaySunTime(timeStr: String?): String {
        if (timeStr.isNullOrBlank()) return "--:--"
        val clean = timeStr.trim().takeLast(5)
        val parts = clean.split(":")
        if (parts.size == 2) {
            val h = parts[0].toIntOrNull()
            val m = parts[1].take(2).toIntOrNull()
            if (h != null && m != null) {
                val amPm = if (h >= 12) "PM" else "AM"
                val h12 = when {
                    h == 0 -> 12
                    h > 12 -> h - 12
                    else -> h
                }
                return String.format(Locale.US, "%d:%02d %s", h12, m, amPm)
            }
        }
        return timeStr
    }
}
