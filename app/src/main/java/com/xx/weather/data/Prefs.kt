package com.xx.weather.data

import android.content.Context
import com.xx.weather.data.model.Place
import com.xx.weather.data.model.Units

/** Tiny SharedPreferences wrapper for ZIP/place/units settings. */
object Prefs {
    private const val FILE = "xx_weather_prefs"
    private const val KEY_ZIP = "zip"
    private const val KEY_CITY = "city"
    private const val KEY_STATE = "state"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_UNITS = "units"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun place(context: Context): Place? {
        val p = sp(context)
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

    fun savePlace(context: Context, place: Place) {
        sp(context).edit()
            .putString(KEY_ZIP, place.zip)
            .putString(KEY_CITY, place.city)
            .putString(KEY_STATE, place.state)
            .putString(KEY_LAT, place.lat.toString())
            .putString(KEY_LON, place.lon.toString())
            .apply()
    }

    /** Refine display labels after a richer source (NWS) resolves them. */
    fun updateLabels(context: Context, city: String, state: String) {
        if (city.isBlank()) return
        sp(context).edit()
            .putString(KEY_CITY, city)
            .putString(KEY_STATE, state)
            .apply()
    }

    fun units(context: Context): Units =
        if (sp(context).getString(KEY_UNITS, "F") == "C") Units.CELSIUS else Units.FAHRENHEIT

    fun setUnits(context: Context, units: Units) {
        sp(context).edit().putString(KEY_UNITS, if (units == Units.CELSIUS) "C" else "F").apply()
    }
}
