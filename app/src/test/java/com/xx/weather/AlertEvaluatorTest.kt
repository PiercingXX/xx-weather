package com.xx.weather

import com.xx.weather.data.AlertEvaluator
import com.xx.weather.data.ZipAlertCodec
import com.xx.weather.data.ZipAlertTriggers
import com.xx.weather.data.model.Condition
import com.xx.weather.data.model.HourlyPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class AlertEvaluatorTest {

    private val zone = ZoneOffset.UTC
    private val nowLdt = LocalDateTime.of(2026, 1, 15, 12, 0)
    private val nowMs = nowLdt.atZone(zone).toInstant().toEpochMilli()
    private val window = 1_700_000_000_000L
    private val zip = "80202"

    private fun hour(offsetHours: Long, pop: Int?, temp: Double = 40.0, cond: Condition = Condition.CLOUDY) =
        HourlyPoint(nowLdt.plusHours(offsetHours), temp, pop, cond, true)

    private fun eval(
        hourly: List<HourlyPoint>,
        tempF: Double = 40.0,
        triggers: ZipAlertTriggers,
        notified: Set<String> = emptySet(),
        fetchedAt: Long = window,
    ) = AlertEvaluator.evaluate(
        zip = zip,
        city = "Denver",
        hourly = hourly,
        currentTempF = tempF,
        fetchedAtEpochMs = fetchedAt,
        nowEpochMs = nowMs,
        triggers = triggers,
        alreadyNotified = notified,
        zone = zone,
    )

    @Test
    fun `precip PoP above threshold in the next N hours fires`() {
        val hourly = (0L..11L).map { h -> hour(h, if (h == 2L) 70 else 10) }
        val fires = eval(hourly, triggers = ZipAlertTriggers(precipEnabled = true, precipHours = 6))
        assertEquals(1, fires.size)
        assertEquals(AlertEvaluator.Kind.PRECIP, fires[0].kind)
        assertEquals(zip, fires[0].zip)
        assertEquals(window, fires[0].window)
        assertEquals("80202|PRECIP|$window", fires[0].key)
        assertTrue(fires[0].text.contains("6 hours"))
        assertTrue(fires[0].text.contains("70%"))
    }

    @Test
    fun `low PoP in the window does not fire`() {
        val hourly = (0L..11L).map { h -> hour(h, 20) }
        val fires = eval(hourly, triggers = ZipAlertTriggers(precipEnabled = true, precipHours = 6))
        assertTrue(fires.isEmpty())
    }

    @Test
    fun `PoP outside the N-hour window does not fire`() {
        val hourly = (0L..11L).map { h -> hour(h, if (h == 8L) 90 else 10) }
        val fires = eval(hourly, triggers = ZipAlertTriggers(precipEnabled = true, precipHours = 6))
        assertTrue(fires.isEmpty())
    }

    @Test
    fun `PoP of exactly 50 percent fires`() {
        val hourly = (0L..5L).map { h -> hour(h, if (h == 0L) 50 else 0) }
        val fires = eval(hourly, triggers = ZipAlertTriggers(precipEnabled = true, precipHours = 6))
        assertEquals(1, fires.size)
    }

    @Test
    fun `rain condition with null PoP still fires`() {
        val hourly = listOf(
            hour(0, null, cond = Condition.RAIN),
            hour(1, null, cond = Condition.CLOUDY),
        )
        val fires = eval(hourly, triggers = ZipAlertTriggers(precipEnabled = true, precipHours = 6))
        assertEquals(1, fires.size)
        assertEquals(AlertEvaluator.Kind.PRECIP, fires[0].kind)
    }

    @Test
    fun `empty hourly does not fire precip`() {
        val fires = eval(emptyList(), triggers = ZipAlertTriggers(precipEnabled = true))
        assertTrue(fires.isEmpty())
    }

    @Test
    fun `precip disabled ignores high PoP`() {
        val hourly = (0L..5L).map { hour(it, 90) }
        val fires = eval(hourly, triggers = ZipAlertTriggers(precipEnabled = false, tempEnabled = false))
        assertTrue(fires.isEmpty())
    }

    @Test
    fun `temperature at or below threshold fires`() {
        val fires = eval(
            emptyList(),
            tempF = 28.0,
            triggers = ZipAlertTriggers(tempEnabled = true, tempAtOrBelowF = 32),
        )
        assertEquals(1, fires.size)
        assertEquals(AlertEvaluator.Kind.TEMP, fires[0].kind)
        assertTrue(fires[0].text.contains("28°F"))
        assertTrue(fires[0].text.contains("32°F"))
    }

    @Test
    fun `temperature exactly at threshold fires`() {
        val fires = eval(
            emptyList(),
            tempF = 32.0,
            triggers = ZipAlertTriggers(tempEnabled = true, tempAtOrBelowF = 32),
        )
        assertEquals(1, fires.size)
    }

    @Test
    fun `temperature above threshold does not fire`() {
        val fires = eval(
            emptyList(),
            tempF = 33.0,
            triggers = ZipAlertTriggers(tempEnabled = true, tempAtOrBelowF = 32),
        )
        assertTrue(fires.isEmpty())
    }

    @Test
    fun `temp disabled ignores freeze`() {
        val fires = eval(
            emptyList(),
            tempF = 10.0,
            triggers = ZipAlertTriggers(tempEnabled = false),
        )
        assertTrue(fires.isEmpty())
    }

    @Test
    fun `both triggers can fire in one window`() {
        val hourly = (0L..5L).map { hour(it, 80) }
        val fires = eval(
            hourly,
            tempF = 20.0,
            triggers = ZipAlertTriggers(
                precipEnabled = true,
                precipHours = 6,
                tempEnabled = true,
                tempAtOrBelowF = 32,
            ),
        )
        assertEquals(setOf(AlertEvaluator.Kind.PRECIP, AlertEvaluator.Kind.TEMP), fires.map { it.kind }.toSet())
        assertEquals(2, fires.size)
    }

    @Test
    fun `dedupe suppresses the same zip trigger window`() {
        val hourly = (0L..5L).map { hour(it, 80) }
        val key = AlertEvaluator.dedupeKey(zip, AlertEvaluator.Kind.PRECIP, window)
        val fires = eval(
            hourly,
            tempF = 20.0,
            triggers = ZipAlertTriggers(precipEnabled = true, tempEnabled = true, tempAtOrBelowF = 32),
            notified = setOf(key),
        )
        assertEquals(1, fires.size)
        assertEquals(AlertEvaluator.Kind.TEMP, fires[0].kind)
    }

    @Test
    fun `new fetch window can notify again`() {
        val hourly = (0L..5L).map { hour(it, 80) }
        val oldKey = AlertEvaluator.dedupeKey(zip, AlertEvaluator.Kind.PRECIP, window)
        val fires = eval(
            hourly,
            triggers = ZipAlertTriggers(precipEnabled = true),
            notified = setOf(oldKey),
            fetchedAt = window + 1,
        )
        assertEquals(1, fires.size)
        assertEquals(window + 1, fires[0].window)
        assertFalse(fires[0].key == oldKey)
    }

    @Test
    fun `rememberKeys keeps insertion order and drops the oldest past the limit`() {
        val kept = AlertEvaluator.rememberKeys(
            already = setOf("a", "b"),
            newKeys = listOf("c", "d"),
            limit = 3,
        )
        assertEquals(setOf("b", "c", "d"), kept)
        assertFalse("a" in kept)
    }

    @Test
    fun `default precip hours is 6`() {
        assertEquals(6, AlertEvaluator.DEFAULT_PRECIP_HOURS)
        assertEquals(6, ZipAlertTriggers().precipHours)
    }
}

class ZipAlertCodecTest {

    @Test
    fun `round trip preserves per-ZIP triggers`() {
        val original = mapOf(
            "80202" to ZipAlertTriggers(precipEnabled = true, precipHours = 6, tempEnabled = false, tempAtOrBelowF = 32),
            "05751" to ZipAlertTriggers(precipEnabled = false, precipHours = 3, tempEnabled = true, tempAtOrBelowF = 20),
        )
        val decoded = ZipAlertCodec.decode(ZipAlertCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `blank and invalid payloads are empty`() {
        assertTrue(ZipAlertCodec.decode(null).isEmpty())
        assertTrue(ZipAlertCodec.decode("").isEmpty())
        assertTrue(ZipAlertCodec.decode("not-json").isEmpty())
        assertTrue(ZipAlertCodec.decode("{\"12\":{\"precip\":true}}").isEmpty())
    }
}
