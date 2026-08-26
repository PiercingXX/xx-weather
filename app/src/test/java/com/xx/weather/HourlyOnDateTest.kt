package com.xx.weather

import com.xx.weather.data.model.Condition
import com.xx.weather.data.model.CurrentConditions
import com.xx.weather.data.model.HourlyPoint
import com.xx.weather.data.model.Place
import com.xx.weather.data.model.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HourlyOnDateTest {

    @Test
    fun `hourlyOn keeps only hours for the tapped date`() {
        val day = LocalDate.of(2026, 8, 26)
        val next = day.plusDays(1)
        val data = WeatherData(
            place = Place("00802", "Charlotte Amalie", "VI", 18.34, -64.93),
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
            hourly = listOf(
                HourlyPoint(LocalDateTime.of(2026, 8, 26, 9, 0), 82.0, 10, Condition.CLEAR, true),
                HourlyPoint(LocalDateTime.of(2026, 8, 26, 15, 0), 86.0, 20, Condition.PARTLY_CLOUDY, true),
                HourlyPoint(LocalDateTime.of(2026, 8, 27, 9, 0), 80.0, 40, Condition.SHOWERS, true),
            ),
            daily = emptyList(),
            sun = null,
            source = "test",
        )
        assertEquals(2, data.hourlyOn(day).size)
        assertEquals(1, data.hourlyOn(next).size)
        assertEquals(15, data.hourlyOn(day)[1].time.hour)
    }
}
