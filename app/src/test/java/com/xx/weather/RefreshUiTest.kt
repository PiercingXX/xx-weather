package com.xx.weather

import com.xx.weather.data.WeatherRepository
import com.xx.weather.data.model.Condition
import com.xx.weather.data.model.CurrentConditions
import com.xx.weather.data.model.Place
import com.xx.weather.data.model.WeatherData
import com.xx.weather.ui.ZipRefreshState
import com.xx.weather.ui.applyRefreshResult
import com.xx.weather.ui.shouldRefreshOnStartTick
import com.xx.weather.ui.zipShowsSpinner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshUiTest {

    private val zip = "00802"
    private val sample = sampleData(zip)

    @Test
    fun `failure without stale inserts a null placeholder and stops the spinner`() {
        val next = applyRefreshResult(
            ZipRefreshState(),
            zip,
            WeatherRepository.RefreshResult.Failure("offline", stale = null),
        )
        assertTrue(zip in next.dataByZip)
        assertNull(next.dataByZip[zip])
        assertEquals("offline", next.errors[zip])
        assertFalse(zipShowsSpinner(zip, next.dataByZip, next.errors))
    }

    @Test
    fun `failure with stale keeps last-good data`() {
        val next = applyRefreshResult(
            ZipRefreshState(),
            zip,
            WeatherRepository.RefreshResult.Failure("offline", stale = sample),
        )
        assertEquals(sample, next.dataByZip[zip])
        assertEquals("offline", next.errors[zip])
        assertFalse(zipShowsSpinner(zip, next.dataByZip, next.errors))
    }

    @Test
    fun `failure without stale does not clobber in-memory data`() {
        val start = ZipRefreshState(dataByZip = mapOf(zip to sample))
        val next = applyRefreshResult(
            start,
            zip,
            WeatherRepository.RefreshResult.Failure("offline", stale = null),
        )
        assertEquals(sample, next.dataByZip[zip])
        assertEquals("offline", next.errors[zip])
    }

    @Test
    fun `success replaces placeholder and clears the error`() {
        val failed = applyRefreshResult(
            ZipRefreshState(),
            zip,
            WeatherRepository.RefreshResult.Failure("offline", stale = null),
        )
        val next = applyRefreshResult(
            failed,
            zip,
            WeatherRepository.RefreshResult.Success(sample, fromCache = false),
        )
        assertEquals(sample, next.dataByZip[zip])
        assertTrue(next.errors.isEmpty())
        assertFalse(zipShowsSpinner(zip, next.dataByZip, next.errors))
    }

    @Test
    fun `missing zip still spins until applyResult runs`() {
        assertTrue(zipShowsSpinner(zip, emptyMap(), emptyMap()))
    }

    @Test
    fun `composition startTick of zero does not fetch`() {
        assertFalse(shouldRefreshOnStartTick(0L))
        assertTrue(shouldRefreshOnStartTick(1L))
    }

    private fun sampleData(zip: String): WeatherData = WeatherData(
        place = Place(zip, "Charlotte Amalie", "VI", 18.34, -64.93),
        updatedAtEpochMs = 1L,
        current = CurrentConditions(
            tempF = 84.0,
            feelsLikeF = 90.0,
            condition = Condition.PARTLY_CLOUDY,
            conditionText = "Partly Cloudy",
            humidityPct = 70,
            windMph = 10.0,
            windDirDeg = 90,
            pressureInHg = 30.0,
            visibilityMi = 10.0,
            uvIndex = 8.0,
            dewpointF = 72.0,
            popPct = 20,
        ),
        hourly = emptyList(),
        daily = emptyList(),
        sun = null,
        source = "test",
    )
}
