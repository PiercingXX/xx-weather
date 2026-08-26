package com.xx.weather

import com.xx.weather.data.SunCalc
import com.xx.weather.data.model.Condition
import com.xx.weather.data.model.Conditions
import com.xx.weather.data.model.Units
import com.xx.weather.ui.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SunCalcTest {

    @Test
    fun `denver summer solstice has plausible sun times`() {
        // Denver: 39.7392 N, -104.9903 W. June 21 2026.
        val times = SunCalc.timesUtc(LocalDate.of(2026, 6, 21), 39.7392, -104.9903)
        assertNotNull("Sun times should exist for Denver", times)
        val (riseMs, setMs) = times!!
        val rise = Instant.ofEpochMilli(riseMs).atZone(ZoneOffset.UTC)
        val set = Instant.ofEpochMilli(setMs).atZone(ZoneOffset.UTC)

        // Sunrise ≈ 5:31 AM MDT = 11:31 UTC (allow generous window)
        assertTrue("sunrise too early: $rise", rise.hour >= 10)
        assertTrue("sunrise too late: $rise", rise.hour <= 13)
        // Sunset ≈ 8:28 PM MDT = 02:28 UTC next day
        val setHourNextDay = if (set.dayOfMonth == 22) set.hour + 24 else set.hour
        assertTrue("sunset implausible: $set", setHourNextDay in 24..28)
    }

    @Test
    fun `polar night returns null`() {
        // Longyearbyen mid-January: polar night
        assertEquals(null, SunCalc.timesUtc(LocalDate.of(2026, 1, 10), 78.22, 15.65))
    }
}

class ConditionsTest {

    @Test
    fun `wmo codes map correctly`() {
        assertEquals(Condition.CLEAR, Conditions.fromWmo(0))
        assertEquals(Condition.OVERCAST, Conditions.fromWmo(3))
        assertEquals(Condition.FOG, Conditions.fromWmo(45))
        assertEquals(Condition.THUNDERSTORM, Conditions.fromWmo(95))
        assertEquals(Condition.HEAVY_SNOW, Conditions.fromWmo(75))
    }

    @Test
    fun `nws icon urls map correctly`() {
        assertEquals(
            Condition.THUNDERSTORM,
            Conditions.fromNwsIcon("https://api.weather.gov/icons/land/day/tsra_hi,40?size=medium")
        )
        assertEquals(
            Condition.PARTLY_CLOUDY,
            Conditions.fromNwsIcon("https://api.weather.gov/icons/land/night/sct")
        )
    }

    @Test
    fun `nws text fallback maps correctly`() {
        assertEquals(Condition.MOSTLY_CLEAR, Conditions.fromNwsText("Mostly Sunny"))
        assertEquals(Condition.SHOWERS, Conditions.fromNwsText("Slight Chance Rain Showers"))
        assertEquals(Condition.SLEET, Conditions.fromNwsText("Rain And Snow likely"))
    }
}

class FmtTest {

    @Test
    fun `wind cardinal directions`() {
        assertEquals("N", Fmt.windDir(0))
        assertEquals("N", Fmt.windDir(350))
        assertEquals("E", Fmt.windDir(90))
        assertEquals("SW", Fmt.windDir(225))
        assertEquals("NNW", Fmt.windDir(337))
    }

    @Test
    fun `temperature conversion`() {
        assertEquals(0, Math.round(Fmt.convert(32.0, Units.CELSIUS)))
        assertEquals(100, Math.round(Fmt.convert(212.0, Units.CELSIUS)))
        assertEquals(212.0, Fmt.convert(212.0, Units.FAHRENHEIT), 0.001)
    }

    @Test
    fun `uv labels`() {
        assertEquals("Low", Fmt.uvLabel(1.5))
        assertEquals("Moderate", Fmt.uvLabel(5.0))
        assertEquals("Very High", Fmt.uvLabel(10.0))
        assertEquals("Extreme", Fmt.uvLabel(11.5))
    }
}
