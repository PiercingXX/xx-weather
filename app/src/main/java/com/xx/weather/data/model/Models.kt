package com.xx.weather.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Normalized weather condition driving icons, background art, and labels. */
enum class Condition {
    CLEAR,
    MOSTLY_CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    SHOWERS,
    THUNDERSTORM,
    SNOW,
    HEAVY_SNOW,
    SLEET,
    WINDY
}

enum class Units { FAHRENHEIT, CELSIUS }

data class Place(
    val zip: String,
    val city: String,
    val state: String,
    val lat: Double,
    val lon: Double
)

data class CurrentConditions(
    val tempF: Double,
    val feelsLikeF: Double?,
    val condition: Condition,
    val conditionText: String,
    val humidityPct: Int?,
    val windMph: Double?,
    val windDirDeg: Int?,
    val pressureInHg: Double?,
    val visibilityMi: Double?,
    val uvIndex: Double?,
    val dewpointF: Double?,
    val popPct: Int?,
    // True only when the reading is station-level pressure (no MSL field was
    // available), so the UI can say "Station" instead of lying "Sea level".
    val pressureStationLevel: Boolean = false
)

data class HourlyPoint(
    val time: LocalDateTime,
    val tempF: Double,
    val popPct: Int?,
    val condition: Condition,
    val isDay: Boolean
)

/**
 * Max PoP across the next [hours] hourly points at or after [nowEpochMs]
 * (points carry LOCAL date-times, resolved against the device zone); null when
 * no such point reports PoP.
 *
 * WHY count-capped instead of a strict [now, now+12h) clock window: hourly
 * feeds are evenly spaced, so "first 12 points at or after now" equals that
 * window — and counting keeps working when a feed's first entry sits beyond
 * the window (stale cache, sparse periods) instead of returning empty.
 */
fun maxPopNextHours(
    hourly: List<HourlyPoint>,
    nowEpochMs: Long,
    hours: Int = 12
): Int? {
    val now = Instant.ofEpochMilli(nowEpochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    return hourly.asSequence()
        .filter { !it.time.isBefore(now) }
        .take(hours)
        .mapNotNull { it.popPct }
        .maxOrNull()
}

data class DailyPoint(
    val date: LocalDate,
    // Null for night-only dates (evening "Tonight"): the overnight low must
    // never be presented as the day's high. Callers may backfill from hourly.
    val hiF: Double?,
    val loF: Double,
    val popPct: Int?,
    val condition: Condition,
    val uvMax: Double?
)

data class SunTimes(
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?
)

/** Enrichment fields sourced from Open-Meteo when NWS does not provide them. */
data class Extras(
    val uvIndex: Double?,
    val visibilityMi: Double?,
    val pressureInHg: Double?,
    val dewpointF: Double?,
    val sun: SunTimes?,
    val pressureStationLevel: Boolean = false
)

data class WeatherData(
    val place: Place,
    val updatedAtEpochMs: Long,
    val current: CurrentConditions,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    val sun: SunTimes?,
    val source: String
)

/** Condition mapping helpers for NWS icon tokens, NWS text, and WMO codes. */
object Conditions {

    fun label(c: Condition): String = when (c) {
        Condition.CLEAR -> "Clear"
        Condition.MOSTLY_CLEAR -> "Mostly Clear"
        Condition.PARTLY_CLOUDY -> "Partly Cloudy"
        Condition.CLOUDY -> "Cloudy"
        Condition.OVERCAST -> "Overcast"
        Condition.FOG -> "Foggy"
        Condition.DRIZZLE -> "Drizzle"
        Condition.RAIN -> "Rain"
        Condition.HEAVY_RAIN -> "Heavy Rain"
        Condition.SHOWERS -> "Rain Showers"
        Condition.THUNDERSTORM -> "Thunderstorm"
        Condition.SNOW -> "Snow"
        Condition.HEAVY_SNOW -> "Heavy Snow"
        Condition.SLEET -> "Sleet / Mix"
        Condition.WINDY -> "Windy"
    }

    /** Severity rank for dual-condition icon URLs: higher wins (tstorm > precip > fog > cloud > clear). */
    private val SEVERITY = mapOf(
        Condition.CLEAR to 0,
        Condition.MOSTLY_CLEAR to 1,
        Condition.PARTLY_CLOUDY to 1,
        Condition.CLOUDY to 2,
        Condition.OVERCAST to 2,
        Condition.WINDY to 3,
        Condition.FOG to 4,
        Condition.DRIZZLE to 5,
        Condition.SHOWERS to 5,
        Condition.RAIN to 5,
        Condition.HEAVY_RAIN to 5,
        Condition.SLEET to 6,
        Condition.SNOW to 6,
        Condition.HEAVY_SNOW to 6,
        Condition.THUNDERSTORM to 7
    )

    private val ICON_TOKENS = mapOf(
        "skc" to Condition.CLEAR,
        "wind_skc" to Condition.CLEAR,
        "few" to Condition.MOSTLY_CLEAR,
        "wind_few" to Condition.MOSTLY_CLEAR,
        "sct" to Condition.PARTLY_CLOUDY,
        "wind_sct" to Condition.PARTLY_CLOUDY,
        "bkn" to Condition.CLOUDY,
        "wind_bkn" to Condition.CLOUDY,
        "ovc" to Condition.OVERCAST,
        "cloud" to Condition.OVERCAST,
        "wind_ovc" to Condition.OVERCAST,
        "fg" to Condition.FOG,
        "fzfg" to Condition.FOG,
        "haze" to Condition.FOG,
        "br" to Condition.FOG,
        "du" to Condition.FOG,
        "dust" to Condition.FOG,
        "smoke" to Condition.FOG,
        "fu" to Condition.FOG,
        "ra" to Condition.RAIN,
        "rain" to Condition.RAIN,
        "shra" to Condition.SHOWERS,
        "rain_showers" to Condition.SHOWERS,
        "rain_showers_high" to Condition.SHOWERS,
        "rain_showers_hi" to Condition.SHOWERS,
        "hi_shwrs" to Condition.SHOWERS,
        "drizzle" to Condition.DRIZZLE,
        "fzdrizzle" to Condition.DRIZZLE,
        "tsra" to Condition.THUNDERSTORM,
        "tsra_sct" to Condition.THUNDERSTORM,
        "tsra_hi" to Condition.THUNDERSTORM,
        "trw" to Condition.THUNDERSTORM,
        "sn" to Condition.SNOW,
        "snow" to Condition.SNOW,
        "blizzard" to Condition.SNOW,
        "blowing_snow" to Condition.SNOW,
        "snbr" to Condition.SNOW,
        "heavy_snow" to Condition.HEAVY_SNOW,
        "fzra" to Condition.SLEET,
        "raip" to Condition.SLEET,
        "rain_snow" to Condition.SLEET,
        "sleet" to Condition.SLEET,
        "ip" to Condition.SLEET,
        "mix" to Condition.SLEET,
        "rain_sleet" to Condition.SLEET,
        "snow_sleet" to Condition.SLEET,
        "slip" to Condition.SLEET
    )

    /**
     * Map an api.weather.gov icon URL (…/icons/land/day/tsra_hi,40…) to a [Condition].
     *
     * Dual-condition URLs like …/day/sct/tsra_hi,40 encode several tokens, so
     * EVERY token after the day|night segment is scanned and the MOST SEVERE
     * match wins; unmapped tokens are skipped. Text fallback stays last resort.
     */
    fun fromNwsIcon(url: String?, fallbackText: String? = null): Condition {
        if (!url.isNullOrEmpty()) {
            val tail = url.substringAfter("/icons/", "").substringBefore("?")
            if (tail.isNotEmpty()) {
                val tokens = tail.split('/', ',')
                val start = tokens.indexOfFirst { it == "day" || it == "night" }
                if (start >= 0) {
                    var best: Condition? = null
                    for (token in tokens.subList(start + 1, tokens.size)) {
                        val mapped = ICON_TOKENS[token] ?: continue
                        if (best == null || SEVERITY.getValue(mapped) > SEVERITY.getValue(best)) {
                            best = mapped
                        }
                    }
                    if (best != null) return best
                }
            }
        }
        return fromNwsText(fallbackText)
    }

    /** Keyword mapping over NWS shortForecast / textDescription strings. */
    fun fromNwsText(text: String?): Condition {
        val t = text?.lowercase() ?: return Condition.PARTLY_CLOUDY
        return when {
            "thunderstorm" in t || "t-storm" in t || "tsra" in t -> Condition.THUNDERSTORM
            "heavy snow" in t || "blizzard" in t -> Condition.HEAVY_SNOW
            "snow" in t && ("rain" in t || "sleet" in t || "mix" in t || "freezing" in t) -> Condition.SLEET
            "snow" in t || "flurr" in t -> Condition.SNOW
            "heavy rain" in t -> Condition.HEAVY_RAIN
            "showers" in t -> Condition.SHOWERS
            "drizzle" in t -> Condition.DRIZZLE
            "rain" in t -> Condition.RAIN
            "fog" in t || "haze" in t || "smoke" in t || "mist" in t -> Condition.FOG
            "sleet" in t || "freezing rain" in t -> Condition.SLEET
            "mostly cloudy" in t || "considerable cloudiness" in t -> Condition.CLOUDY
            "cloudy" in t || "overcast" in t -> Condition.OVERCAST
            "partly sunny" in t || "partly cloudy" in t || "increasing clouds" in t -> Condition.PARTLY_CLOUDY
            "mostly sunny" in t || "mostly clear" in t -> Condition.MOSTLY_CLEAR
            "sunny" in t || "clear" in t || "fair" in t -> Condition.CLEAR
            "windy" in t || "breezy" in t -> Condition.WINDY
            else -> Condition.PARTLY_CLOUDY
        }
    }

    /** WMO weather-code table used by Open-Meteo. */
    fun fromWmo(code: Int): Condition = when (code) {
        0 -> Condition.CLEAR
        1 -> Condition.MOSTLY_CLEAR
        2 -> Condition.PARTLY_CLOUDY
        3 -> Condition.OVERCAST
        45, 48 -> Condition.FOG
        51, 53, 55, 56, 57 -> Condition.DRIZZLE
        61, 63 -> Condition.RAIN
        65 -> Condition.HEAVY_RAIN
        66, 67 -> Condition.SLEET
        71, 73, 77, 85 -> Condition.SNOW
        75, 86 -> Condition.HEAVY_SNOW
        80, 81, 82 -> Condition.SHOWERS
        95, 96, 99 -> Condition.THUNDERSTORM
        else -> Condition.PARTLY_CLOUDY
    }
}
