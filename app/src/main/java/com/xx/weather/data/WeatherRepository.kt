package com.xx.weather.data

import android.content.Context
import com.xx.weather.data.model.Extras
import com.xx.weather.data.model.Place
import com.xx.weather.data.model.SunTimes
import com.xx.weather.data.model.WeatherData
import com.xx.weather.data.remote.NwsSource
import com.xx.weather.data.remote.OpenMeteoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime

/**
 * Repository: instant offline display from raw-response caches, then refresh.
 *
 * Accuracy pipeline (researched):
 *  1. PRIMARY  — NWS api.weather.gov (human-edited NDFD; best US short-range accuracy)
 *  2. ENRICH   — Open-Meteo for UV index / visibility / pressure / dew point / sun times
 *  3. FALLBACK — full standalone Open-Meteo (best_match NOAA blend) if NWS unreachable
 */
class WeatherRepository(private val context: Context) {

    sealed interface RefreshResult {
        data class Success(val data: WeatherData, val fromCache: Boolean) : RefreshResult
        data class Failure(val message: String, val stale: WeatherData?) : RefreshResult
    }

    // ---------------------------------------------------------------- cache

    private fun cacheFile(name: String) = File(context.filesDir, name)

    private fun readCache(name: String): String? = try {
        cacheFile(name).takeIf { it.exists() }?.readText()
    } catch (_: Exception) {
        null
    }

    private fun writeCache(name: String, body: String) {
        // Unique tmp name: a stray concurrent writer can never collide on it.
        val tmp = File(context.filesDir, "$name.${System.nanoTime()}.tmp")
        try {
            tmp.writeText(body)
            // rename(2) atomically replaces an existing target on Linux/Android.
            if (!tmp.renameTo(cacheFile(name))) tmp.delete()
        } catch (_: Exception) {
            tmp.delete()
        }
    }

    private fun deleteCache(name: String) {
        try {
            cacheFile(name).delete()
        } catch (_: Exception) {
        }
    }

    private fun clearCaches() {
        listOf(NWS_DAILY, NWS_HOURLY, NWS_OBS, OM_BODY, EXTRAS, FETCH_STAMP).forEach {
            deleteCache(it)
        }
    }

    /** Sidecar stamp: epoch ms of the last successful primary/fallback fetch. */
    private fun writeStamp(fetchedAtEpochMs: Long) {
        // Same tmp+rename path as writeCache so a reader can never observe a
        // torn/half-written stamp.
        writeCache(FETCH_STAMP, fetchedAtEpochMs.toString())
    }

    /** Null when missing/unparseable (old install with cache but no stamp). */
    private fun readStamp(): Long? =
        readCache(FETCH_STAMP)?.trim()?.toLongOrNull()

    private fun writeExtras(ex: Extras) {
        val o = JSONObject()
        ex.uvIndex?.let { o.put("uv", it) }
        ex.visibilityMi?.let { o.put("vis", it) }
        ex.pressureInHg?.let { o.put("pres", it) }
        ex.dewpointF?.let { o.put("dew", it) }
        if (ex.pressureStationLevel) o.put("presStation", true)
        ex.sun?.sunrise?.let { o.put("sunrise", it.toString()) }
        ex.sun?.sunset?.let { o.put("sunset", it.toString()) }
        writeCache(EXTRAS, o.toString())
    }

    private fun readExtras(): Extras? {
        val raw = readCache(EXTRAS) ?: return null
        return try {
            val o = JSONObject(raw)

            fun num(key: String): Double? = o.optDouble(key).takeUnless { it.isNaN() }

            val sun = if (o.has("sunrise") && o.has("sunset")) {
                try {
                    SunTimes(
                        LocalDateTime.parse(o.getString("sunrise")),
                        LocalDateTime.parse(o.getString("sunset"))
                    )
                } catch (_: Exception) {
                    null
                }
            } else null
            Extras(num("uv"), num("vis"), num("pres"), num("dew"), sun, o.optBoolean("presStation"))
        } catch (_: Exception) {
            null
        }
    }

    private fun mergeExtras(data: WeatherData, ex: Extras): WeatherData {
        val usedExtrasPressure = data.current.pressureInHg == null && ex.pressureInHg != null
        return data.copy(
            current = data.current.copy(
                uvIndex = data.current.uvIndex ?: ex.uvIndex,
                visibilityMi = data.current.visibilityMi ?: ex.visibilityMi,
                pressureInHg = data.current.pressureInHg ?: ex.pressureInHg,
                dewpointF = data.current.dewpointF ?: ex.dewpointF,
                pressureStationLevel = if (usedExtrasPressure) {
                    ex.pressureStationLevel
                } else {
                    data.current.pressureStationLevel
                }
            ),
            sun = data.sun ?: ex.sun
        )
    }

    // ---------------------------------------------------------------- reads

    /** Blocking; call only from IO context. Rebuilds domain data from cached raw responses. */
    private fun loadCachedBlocking(): WeatherData? {
        val place = Prefs.place(context) ?: return null

        // WHY 0L: a missing stamp means an old install (cached before stamps
        // existed). Stamping 0 makes isFresh() report stale so callers force
        // exactly one network refresh instead of replaying forever.
        val fetchedAt = readStamp() ?: 0L

        val daily = readCache(NWS_DAILY)
        val hourly = readCache(NWS_HOURLY)
        if (daily != null && hourly != null) {
            try {
                val bodies = NwsSource.Bodies(daily, hourly, readCache(NWS_OBS), null, null)
                val data = NwsSource.assemble(place, bodies, fetchedAt)
                val ex = readExtras()
                return if (ex != null) mergeExtras(data, ex) else data
            } catch (_: Exception) {
                // fall through to Open-Meteo cache
            }
        }

        val om = readCache(OM_BODY)
        if (om != null) {
            try {
                return OpenMeteoSource.parse(place, om, fetchedAt)
            } catch (_: Exception) {
            }
        }
        return null
    }

    suspend fun loadCached(): WeatherData? = withContext(Dispatchers.IO) {
        // Public readers share the same lock as writers. Internal callers
        // (refreshLocked) use loadCachedBlocking() directly — kotlinx Mutex
        // is not reentrant.
        ioMutex.withLock { loadCachedBlocking() }
    }

    // --------------------------------------------------------------- writes

    suspend fun refresh(force: Boolean = false, budgetMs: Long? = null): RefreshResult =
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                // Computed inside the lock right before work starts, so a
                // caller queued behind another refresh doesn't inherit that
                // wait time as its own network budget.
                val deadlineEpochMs = budgetMs?.let { System.currentTimeMillis() + it }
                refreshLocked(force, deadlineEpochMs)
            }
        }

    private suspend fun refreshLocked(force: Boolean, deadlineEpochMs: Long?): RefreshResult {
        val place = Prefs.place(context)
            ?: return RefreshResult.Failure("No ZIP code set yet.", null)

        if (!force) {
            val cached = loadCachedBlocking()
            // updatedAtEpochMs is the fetch stamp (loadCachedBlocking passes the
            // sidecar value), so this measures age since the actual fetch.
            if (cached != null &&
                isFresh(cached.updatedAtEpochMs, System.currentTimeMillis())
            ) {
                return RefreshResult.Success(cached, fromCache = true)
            }
        }

        // 1) Primary: NWS
        try {
            val bodies = NwsSource.fetchBodies(place, deadlineEpochMs)
            val fetchedAt = System.currentTimeMillis()
            var data = NwsSource.assemble(place, bodies, fetchedAt)
            writeCache(NWS_DAILY, bodies.daily)
            writeCache(NWS_HOURLY, bodies.hourly)
            // Keep last-good METAR when obs was skipped (budget/timeout).
            bodies.obs?.let { writeCache(NWS_OBS, it) }
            deleteCache(OM_BODY)
            // Stamp only after the forecast pair is durably cached; enrichment
            // below is best-effort and must not affect the freshness window.
            writeStamp(fetchedAt)
            Prefs.updateLabels(context, data.place.city, data.place.state)

            // 2) Enrichment (best-effort, never fatal)
            try {
                val ex = OpenMeteoSource.fetchExtras(place, deadlineEpochMs)
                writeExtras(ex)
                data = mergeExtras(data, ex)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            return RefreshResult.Success(data, fromCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // fall through to fallback source
        }

        // 3) Fallback: full Open-Meteo
        try {
            val body = OpenMeteoSource.fetchBody(place, deadlineEpochMs)
            val fetchedAt = System.currentTimeMillis()
            val data = OpenMeteoSource.parse(place, body, fetchedAt)
            writeCache(OM_BODY, body)
            deleteCache(NWS_DAILY)
            deleteCache(NWS_HOURLY)
            deleteCache(NWS_OBS)
            writeStamp(fetchedAt)
            return RefreshResult.Success(data, fromCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RefreshResult.Failure(
                e.message ?: "Could not reach weather services.",
                loadCachedBlocking()
            )
        }
    }

    /** Resolve + persist a new ZIP; clears stale caches so the next refresh is fresh. */
    suspend fun setZip(zip: String): Place = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val place = ZipGeocoder.geocode(zip)
            Prefs.savePlace(context, place)
            clearCaches()
            place
        }
    }

    companion object {
        // WHY process-wide: instances are created per entry point (UI
        // remember{} vs widget applicationContext) but they all hit the same
        // cache files, so the lock must be shared across instances.
        private val ioMutex = Mutex()

        const val STALE_MS = 15 * 60 * 1000L

        /** A cached payload is fresh only when its age is in [0, staleMs). */
        fun isFresh(
            fetchedAtEpochMs: Long,
            nowEpochMs: Long,
            staleMs: Long = STALE_MS
        ): Boolean = nowEpochMs - fetchedAtEpochMs in 0 until staleMs

        private const val NWS_DAILY = "cache_nws_daily.json"
        private const val NWS_HOURLY = "cache_nws_hourly.json"
        private const val NWS_OBS = "cache_nws_obs.json"
        private const val OM_BODY = "cache_openmeteo.json"
        private const val EXTRAS = "cache_extras.json"
        private const val FETCH_STAMP = "cache_fetched_at.txt"
    }
}
