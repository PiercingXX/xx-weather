package com.xx.weather.data.remote

import com.xx.weather.data.Http
import com.xx.weather.data.SunCalc
import com.xx.weather.data.model.Conditions
import com.xx.weather.data.model.CurrentConditions
import com.xx.weather.data.model.DailyPoint
import com.xx.weather.data.model.Extras
import com.xx.weather.data.model.HourlyPoint
import com.xx.weather.data.model.Place
import com.xx.weather.data.model.SunTimes
import com.xx.weather.data.model.WeatherData
import com.xx.weather.data.model.maxPopNextHours
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Fallback + enrichment source: Open-Meteo (free, keyless, CC-BY).
 * `best_match` auto-selects the highest-resolution model per location
 * (NOAA HRRR/NBM/GFS blend over CONUS). Used standalone when NWS fails,
 * and for UV index / visibility / pressure / dew point enrichment.
 */
object OpenMeteoSource {

    private const val BASE = "https://api.open-meteo.com/v1/forecast"

    /**
     * WHY socket timeouts, not coroutine cancellation: Http.get blocks on
     * HttpURLConnection reads that withTimeoutOrNull cannot preempt, so any
     * caller-supplied deadline is enforced via each call's connect/read
     * timeout (the OS honors those) plus a pre-call abort once spent.
     */
    private fun remainingMs(deadlineEpochMs: Long?): Long? =
        deadlineEpochMs?.let { it - System.currentTimeMillis() }

    private fun perCallTimeoutMs(deadlineEpochMs: Long?): Int =
        remainingMs(deadlineEpochMs)?.coerceIn(500L, 12_000L)?.toInt() ?: 12_000

    private fun assertBudget(deadlineEpochMs: Long?) {
        if (remainingMs(deadlineEpochMs)?.let { it <= 500 } == true) {
            throw RuntimeException("OM fetch budget exhausted")
        }
    }

    fun fetchBody(place: Place, deadlineEpochMs: Long? = null): String {
        assertBudget(deadlineEpochMs)
        return Http.get(forecastUrl(place), timeoutMs = perCallTimeoutMs(deadlineEpochMs))
    }

    internal fun forecastUrl(place: Place): String = buildString {
        append(BASE)
        append("?latitude=").append(place.lat)
        append("&longitude=").append(place.lon)
        append("&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,")
        append("weather_code,wind_speed_10m,wind_direction_10m,surface_pressure,pressure_msl,")
        append("dew_point_2m,uv_index,visibility")
        append("&hourly=temperature_2m,precipitation_probability,weather_code,is_day")
        append("&daily=weather_code,temperature_2m_max,temperature_2m_min,")
        append("precipitation_probability_max,sunrise,sunset")
        append("&temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch")
        append("&timezone=auto&forecast_days=10")
    }

    /** Lightweight call used to enrich NWS data with fields NWS does not forecast. */
    fun fetchExtras(place: Place, deadlineEpochMs: Long? = null): Extras {
        val url = buildString {
            append(BASE)
            append("?latitude=").append(place.lat)
            append("&longitude=").append(place.lon)
            // pressure_msl alongside surface_pressure so elevation doesn't
            // masquerade as sea-level pressure.
            append("&current=dew_point_2m,uv_index,visibility,surface_pressure,pressure_msl")
            append("&daily=sunrise,sunset&timezone=auto&temperature_unit=fahrenheit")
            append("&precipitation_unit=inch")
        }
        assertBudget(deadlineEpochMs)
        val root = JSONObject(Http.get(url, timeoutMs = perCallTimeoutMs(deadlineEpochMs)))
        val cur = root.getJSONObject("current")
        val units = root.optJSONObject("current_units")

        fun num(key: String): Double? = cur.optDouble(key).takeUnless { it.isNaN() }

        val daily = root.optJSONObject("daily")
        val sunrise = daily?.optJSONArray("sunrise")
        val sunset = daily?.optJSONArray("sunset")
        val sun = if (sunrise != null && sunset != null &&
            sunrise.length() > 0 && sunset.length() > 0
        ) {
            try {
                SunTimes(
                    LocalDateTime.parse(sunrise.getString(0)),
                    LocalDateTime.parse(sunset.getString(0))
                )
            } catch (_: Exception) {
                null
            }
        } else null

        val msl = num("pressure_msl")
        val surface = num("surface_pressure")
        return Extras(
            uvIndex = num("uv_index"),
            visibilityMi = visibilityToMi(num("visibility"), units),
            pressureInHg = (msl ?: surface)?.times(0.02953), // hPa → inHg
            dewpointF = num("dew_point_2m"),
            sun = sun ?: SunCalc.times(LocalDate.now(), place.lat, place.lon)
                ?.let { SunTimes(it.first, it.second) },
            pressureStationLevel = msl == null && surface != null
        )
    }

    /**
     * Convert Open-Meteo visibility using `current_units.visibility`.
     * Missing/unknown labels are metres (API default).
     */
    private fun visibilityToMi(value: Double?, units: JSONObject?): Double? {
        value ?: return null
        return when (units?.optString("visibility")?.lowercase()?.trim()) {
            "ft", "feet" -> value / 5280.0
            "mi", "mile", "miles" -> value
            else -> value / 1609.344   // "m" / "meter" / "metres" / absent / unknown
        }
    }

    fun parse(place: Place, body: String, nowEpochMs: Long = System.currentTimeMillis()): WeatherData {
        val root = JSONObject(body)
        val cur = root.getJSONObject("current")
        val units = root.optJSONObject("current_units")

        fun num(o: JSONObject, key: String): Double? = o.optDouble(key).takeUnless { it.isNaN() }

        val wmoCode = cur.optInt("weather_code", -1)
        val condition = if (wmoCode >= 0) Conditions.fromWmo(wmoCode) else Conditions.fromNwsText(null)

        // ---- Hourly ----
        val hourlyObj = root.getJSONObject("hourly")
        val hTimes = hourlyObj.getJSONArray("time")
        val hTemps = hourlyObj.getJSONArray("temperature_2m")
        val hPops = hourlyObj.optJSONArray("precipitation_probability")
        val hCodes = hourlyObj.getJSONArray("weather_code")
        val hIsDay = hourlyObj.optJSONArray("is_day")

        val nowLocal = LocalDateTime.now()
        var startIdx = 0
        for (i in 0 until hTimes.length()) {
            val t = parseLocal(hTimes, i) ?: continue
            if (!t.isBefore(nowLocal)) {
                startIdx = i
                break
            }
            startIdx = i
        }

        val hourly = ArrayList<HourlyPoint>(24)
        var i = startIdx
        while (i < hTimes.length() && hourly.size < 24) {
            val t = parseLocal(hTimes, i)
            if (t != null) {
                hourly += HourlyPoint(
                    time = t,
                    tempF = hTemps.optDouble(i).takeUnless { it.isNaN() } ?: 0.0,
                    popPct = hPops?.optInt(i, -1)?.takeIf { it >= 0 },
                    condition = Conditions.fromWmo(hCodes.optInt(i, 2)),
                    isDay = hIsDay?.optInt(i, 1) == 1
                )
            }
            i++
        }

        // ---- Daily ----
        val dailyObj = root.getJSONObject("daily")
        val dTimes = dailyObj.getJSONArray("time")
        val dHi = dailyObj.getJSONArray("temperature_2m_max")
        val dLo = dailyObj.getJSONArray("temperature_2m_min")
        val dCodes = dailyObj.getJSONArray("weather_code")
        val dPops = dailyObj.optJSONArray("precipitation_probability_max")
        val sunriseArr = dailyObj.optJSONArray("sunrise")
        val sunsetArr = dailyObj.optJSONArray("sunset")

        val daily = (0 until minOf(dTimes.length(), 10)).map { idx ->
            DailyPoint(
                date = LocalDate.parse(dTimes.getString(idx)),
                // WHY null on NaN: a missing high must be omitted downstream
                // (Fmt.hilo drops the H: bound), never rendered as a fake 0°.
                hiF = dHi.optDouble(idx).takeUnless { it.isNaN() },
                loF = dLo.optDouble(idx).takeUnless { it.isNaN() } ?: 0.0,
                popPct = dPops?.optInt(idx, -1)?.takeIf { it >= 0 },
                condition = Conditions.fromWmo(dCodes.optInt(idx, 2)),
                uvMax = null
            )
        }

        val sun = if (sunriseArr != null && sunsetArr != null &&
            sunriseArr.length() > 0 && sunsetArr.length() > 0
        ) {
            try {
                SunTimes(
                    LocalDateTime.parse(sunriseArr.getString(0)),
                    LocalDateTime.parse(sunsetArr.getString(0))
                )
            } catch (_: Exception) {
                null
            }
        } else {
            SunCalc.times(LocalDate.now(), place.lat, place.lon)
                ?.let { SunTimes(it.first, it.second) }
        }

        val mslHpa = num(cur, "pressure_msl")
        val surfaceHpa = num(cur, "surface_pressure")
        val current = CurrentConditions(
            tempF = num(cur, "temperature_2m") ?: hourly.firstOrNull()?.tempF ?: 0.0,
            feelsLikeF = num(cur, "apparent_temperature"),
            condition = condition,
            conditionText = Conditions.label(condition),
            humidityPct = num(cur, "relative_humidity_2m")?.toInt(),
            windMph = num(cur, "wind_speed_10m"),
            windDirDeg = num(cur, "wind_direction_10m")?.toInt(),
            pressureInHg = (mslHpa ?: surfaceHpa)?.times(0.02953),
            visibilityMi = visibilityToMi(num(cur, "visibility"), units),
            uvIndex = num(cur, "uv_index"),
            dewpointF = num(cur, "dew_point_2m"),
            popPct = maxPopNextHours(hourly, nowEpochMs),
            pressureStationLevel = mslHpa == null && surfaceHpa != null
        )

        return WeatherData(
            place = place,
            updatedAtEpochMs = nowEpochMs,
            current = current,
            hourly = hourly,
            daily = daily,
            sun = sun,
            source = "Open-Meteo"
        )
    }

    private fun parseLocal(arr: JSONArray, idx: Int): LocalDateTime? = try {
        LocalDateTime.parse(arr.getString(idx))
    } catch (_: Exception) {
        null
    }
}
