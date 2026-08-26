package com.xx.weather.data.remote

import com.xx.weather.data.Http
import com.xx.weather.data.SunCalc
import com.xx.weather.data.model.Condition
import com.xx.weather.data.model.Conditions
import com.xx.weather.data.model.CurrentConditions
import com.xx.weather.data.model.DailyPoint
import com.xx.weather.data.model.HourlyPoint
import com.xx.weather.data.model.Place
import com.xx.weather.data.model.SunTimes
import com.xx.weather.data.model.WeatherData
import com.xx.weather.data.model.maxPopNextHours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.LinkedHashMap
import java.util.Locale

/**
 * Primary source: NOAA National Weather Service api.weather.gov.
 * Free, keyless, human-edited NDFD forecasts — the most accurate short-range
 * point forecasts available for US locations. Requires an identifying User-Agent.
 */
object NwsSource {

    private const val UA = "(xx-weather, https://github.com/PiercingXX/xx-weather)"

    /** Short timeout for the best-effort observation chain. */
    private const val OBS_TIMEOUT_MS = 4000

    /** Raw response bodies kept separately so the repository can cache them verbatim. */
    data class Bodies(
        val daily: String,
        val hourly: String,
        val obs: String?,
        val city: String?,
        val state: String?
    )

    suspend fun fetchBodies(place: Place, deadlineEpochMs: Long? = null): Bodies {
        // WHY socket timeouts, not coroutine cancellation: Http.get blocks on
        // HttpURLConnection reads that withTimeoutOrNull cannot preempt, so the
        // deadline is enforced by threading an explicit epoch budget down to
        // each call's connect/read timeout (the OS honors those), plus a
        // pre-call abort once the budget is spent.
        fun remaining(): Long? = deadlineEpochMs?.let { it - System.currentTimeMillis() }

        fun assertBudget() {
            if (remaining()?.let { it <= 500 } == true) {
                throw RuntimeException("NWS fetch budget exhausted")
            }
        }

        fun perCallTimeoutMs(): Int = remaining()?.coerceIn(500L, 12_000L)?.toInt() ?: 12_000

        // Locale.US is critical: comma-decimal locales would corrupt the URL.
        val latStr = String.format(Locale.US, "%.4f", place.lat)
        val lonStr = String.format(Locale.US, "%.4f", place.lon)
        val pointsUrl = "https://api.weather.gov/points/$latStr,$lonStr"
        assertBudget()
        val props = JSONObject(
            Http.get(pointsUrl, UA, timeoutMs = perCallTimeoutMs())
        ).getJSONObject("properties")

        val hourlyUrl = props.getString("forecastHourly")
        val dailyUrl = props.getString("forecast")
        val stationsUrl = props.optString("observationStations")
        val relProps = props.optJSONObject("relativeLocation")?.optJSONObject("properties")
        val city = relProps?.optString("city")?.takeUnless { it.isEmpty() }
        val state = relProps?.optString("state")?.takeUnless { it.isEmpty() }

        // WHY parallel: five sequential 12s calls could spend 60s inside the
        // widget's ~8.5s budget; after /points everything runs concurrently.
        return coroutineScope {
            val hourlyDeferred = async(Dispatchers.IO) {
                assertBudget()
                Http.get(hourlyUrl, UA, timeoutMs = perCallTimeoutMs())
            }
            val dailyDeferred = async(Dispatchers.IO) {
                assertBudget()
                Http.get(dailyUrl, UA, timeoutMs = perCallTimeoutMs())
            }

            // Observations stay best-effort: their own short timeouts plus
            // swallowed errors mean they can neither fail nor delay the
            // forecast pair beyond that budget. Blocking IO can't be cancelled
            // cooperatively, so this section bounds itself instead of relying
            // on a coroutine timeout: enter only with >=1.5s left, give the
            // stations call at most half the remaining budget and the
            // obs-latest call the rest, keeping the section near its ~5s intent.
            val obsDeferred = async(Dispatchers.IO) {
                try {
                    if (stationsUrl.isEmpty()) return@async null
                    val startRem = remaining()
                    if (startRem != null && startRem < 1_500L) return@async null
                    val sectionEnd = System.currentTimeMillis() +
                        (startRem?.coerceAtMost(5_000L) ?: 5_000L)
                    val features = JSONObject(
                        Http.get(
                            stationsUrl,
                            UA,
                            timeoutMs = ((sectionEnd - System.currentTimeMillis()) / 2)
                                .toInt().coerceIn(500, OBS_TIMEOUT_MS)
                        )
                    ).getJSONArray("features")
                    if (features.length() == 0) return@async null
                    val stationId = features.getJSONObject(0)
                        .getJSONObject("properties")
                        .getString("stationIdentifier")
                    val latestTimeoutMs =
                        (sectionEnd - System.currentTimeMillis()).toInt()
                    if (latestTimeoutMs <= 500) return@async null
                    Http.get(
                        "https://api.weather.gov/stations/$stationId/observations/latest",
                        UA,
                        timeoutMs = latestTimeoutMs.coerceIn(500, OBS_TIMEOUT_MS)
                    )
                } catch (_: Exception) {
                    null // observations are optional; hourly forecast covers "now"
                }
            }

            val hourly = hourlyDeferred.await()
            val daily = dailyDeferred.await()
            val obs = obsDeferred.await()
            Bodies(daily = daily, hourly = hourly, obs = obs, city = city, state = state)
        }
    }

    fun assemble(place: Place, bodies: Bodies, nowEpochMs: Long = System.currentTimeMillis()): WeatherData {
        val hourly = parseHourly(bodies.hourly)
        require(hourly.isNotEmpty()) { "NWS hourly forecast was empty" }
        val daily = parseDaily(bodies.daily).map { d ->
            // A night-only date (evening "Tonight") has no daytime period temp;
            // backfill the high from that local date's remaining hourly temps
            // so the overnight low is never shown as the day's high.
            if (d.hiF != null) d
            else d.copy(
                hiF = hourly.filter { it.time.toLocalDate() == d.date }
                    .maxOfOrNull { it.tempF }
            )
        }
        val observed = parseObs(bodies.obs) ?: fallbackCurrent(hourly.first())
        // METAR observations carry no PoP: fill from the next 12 hourly points.
        val current = observed.copy(
            popPct = observed.popPct ?: maxPopNextHours(hourly, nowEpochMs)
        )
        val sun = SunCalc.times(LocalDate.now(), place.lat, place.lon)
            ?.let { SunTimes(it.first, it.second) }
        return WeatherData(
            place = place.copy(city = bodies.city ?: place.city, state = bodies.state ?: place.state),
            updatedAtEpochMs = nowEpochMs,
            current = current,
            hourly = hourly,
            daily = daily,
            sun = sun,
            source = "NWS"
        )
    }

    fun parseHourly(body: String): List<HourlyPoint> {
        val periods = JSONObject(body).getJSONObject("properties").getJSONArray("periods")
        return (0 until periods.length()).mapNotNull { i ->
            val p = periods.getJSONObject(i)
            val time = try {
                OffsetDateTime.parse(p.getString("startTime")).toLocalDateTime()
            } catch (_: Exception) {
                return@mapNotNull null
            }
            val pop = p.optJSONObject("probabilityOfPrecipitation")
                ?.optInt("value", -1)?.takeIf { it >= 0 }
            val temp = p.optDouble("temperature")
            if (temp.isNaN()) return@mapNotNull null
            HourlyPoint(
                time = time,
                tempF = temp,
                popPct = pop,
                condition = Conditions.fromNwsIcon(p.optString("icon"), p.optString("shortForecast")),
                isDay = p.optBoolean("isDaytime", true)
            )
        }
    }

    /**
     * Aggregate NWS 12-hour periods into calendar days. Daytime period temps are
     * highs, nighttime period temps are lows; PoP takes the day's maximum.
     * Night-only dates (evening "Tonight") keep hiF null rather than reusing
     * the overnight low as a high.
     */
    fun parseDaily(body: String): List<DailyPoint> {
        val periods = JSONObject(body).getJSONObject("properties").getJSONArray("periods")

        class Agg {
            var hi: Double? = null
            var lo: Double? = null
            var pop: Int? = null
            var firstCond: Condition? = null
            var dayCond: Condition? = null
        }

        val byDate = LinkedHashMap<LocalDate, Agg>()
        for (i in 0 until periods.length()) {
            val p = periods.getJSONObject(i)
            val date = try {
                OffsetDateTime.parse(p.getString("startTime")).toLocalDate()
            } catch (_: Exception) {
                continue
            }
            val temp = p.optDouble("temperature")
            if (temp.isNaN()) continue
            val isDay = p.optBoolean("isDaytime", true)
            val pop = p.optJSONObject("probabilityOfPrecipitation")
                ?.optInt("value", -1)?.takeIf { it >= 0 }
            val cond = Conditions.fromNwsIcon(p.optString("icon"), p.optString("shortForecast"))

            val agg = byDate.getOrPut(date) { Agg() }
            if (isDay) {
                agg.hi = maxOf(agg.hi ?: temp, temp)
                if (agg.dayCond == null) agg.dayCond = cond
            } else {
                agg.lo = minOf(agg.lo ?: temp, temp)
            }
            if (agg.firstCond == null) agg.firstCond = cond
            if (pop != null) agg.pop = maxOf(agg.pop ?: pop, pop)
        }

        return byDate.map { (date, a) ->
            DailyPoint(
                date = date,
                // Never fall back to the night low here; assemble backfills
                // from hourly temps when a real high exists for the date.
                hiF = a.hi,
                loF = a.lo ?: a.hi ?: 0.0,
                popPct = a.pop,
                condition = a.dayCond ?: a.firstCond ?: Condition.PARTLY_CLOUDY,
                uvMax = null
            )
        }.take(10)
    }

    /** Latest METAR-style observation from the nearest NWS station, if available. */
    fun parseObs(body: String?): CurrentConditions? {
        if (body.isNullOrEmpty()) return null
        return try {
            val props = JSONObject(body).getJSONObject("properties")

            fun num(key: String): Double? =
                props.optJSONObject(key)?.optDouble("value")?.takeUnless { it.isNaN() }

            fun cToF(c: Double?): Double? = c?.let { it * 9.0 / 5.0 + 32.0 }

            val tempF = cToF(num("temperature")) ?: return null
            val feelsC = if (tempF <= 60.0) num("windChill") else num("heatIndex")
            // Prefer MSL: barometricPressure is station pressure at elevation
            // (Denver ≈ 846 hPa would read like a crashing barometer).
            val seaLevelPa = num("seaLevelPressure")
            val barometricPa = num("barometricPressure")
            val pressurePa = seaLevelPa ?: barometricPa
            val condition = Conditions.fromNwsIcon(
                props.optString("icon"), props.optString("textDescription")
            )
            val text = props.optString("textDescription")
            CurrentConditions(
                tempF = tempF,
                feelsLikeF = cToF(feelsC),
                condition = condition,
                conditionText = text.ifBlank { Conditions.label(condition) },
                humidityPct = num("relativeHumidity")?.toInt(),
                windMph = num("windSpeed")?.times(0.621371),          // km/h → mph
                windDirDeg = num("windDirection")?.toInt(),           // degrees FROM
                pressureInHg = pressurePa?.div(3386.389),             // Pa → inHg
                visibilityMi = num("visibility")?.div(1609.344),      // m → mi
                uvIndex = null,
                dewpointF = cToF(num("dewpoint")),
                popPct = null,
                pressureStationLevel = seaLevelPa == null && barometricPa != null
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackCurrent(firstHour: HourlyPoint) = CurrentConditions(
        tempF = firstHour.tempF,
        feelsLikeF = null,
        condition = firstHour.condition,
        conditionText = Conditions.label(firstHour.condition),
        humidityPct = null,
        windMph = null,
        windDirDeg = null,
        pressureInHg = null,
        visibilityMi = null,
        uvIndex = null,
        dewpointF = null,
        // assemble fills PoP uniformly from the next 12 hourly points.
        popPct = null
    )
}
