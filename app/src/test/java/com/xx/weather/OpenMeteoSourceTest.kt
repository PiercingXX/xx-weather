package com.xx.weather

import com.xx.weather.data.model.Place
import com.xx.weather.data.remote.OpenMeteoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoSourceTest {

    private val place = Place(zip = "80202", city = "Denver", state = "CO", lat = 39.7392, lon = -104.9903)

    private fun res(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.readBytes().decodeToString()

    @Test
    fun `visibility reported in meters converts to miles`() {
        // current_units.visibility = "m"; 57600 m = 35.79 mi.
        val data = OpenMeteoSource.parse(place, res("om_current_m.json"), nowEpochMs = 1_700_000_000_000L)
        assertEquals(35.8, data.current.visibilityMi!!, 0.05)
    }

    @Test
    fun `visibility reported in feet converts to miles`() {
        // current_units.visibility = "ft"; 188976 ft = 35.79 mi.
        val data = OpenMeteoSource.parse(place, res("om_current_ft.json"), nowEpochMs = 1_700_000_000_000L)
        assertEquals(35.8, data.current.visibilityMi!!, 0.05)
    }

    @Test
    fun `pressure prefers pressure_msl over surface_pressure`() {
        val data = OpenMeteoSource.parse(place, res("om_current_m.json"), nowEpochMs = 1_700_000_000_000L)
        assertEquals(29.93, data.current.pressureInHg!!, 0.05)
        assertFalse(data.current.pressureStationLevel)
    }

    @Test
    fun `forecast url requests pressure_msl`() {
        assertTrue(OpenMeteoSource.forecastUrl(place).contains("pressure_msl"))
    }

    @Test
    fun `surface pressure only is flagged station level`() {
        val data = OpenMeteoSource.parse(
            place,
            res("om_current_surface_only.json"),
            nowEpochMs = 1_700_000_000_000L
        )
        assertEquals(846.5 * 0.02953, data.current.pressureInHg!!, 0.05)
        assertTrue(data.current.pressureStationLevel)
    }
}
