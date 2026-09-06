package com.xx.weather.data

import android.content.Context
import com.xx.weather.data.model.Place
import com.xx.weather.data.model.Units
import org.json.JSONArray
import org.json.JSONObject

/** Encode/decode the saved-location list. Pure so JVM tests can pin it. */
object PlacesCodec {

    fun encode(places: List<Place>): String {
        val arr = JSONArray()
        for (p in places) {
            arr.put(
                JSONObject()
                    .put("zip", p.zip)
                    .put("city", p.city)
                    .put("state", p.state)
                    .put("lat", p.lat)
                    .put("lon", p.lon)
            )
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<Place> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val zip = o.optString("zip")
                if (!zip.matches(Regex("\\d{5}"))) return@mapNotNull null
                val lat = o.optDouble("lat").takeUnless { it.isNaN() } ?: return@mapNotNull null
                val lon = o.optDouble("lon").takeUnless { it.isNaN() } ?: return@mapNotNull null
                Place(
                    zip = zip,
                    city = o.optString("city").ifBlank { zip },
                    state = o.optString("state"),
                    lat = lat,
                    lon = lon
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** SharedPreferences for locations, selected ZIP, units, alerts, and list/detail mode. */
object Prefs {
    private const val FILE = "xx_weather_prefs"
    private const val KEY_ZIP = "zip"
    private const val KEY_CITY = "city"
    private const val KEY_STATE = "state"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_UNITS = "units"
    private const val KEY_PLACES = "places_json"
    private const val KEY_SELECTED_ZIP = "selected_zip"
    private const val KEY_COLLAPSED = "collapsed"
    private const val KEY_ALERTS_ENABLED = "alerts_enabled"
    private const val KEY_ALERTS_JSON = "alerts_json"
    private const val KEY_ALERT_DEDUPE = "alert_dedupe"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun legacyPlace(p: android.content.SharedPreferences): Place? {
        val zip = p.getString(KEY_ZIP, null) ?: return null
        val lat = p.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = p.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
        return Place(
            zip = zip,
            city = p.getString(KEY_CITY, "").orEmpty().ifEmpty { zip },
            state = p.getString(KEY_STATE, "").orEmpty(),
            lat = lat,
            lon = lon
        )
    }

    fun places(context: Context): List<Place> {
        val p = sp(context)
        val stored = PlacesCodec.decode(p.getString(KEY_PLACES, null))
        if (stored.isNotEmpty()) return stored
        val one = legacyPlace(p) ?: return emptyList()
        savePlaces(context, listOf(one))
        setSelectedZip(context, one.zip)
        return listOf(one)
    }

    fun savePlaces(context: Context, places: List<Place>) {
        sp(context).edit().putString(KEY_PLACES, PlacesCodec.encode(places)).apply()
    }

    /** Currently selected location, or the first saved one. Widgets use this. */
    fun place(context: Context): Place? {
        val all = places(context)
        if (all.isEmpty()) return null
        val sel = selectedZip(context)
        return all.find { it.zip == sel } ?: all.first()
    }

    fun selectedZip(context: Context): String? {
        val all = PlacesCodec.decode(sp(context).getString(KEY_PLACES, null))
        val sel = sp(context).getString(KEY_SELECTED_ZIP, null)
        if (sel != null && all.any { it.zip == sel }) return sel
        return all.firstOrNull()?.zip ?: legacyPlace(sp(context))?.zip
    }

    fun setSelectedZip(context: Context, zip: String?) {
        val e = sp(context).edit()
        if (zip.isNullOrBlank()) e.remove(KEY_SELECTED_ZIP) else e.putString(KEY_SELECTED_ZIP, zip)
        e.apply()
    }

    fun addPlace(context: Context, place: Place) {
        val current = places(context)
        val next = if (current.any { it.zip == place.zip }) {
            current.map { if (it.zip == place.zip) place else it }
        } else {
            current + place
        }
        savePlaces(context, next)
        setSelectedZip(context, place.zip)
    }

    fun removePlace(context: Context, zip: String) {
        val next = places(context).filter { it.zip != zip }
        savePlaces(context, next)
        if (selectedZip(context) == zip) {
            setSelectedZip(context, next.firstOrNull()?.zip)
        }
        removeAlertTriggers(context, zip)
    }

    fun updateLabels(context: Context, zip: String, city: String, state: String) {
        if (city.isBlank()) return
        val next = places(context).map {
            if (it.zip == zip) it.copy(city = city, state = state) else it
        }
        savePlaces(context, next)
    }

    fun collapsed(context: Context): Boolean =
        sp(context).getBoolean(KEY_COLLAPSED, false)

    fun setCollapsed(context: Context, collapsed: Boolean) {
        sp(context).edit().putBoolean(KEY_COLLAPSED, collapsed).apply()
    }

    fun units(context: Context): Units =
        if (sp(context).getString(KEY_UNITS, "F") == "C") Units.CELSIUS else Units.FAHRENHEIT

    fun setUnits(context: Context, units: Units) {
        sp(context).edit().putString(KEY_UNITS, if (units == Units.CELSIUS) "C" else "F").apply()
    }

    fun alertsEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_ALERTS_ENABLED, false)

    fun setAlertsEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_ALERTS_ENABLED, enabled).apply()
    }

    fun alertTriggers(context: Context, zip: String): ZipAlertTriggers? =
        ZipAlertCodec.decode(sp(context).getString(KEY_ALERTS_JSON, null))[zip]

    fun setAlertTriggers(context: Context, zip: String, triggers: ZipAlertTriggers) {
        val next = ZipAlertCodec.decode(sp(context).getString(KEY_ALERTS_JSON, null)).toMutableMap()
        next[zip] = triggers
        sp(context).edit().putString(KEY_ALERTS_JSON, ZipAlertCodec.encode(next)).apply()
    }

    fun ensureAlertTriggers(context: Context, zip: String): ZipAlertTriggers {
        alertTriggers(context, zip)?.let { return it }
        val defaults = ZipAlertTriggers(precipEnabled = true)
        setAlertTriggers(context, zip, defaults)
        return defaults
    }

    fun removeAlertTriggers(context: Context, zip: String) {
        val current = ZipAlertCodec.decode(sp(context).getString(KEY_ALERTS_JSON, null))
        if (zip !in current) return
        val next = current - zip
        val e = sp(context).edit()
        if (next.isEmpty()) e.remove(KEY_ALERTS_JSON) else e.putString(KEY_ALERTS_JSON, ZipAlertCodec.encode(next))
        e.apply()
    }

    fun alertDedupeKeys(context: Context): Set<String> =
        sp(context).getStringSet(KEY_ALERT_DEDUPE, null)?.toSet() ?: emptySet()

    fun addAlertDedupeKeys(context: Context, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val next = AlertEvaluator.rememberKeys(alertDedupeKeys(context), keys)
        sp(context).edit().putStringSet(KEY_ALERT_DEDUPE, next).apply()
    }
}
