package com.xx.weather

import com.xx.weather.data.model.Condition
import com.xx.weather.data.model.Conditions
import com.xx.weather.data.model.Place
import com.xx.weather.data.remote.NwsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NwsSourceTest {

    private val place = Place(zip = "80202", city = "Denver", state = "CO", lat = 39.7392, lon = -104.9903)

    private fun res(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.readBytes().decodeToString()

    @Test
    fun `current pop falls back to max pop of next twelve hourly hours`() {
        // First 12 periods of nws_hourly.json have pops [10,20,30,20,10,40,30,20,10,5,15,25]
        // -> max 40. Hour 13 spikes to 90 and must not win.
        val hourly = res("nws_hourly.json")
        val bodies = NwsSource.Bodies(
            daily = hourly,
            hourly = hourly,
            obs = res("nws_obs_msl.json"),
            city = null,
            state = null
        )
        val data = NwsSource.assemble(place, bodies, nowEpochMs = 1_700_000_000_000L)
        assertEquals("current popPct should be max of first 12 hourly pops", 40, data.current.popPct)
    }

    @Test
    fun `evening only daily date fills high from daytime period`() {
        // A Tonight-only first date must not report loF as hiF.
        val parsed = NwsSource.parseDaily(res("nws_daily_evening.json"))
        assertEquals(2, parsed.size)
        val tonight = parsed.first()
        assertEquals(61.0, tonight.loF, 0.001)
        val hi: Double? = tonight.hiF
        assertTrue(
            "evening-only date must not fall back to lo as hi (got $hi)",
            hi == null || hi != 61.0
        )
        if (hi != null) {
            assertEquals(
                "when filled, the evening-date high must come from the daytime period",
                88.0, hi, 0.001
            )
        }
    }

    @Test
    fun `evening daily date high is backfilled from hourly max at assemble level`() {
        // Pins the assemble() hourly-backfill step end to end: Tonight
        // (2026-08-25) has no daytime period, so its high must equal the max
        // hourly tempF for that local date. nws_hourly.json periods on
        // 2026-08-25 (-06:00) are 91,92,90,88,85,82,78,74,70,66,63 -> max 92.
        val bodies = NwsSource.Bodies(
            daily = res("nws_daily_evening.json"),
            hourly = res("nws_hourly.json"),
            obs = null,
            city = null,
            state = null
        )
        val data = NwsSource.assemble(place, bodies, nowEpochMs = 1_700_000_000_000L)
        assertEquals(92.0, data.daily.first().hiF!!, 0.001)
    }

    @Test
    fun `obs pressure station-level flag set only when sea level pressure missing`() {
        val baroOnly = NwsSource.parseObs(res("nws_obs_baro_only.json"))
        assertTrue("baro-only obs should parse", baroOnly != null)
        assertTrue(
            "station-level flag must be true when only barometricPressure exists",
            baroOnly!!.pressureStationLevel
        )
        val msl = NwsSource.parseObs(res("nws_obs_msl.json"))
        assertTrue("msl obs should parse", msl != null)
        assertTrue(
            "station-level flag must be false when seaLevelPressure exists",
            !msl!!.pressureStationLevel
        )
    }

    @Test
    fun `obs pressure prefers sea level pressure when both present`() {
        val obs = NwsSource.parseObs(res("nws_obs_msl.json"))
        assertTrue("obs should parse", obs != null)
        // seaLevelPressure=101360 Pa -> 29.93 inHg; barometric 84600 Pa would give ~24.98.
        assertEquals(29.93, obs!!.pressureInHg!!, 0.05)
    }

    @Test
    fun `obs with only barometric pressure still parses`() {
        val obs = NwsSource.parseObs(res("nws_obs_baro_only.json"))
        assertTrue("obs should parse with only barometricPressure", obs != null)
        assertEquals(101360.0 / 3386.389, obs!!.pressureInHg!!, 0.05)
    }
}

class NwsIconTest {

    @Test
    fun `dual condition day icon resolves severe token not first sky token`() {
        // sct/tsra_hi,40 — thunderstorm token wins over the leading sky-cover token.
        assertEquals(
            Condition.THUNDERSTORM,
            Conditions.fromNwsIcon("https://api.weather.gov/icons/land/day/sct/tsra_hi,40?size=medium")
        )
    }

    @Test
    fun `dual condition showers_hi icon maps to showers`() {
        assertEquals(
            Condition.SHOWERS,
            Conditions.fromNwsIcon("https://api.weather.gov/icons/land/day/rain_showers_hi,50")
        )
    }

    @Test
    fun `night dual condition thunderstorm icon resolves severe token`() {
        assertEquals(
            Condition.THUNDERSTORM,
            Conditions.fromNwsIcon("https://api.weather.gov/icons/land/night/sct/tsra_hi,40")
        )
    }

    @Test
    fun `wind_skc and wind_sct map to their cloud conditions`() {
        assertEquals(
            Condition.CLEAR,
            Conditions.fromNwsIcon("https://api.weather.gov/icons/land/day/wind_skc")
        )
        assertEquals(
            Condition.PARTLY_CLOUDY,
            Conditions.fromNwsIcon("https://api.weather.gov/icons/land/day/wind_sct")
        )
    }

    @Test
    fun `wind_few wind_bkn wind_ovc map to cloud cover`() {
        assertEquals(
            Condition.MOSTLY_CLEAR,
            Conditions.fromNwsIcon("https://api.weather.gov/icons/land/day/wind_few")
        )
        assertEquals(
            Condition.CLOUDY,
            Conditions.fromNwsIcon("https://api.weather.gov/icons/land/day/wind_bkn")
        )
        assertEquals(
            Condition.OVERCAST,
            Conditions.fromNwsIcon("https://api.weather.gov/icons/land/day/wind_ovc")
        )
    }
}
