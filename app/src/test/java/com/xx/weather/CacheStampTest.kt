package com.xx.weather

import com.xx.weather.data.WeatherRepository
import com.xx.weather.data.model.Place
import com.xx.weather.data.remote.NwsSource
import com.xx.weather.data.remote.OpenMeteoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CacheStampTest {

    private val place = Place(zip = "80202", city = "Denver", state = "CO", lat = 39.7392, lon = -104.9903)

    private fun res(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.readBytes().decodeToString()

    @Test
    fun `nws assemble stamps updatedAtEpochMs from fixed parameter`() {
        val hourly = res("nws_hourly.json")
        val bodies = NwsSource.Bodies(
            daily = hourly,
            hourly = hourly,
            obs = null,
            city = "Denver",
            state = "CO"
        )
        val now = 1_700_000_000_000L
        val data = NwsSource.assemble(place, bodies, nowEpochMs = now)
        assertEquals(now, data.updatedAtEpochMs)
    }

    @Test
    fun `openmeteo parse stamps updatedAtEpochMs from fixed parameter`() {
        val now = 1_700_000_123_000L
        val data = OpenMeteoSource.parse(place, res("om_current_m.json"), nowEpochMs = now)
        assertEquals(now, data.updatedAtEpochMs)
    }

    @Test
    fun `isFresh is true fourteen minutes after fetch`() {
        val stamp = 1_700_000_000_000L
        assertEquals(true, WeatherRepository.isFresh(stamp, stamp + 14 * 60_000L))
    }

    @Test
    fun `isFresh is false sixteen minutes after fetch`() {
        val stamp = 1_700_000_000_000L
        assertEquals(false, WeatherRepository.isFresh(stamp, stamp + 16 * 60_000L))
    }

    @Test
    fun `isFresh is true at zero elapsed`() {
        val stamp = 1_700_000_000_000L
        assertEquals(true, WeatherRepository.isFresh(stamp, stamp))
    }

    @Test
    fun `isFresh treats epoch-zero missing stamp as stale`() {
        assertFalse(WeatherRepository.isFresh(0L, 1_700_000_000_000L))
    }
}
