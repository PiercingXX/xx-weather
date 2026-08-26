package com.xx.weather

import com.xx.weather.data.model.Units
import com.xx.weather.ui.Fmt
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the shared H/L renderer: missing bounds are omitted, never "--". */
class FmtHiloTest {

    @Test
    fun `missing high is omitted`() {
        assertEquals("L:61°", Fmt.hilo(null, 61.0, Units.FAHRENHEIT))
    }

    @Test
    fun `both bounds render`() {
        assertEquals("H:88° L:61°", Fmt.hilo(88.0, 61.0, Units.FAHRENHEIT))
    }

    @Test
    fun `both bounds missing renders empty`() {
        assertEquals("", Fmt.hilo(null, null, Units.FAHRENHEIT))
    }

    @Test
    fun `celsius converts the value`() {
        // 88F = (88-32)*5/9 = 31.1... -> rounds to 31.
        assertEquals("H:31°", Fmt.hilo(88.0, null, Units.CELSIUS))
    }
}
