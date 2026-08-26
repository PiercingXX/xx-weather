package com.xx.weather.data

import com.xx.weather.data.model.Place
import org.json.JSONObject
import java.io.IOException

/**
 * ZIP code → lat/lon via api.zippopotam.us (free, keyless).
 * Results are cached by the caller in [Prefs]; failures here only matter
 * when the user changes their ZIP.
 */
object ZipGeocoder {

    fun geocode(rawZip: String): Place {
        val zip = rawZip.trim()
        require(zip.matches(Regex("\\d{5}"))) { "Enter a valid 5-digit ZIP code." }
        val body = try {
            Http.get("https://api.zippopotam.us/us/$zip")
        } catch (e: java.io.IOException) {
            throw java.io.IOException("Couldn't look up ZIP $zip — check the code and your connection.")
        }
        val places = JSONObject(body).optJSONArray("places")
            ?: throw IOException("ZIP code not found: $zip")
        if (places.length() == 0) throw IOException("ZIP code not found: $zip")
        val p = places.getJSONObject(0)
        val lat = p.optString("latitude").toDoubleOrNull()
            ?: throw IOException("Bad geocode response for $zip")
        val lon = p.optString("longitude").toDoubleOrNull()
            ?: throw IOException("Bad geocode response for $zip")
        return Place(
            zip = zip,
            city = p.optString("place name").ifBlank { zip },
            state = p.optString("state"),
            lat = lat,
            lon = lon
        )
    }
}
