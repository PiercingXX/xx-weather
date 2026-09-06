package com.xx.weather.data

import com.xx.weather.data.model.Condition
import com.xx.weather.data.model.HourlyPoint
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Per-ZIP precip / freeze triggers persisted in Prefs. */
data class ZipAlertTriggers(
    val precipEnabled: Boolean = false,
    val precipHours: Int = AlertEvaluator.DEFAULT_PRECIP_HOURS,
    val tempEnabled: Boolean = false,
    val tempAtOrBelowF: Int = AlertEvaluator.DEFAULT_TEMP_F,
) {
    val anyEnabled: Boolean get() = precipEnabled || tempEnabled
}

/** Encode/decode the per-ZIP trigger map. Pure so JVM tests can pin it. */
object ZipAlertCodec {

    fun encode(map: Map<String, ZipAlertTriggers>): String {
        val o = JSONObject()
        for ((zip, t) in map) {
            o.put(
                zip,
                JSONObject()
                    .put("precip", t.precipEnabled)
                    .put("hours", t.precipHours)
                    .put("temp", t.tempEnabled)
                    .put("tempF", t.tempAtOrBelowF),
            )
        }
        return o.toString()
    }

    fun decode(raw: String?): Map<String, ZipAlertTriggers> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(raw)
            val names = o.names() ?: return emptyMap()
            (0 until names.length()).mapNotNull { i ->
                val zip = names.optString(i)
                if (!zip.matches(ZIP_RE)) return@mapNotNull null
                val t = o.optJSONObject(zip) ?: return@mapNotNull null
                zip to ZipAlertTriggers(
                    precipEnabled = t.optBoolean("precip", false),
                    precipHours = t.optInt("hours", AlertEvaluator.DEFAULT_PRECIP_HOURS)
                        .coerceIn(1, 24),
                    tempEnabled = t.optBoolean("temp", false),
                    tempAtOrBelowF = t.optInt("tempF", AlertEvaluator.DEFAULT_TEMP_F)
                        .coerceIn(-80, 140),
                )
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private val ZIP_RE = Regex("\\d{5}")
}

/**
 * Pure trigger evaluation + fetch-window dedupe. No Context, no notify.
 *
 * Precip: max PoP in the next N hourly points (count-capped, same as
 * [com.xx.weather.data.model.maxPopNextHours]) is at least [PRECIP_POP_PCT],
 * or any of those points is already a precip condition.
 * Temp: current °F at or below the threshold.
 * Dedupe key: zip + trigger kind + fetch window ([WeatherData.updatedAtEpochMs]).
 */
object AlertEvaluator {

    const val DEFAULT_PRECIP_HOURS = 6
    const val DEFAULT_TEMP_F = 32
    const val PRECIP_POP_PCT = 50

    enum class Kind { PRECIP, TEMP }

    data class Fire(
        val kind: Kind,
        val zip: String,
        val window: Long,
        val title: String,
        val text: String,
    ) {
        val key: String get() = dedupeKey(zip, kind, window)
    }

    fun dedupeKey(zip: String, kind: Kind, window: Long): String =
        "$zip|${kind.name}|$window"

    fun rememberKeys(
        already: Set<String>,
        newKeys: Collection<String>,
        limit: Int = 64,
    ): Set<String> {
        if (newKeys.isEmpty()) return already
        val list = already.toList() + newKeys.filter { it !in already }
        return list.takeLast(limit).toSet()
    }

    fun evaluate(
        zip: String,
        city: String,
        hourly: List<HourlyPoint>,
        currentTempF: Double,
        fetchedAtEpochMs: Long,
        nowEpochMs: Long,
        triggers: ZipAlertTriggers,
        alreadyNotified: Set<String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Fire> {
        if (!triggers.anyEnabled) return emptyList()
        val window = fetchedAtEpochMs
        val where = city.ifBlank { zip }
        val out = ArrayList<Fire>(2)
        if (triggers.precipEnabled) {
            val hours = triggers.precipHours.coerceIn(1, 24)
            val upcoming = upcomingHourly(hourly, nowEpochMs, hours, zone)
            val maxPop = upcoming.mapNotNull { it.popPct }.maxOrNull()
            val precipNow = upcoming.any { it.condition in PRECIP_CONDITIONS }
            if ((maxPop != null && maxPop >= PRECIP_POP_PCT) || precipNow) {
                val fire = Fire(
                    kind = Kind.PRECIP,
                    zip = zip,
                    window = window,
                    title = "Precipitation nearby",
                    text = buildString {
                        append("Precipitation in the next $hours hours in $where")
                        if (maxPop != null) append(" ($maxPop% chance)")
                        append(".")
                    },
                )
                if (fire.key !in alreadyNotified) out.add(fire)
            }
        }
        if (triggers.tempEnabled && currentTempF <= triggers.tempAtOrBelowF) {
            val fire = Fire(
                kind = Kind.TEMP,
                zip = zip,
                window = window,
                title = "Temperature alert",
                text = "$where is ${currentTempF.roundToInt()}°F (at or below ${triggers.tempAtOrBelowF}°F).",
            )
            if (fire.key !in alreadyNotified) out.add(fire)
        }
        return out
    }

    fun upcomingHourly(
        hourly: List<HourlyPoint>,
        nowEpochMs: Long,
        hours: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<HourlyPoint> {
        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDateTime()
        return hourly.asSequence()
            .filter { !it.time.isBefore(now) }
            .take(hours.coerceIn(1, 24))
            .toList()
    }

    private val PRECIP_CONDITIONS = setOf(
        Condition.DRIZZLE,
        Condition.RAIN,
        Condition.HEAVY_RAIN,
        Condition.SHOWERS,
        Condition.THUNDERSTORM,
        Condition.SNOW,
        Condition.HEAVY_SNOW,
        Condition.SLEET,
    )
}
