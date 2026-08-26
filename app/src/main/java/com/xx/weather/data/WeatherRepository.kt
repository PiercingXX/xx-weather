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
 *
 * Cache files are prefixed by ZIP so multiple saved locations do not clobber
 * each other.
 */
class WeatherRepository(private val context: Context) {

    sealed interface RefreshResult {
        data class Success(val data: WeatherData, val fromCache: Boolean) : RefreshResult
        data class Failure(val message: String, val stale: WeatherData?) : RefreshResult
    }

    // ---------------------------------------------------------------- cache

    private fun cacheFile(zip: String, name: String) = File(context.filesDir, "${zip}_$name")

    private fun migrateLegacyCaches(zip: String) {
        val dest = cacheFile(zip, NWS_DAILY)
        val src = File(context.filesDir, NWS_DAILY)
        if (dest.exists() || !src.exists()) return
        listOf(NWS_DAILY, NWS_HOURLY, NWS_OBS, OM_BODY, EXTRAS, FETCH_STAMP).forEach { name ->
            val from = File(context.filesDir, name)
            if (from.exists()) from.renameTo(cacheFile(zip, name))
        }
    }

    private fun readCache(zip: String, name: String): String? = try {
        cacheFile(zip, name).takeIf { it.exists() }?.readText()
    } catch (_: Exception) {
        null
    }

    private fun writeCache(zip: String, name: String, body: String) {
        val tmp = File(context.filesDir, "${zip}_$name.${System.nanoTime()}.tmp")
        try {
            tmp.writeText(body)
            if (!tmp.renameTo(cacheFile(zip, name))) tmp.delete()
        } catch (_: Exception) {
            tmp.delete()
        }
    }

    private fun deleteCache(zip: String, name: String) {
        try {
            cacheFile(zip, name).delete()
        } catch (_: Exception) {
        }
    }

    private fun clearCaches(zip: String) {
        listOf(NWS_DAILY, NWS_HOURLY, NWS_OBS, OM_BODY, EXTRAS, FETCH_STAMP).forEach {
            deleteCache(zip, it)
        }
    }

    private fun writeStamp(zip: String, fetchedAtEpochMs: Long) {
        writeCache(zip, FETCH_STAMP, fetchedAtEpochMs.toString())
    }

    private fun readStamp(zip: String): Long? =
        readCache(zip, FETCH_STAMP)?.trim()?.toLongOrNull()

    private fun writeExtras(zip: String, ex: Extras) {
        val o = JSONObject()
        ex.uvIndex?.let { o.put("uv", it) }
        ex.visibilityMi?.let { o.put("vis", it) }
        ex.pressureInHg?.let { o.put("pres", it) }
        ex.dewpointF?.let { o.put("dew", it) }
        if (ex.pressureStationLevel) o.put("presStation", true)
        ex.sun?.sunrise?.let { o.put("sunrise", it.toString()) }
        ex.sun?.sunset?.let { o.put("sunset", it.toString()) }
        writeCache(zip, EXTRAS, o.toString())
    }

    private fun readExtras(zip: String): Extras? {
        val raw = readCache(zip, EXTRAS) ?: return null
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

    private fun loadCachedBlocking(place: Place): WeatherData? {
        migrateLegacyCaches(place.zip)
        val fetchedAt = readStamp(place.zip) ?: 0L

        val daily = readCache(place.zip, NWS_DAILY)
        val hourly = readCache(place.zip, NWS_HOURLY)
        if (daily != null && hourly != null) {
            try {
                val bodies = NwsSource.Bodies(daily, hourly, readCache(place.zip, NWS_OBS), null, null)
                val data = NwsSource.assemble(place, bodies, fetchedAt)
                val ex = readExtras(place.zip)
                return if (ex != null) mergeExtras(data, ex) else data
            } catch (_: Exception) {
            }
        }

        val om = readCache(place.zip, OM_BODY)
        if (om != null) {
            try {
                return OpenMeteoSource.parse(place, om, fetchedAt)
            } catch (_: Exception) {
            }
        }
        return null
    }

    suspend fun loadCached(place: Place? = null): WeatherData? = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val p = place ?: Prefs.place(context) ?: return@withLock null
            loadCachedBlocking(p)
        }
    }

    // --------------------------------------------------------------- writes

    suspend fun refresh(
        force: Boolean = false,
        budgetMs: Long? = null,
        place: Place? = null
    ): RefreshResult = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val deadlineEpochMs = budgetMs?.let { System.currentTimeMillis() + it }
            val p = place ?: Prefs.place(context)
                ?: return@withLock RefreshResult.Failure("No ZIP code set yet.", null)
            refreshLocked(p, force, deadlineEpochMs)
        }
    }

    private suspend fun refreshLocked(
        place: Place,
        force: Boolean,
        deadlineEpochMs: Long?
    ): RefreshResult {
        migrateLegacyCaches(place.zip)

        if (!force) {
            val cached = loadCachedBlocking(place)
            if (cached != null &&
                isFresh(cached.updatedAtEpochMs, System.currentTimeMillis())
            ) {
                return RefreshResult.Success(cached, fromCache = true)
            }
        }

        try {
            val bodies = NwsSource.fetchBodies(place, deadlineEpochMs)
            val fetchedAt = System.currentTimeMillis()
            var data = NwsSource.assemble(place, bodies, fetchedAt)
            writeCache(place.zip, NWS_DAILY, bodies.daily)
            writeCache(place.zip, NWS_HOURLY, bodies.hourly)
            bodies.obs?.let { writeCache(place.zip, NWS_OBS, it) }
            deleteCache(place.zip, OM_BODY)
            writeStamp(place.zip, fetchedAt)
            Prefs.updateLabels(context, place.zip, data.place.city, data.place.state)

            try {
                val ex = OpenMeteoSource.fetchExtras(place, deadlineEpochMs)
                writeExtras(place.zip, ex)
                data = mergeExtras(data, ex)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            return RefreshResult.Success(data, fromCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        try {
            val body = OpenMeteoSource.fetchBody(place, deadlineEpochMs)
            val fetchedAt = System.currentTimeMillis()
            val data = OpenMeteoSource.parse(place, body, fetchedAt)
            writeCache(place.zip, OM_BODY, body)
            deleteCache(place.zip, NWS_DAILY)
            deleteCache(place.zip, NWS_HOURLY)
            deleteCache(place.zip, NWS_OBS)
            writeStamp(place.zip, fetchedAt)
            return RefreshResult.Success(data, fromCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RefreshResult.Failure(
                e.message ?: "Could not reach weather services.",
                loadCachedBlocking(place)
            )
        }
    }

    /** Geocode and persist a ZIP. Existing locations are kept; duplicates select. */
    suspend fun addZip(zip: String): Place = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val place = ZipGeocoder.geocode(zip)
            val existed = Prefs.places(context).any { it.zip == place.zip }
            Prefs.addPlace(context, place)
            if (!existed) clearCaches(place.zip)
            place
        }
    }

    suspend fun removeZip(zip: String) = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            Prefs.removePlace(context, zip)
            clearCaches(zip)
        }
    }

    companion object {
        // Process-wide: UI and widgets construct separate instances that share files.
        private val ioMutex = Mutex()

        const val STALE_MS = 15 * 60 * 1000L

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
