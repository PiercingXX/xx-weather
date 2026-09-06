package com.xx.weather

import com.xx.weather.theme.FOREGROUND_INK
import com.xx.weather.theme.FOREGROUND_WHITE
import com.xx.weather.theme.SyncedTheme
import com.xx.weather.theme.ThemePreset
import com.xx.weather.theme.withAlpha
import com.xx.weather.widget.widgetPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetPaintTest {

    @Test
    fun `wide pop uses muted ink on Paper not accent blue`() {
        val theme = SyncedTheme(
            background = ThemePreset.PAPER.background,
            isDark = ThemePreset.PAPER.isDark,
            presetKey = ThemePreset.PAPER.key,
        )
        val paint = widgetPaint(theme)
        val expectedMuted = withAlpha(FOREGROUND_INK, 0xB3).toInt()
        assertEquals(expectedMuted, paint.muted)
        assertEquals(paint.muted, paint.pop)
        assertEquals(FOREGROUND_INK.toInt(), paint.foreground)
    }

    @Test
    fun `wide pop uses muted white on AMOLED`() {
        val theme = SyncedTheme(
            background = ThemePreset.AMOLED_NIGHT.background,
            isDark = ThemePreset.AMOLED_NIGHT.isDark,
            presetKey = ThemePreset.AMOLED_NIGHT.key,
        )
        val paint = widgetPaint(theme)
        assertEquals(withAlpha(FOREGROUND_WHITE, 0xB3).toInt(), paint.pop)
        assertEquals(paint.muted, paint.pop)
    }

    @Test
    fun `paintTheme sets hourly pop text color`() {
        val src = File("src/main/java/com/xx/weather/widget/WeatherWidgets.kt")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/xx/weather/widget/WeatherWidgets.kt")
        val text = src.readText()
        assertTrue(text.contains("setTextColor(HOUR_POP_IDS[i], paint.pop)"))
    }
}
