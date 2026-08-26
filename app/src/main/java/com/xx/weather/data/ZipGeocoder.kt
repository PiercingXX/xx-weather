package com.xx.weather.data

import com.xx.weather.data.model.Place
import org.json.JSONObject
import java.io.IOException

/**
 * ZIP code → lat/lon.
 *
 * zippopotam.us `/us/` does not cover US territories, so Virgin Islands
 * (008xx) and Puerto Rico (006/007/009) miss if we only hit that path.
 * USVI ZIPs are resolved from a local table first (offline, no API gaps);
 * remaining codes try zippopotam country endpoints in order.
 */
object ZipGeocoder {

    private val ZIP_RE = Regex("\\d{5}")

    fun normalize(raw: String): String {
        val zip = raw.trim()
        require(zip.matches(ZIP_RE)) { "Enter a valid 5-digit ZIP code." }
        return zip
    }

    fun localPlace(zip: String): Place? = TERRITORY_ZIPS[zip]

    fun countriesFor(zip: String): List<String> = when {
        zip.startsWith("008") -> listOf("vi", "us")
        zip.startsWith("006") || zip.startsWith("007") || zip.startsWith("009") ->
            listOf("pr", "us")
        else -> listOf("us")
    }

    fun geocode(raw: String): Place {
        val zip = normalize(raw)
        localPlace(zip)?.let { return it }
        var lastError: Exception? = null
        for (country in countriesFor(zip)) {
            try {
                return parseZippopotam(Http.get("https://api.zippopotam.us/$country/$zip"), zip)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException(
            lastError?.message ?: "Couldn't look up ZIP $zip — check the code and your connection.",
        )
    }

    fun parseZippopotam(body: String, zip: String): Place {
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
            state = p.optString("state abbreviation").ifBlank {
                p.optString("state")
            },
            lat = lat,
            lon = lon,
        )
    }

    /**
     * USPS ZIP centroids for the U.S. Virgin Islands. zippopotam `/us/` 404s
     * these; NWS San Juan still forecasts them once we have lat/lon.
     */
    private val TERRITORY_ZIPS: Map<String, Place> = listOf(
        vi("00801", "Charlotte Amalie", 18.3419, -64.9307),
        vi("00802", "Charlotte Amalie", 18.3419, -64.9307),
        vi("00803", "Charlotte Amalie", 18.3419, -64.9307),
        vi("00804", "Charlotte Amalie", 18.3419, -64.9307),
        vi("00805", "Charlotte Amalie", 18.3381, -64.8941),
        vi("00820", "Christiansted", 17.7466, -64.7032),
        vi("00821", "Christiansted", 17.7466, -64.7032),
        vi("00822", "Christiansted", 17.7466, -64.7032),
        vi("00823", "Christiansted", 17.7466, -64.7032),
        vi("00824", "Christiansted", 17.7466, -64.7032),
        vi("00830", "Cruz Bay", 18.3313, -64.7956),
        vi("00831", "Cruz Bay", 18.3313, -64.7956),
        vi("00840", "Frederiksted", 17.7125, -64.8819),
        vi("00841", "Frederiksted", 17.7125, -64.8819),
        vi("00850", "Kingshill", 17.7097, -64.7986),
        vi("00851", "Kingshill", 17.7097, -64.7986),
    ).associateBy { it.zip }

    private fun vi(zip: String, city: String, lat: Double, lon: Double): Place =
        Place(zip = zip, city = city, state = "VI", lat = lat, lon = lon)
}
