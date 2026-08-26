package com.xx.weather.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sunrise/sunset via the standard NOAA solar equations (public domain),
 * so the app never depends on a network call for day/night or sun times.
 */
object SunCalc {

    /** Returns (sunrise, sunset) as local date-times, or null in polar day/night. */
    fun times(date: LocalDate, latDeg: Double, lonDeg: Double): Pair<LocalDateTime, LocalDateTime>? {
        val utc = timesUtc(date, latDeg, lonDeg) ?: return null
        val zone = ZoneId.systemDefault()
        val rise = Instant.ofEpochMilli(utc.first).atZone(zone).toLocalDateTime()
        val set = Instant.ofEpochMilli(utc.second).atZone(zone).toLocalDateTime()
        return Pair(rise, set)
    }

    fun timesUtc(date: LocalDate, latDeg: Double, lonDeg: Double): Pair<Long, Long>? {
        val rad = Math.PI / 180.0
        // Days since J2000.0 (2000-01-01 noon UT), plus fractional leap correction.
        val n = date.toEpochDay() - 10957 + 0.0008
        val jStar = n - lonDeg / 360.0
        val meanAnomaly = norm(357.5291 + 0.98560028 * jStar)
        val center = 1.9148 * sin(meanAnomaly * rad) +
            0.02 * sin(2 * meanAnomaly * rad) +
            0.0003 * sin(3 * meanAnomaly * rad)
        val eclipticLon = norm(meanAnomaly + center + 180.0 + 102.9372)
        val jTransit = 2451545.0 + jStar +
            0.0053 * sin(meanAnomaly * rad) -
            0.0069 * sin(2 * eclipticLon * rad)
        val declination = asin(sin(eclipticLon * rad) * sin(23.44 * rad))
        val cosOmega = (sin(-0.833 * rad) - sin(latDeg * rad) * sin(declination)) /
            (cos(latDeg * rad) * cos(declination))
        if (cosOmega < -1.0 || cosOmega > 1.0) return null
        val omega = acos(cosOmega) / rad
        val jRise = jTransit - omega / 360.0
        val jSet = jTransit + omega / 360.0
        return Pair(toEpochMillis(jRise), toEpochMillis(jSet))
    }

    private fun toEpochMillis(julianDay: Double): Long =
        ((julianDay - 2440587.5) * 86400000.0).toLong()

    private fun norm(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }
}
