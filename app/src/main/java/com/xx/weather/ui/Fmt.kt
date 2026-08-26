package com.xx.weather.ui

import com.xx.weather.data.model.Units
import com.xx.weather.data.model.WeatherData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/** Display formatting + unit conversion helpers. */
object Fmt {

    fun temp(fahrenheit: Double?, units: Units): String {
        if (fahrenheit == null) return "--"
        return "${convert(fahrenheit, units).roundToInt()}°"
    }

    fun convert(fahrenheit: Double, units: Units): Double =
        if (units == Units.CELSIUS) (fahrenheit - 32.0) * 5.0 / 9.0 else fahrenheit

    /**
     * The one H/L renderer shared by the hero, daily card, and widgets so they
     * cannot drift: a missing bound is omitted, never rendered as "--".
     */
    fun hilo(hiF: Double?, loF: Double?, units: Units): String =
        listOfNotNull(
            hiF?.let { "H:${convert(it, units).roundToInt()}°" },
            loF?.let { "L:${convert(it, units).roundToInt()}°" }
        ).joinToString(" ")

    fun hourLabel(t: LocalDateTime): String =
        t.format(DateTimeFormatter.ofPattern("h a", Locale.getDefault()))

    fun clockLabel(t: LocalDateTime): String =
        t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    fun updatedLabel(epochMs: Long): String =
        clockLabel(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime())

    fun dayLabel(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))

    fun fullDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))

    private val DIRS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    /** Cardinal direction FROM which the wind blows (meteorological convention). */
    fun windDir(degrees: Int): String {
        val norm = ((degrees % 360) + 360) % 360
        val idx = floor((norm + 11.25) / 22.5).toInt() % 16
        return DIRS[idx]
    }

    fun uvText(uv: Double): String =
        if (uv < 10.0) String.format(Locale.US, "%.1f", uv) else uv.roundToInt().toString()

    fun uvLabel(uv: Double?): String = when {
        uv == null -> ""
        uv < 3.0 -> "Low"
        uv < 6.0 -> "Moderate"
        uv < 8.0 -> "High"
        uv < 11.0 -> "Very High"
        else -> "Extreme"
    }

    /** Day/night decision: prefer measured sunrise/sunset, fall back to forecast flag. */
    fun isDaytime(data: WeatherData): Boolean {
        val now = LocalDateTime.now()
        val rise = data.sun?.sunrise
        val set = data.sun?.sunset
        if (rise != null && set != null) return now.isAfter(rise) && now.isBefore(set)
        return data.hourly.firstOrNull()?.isDay ?: true
    }
}
