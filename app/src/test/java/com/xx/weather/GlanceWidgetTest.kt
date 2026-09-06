package com.xx.weather

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Pins the W4 feels-like/wind glance widget wiring (mirrors ThemeSyncReceiverTest). */
class GlanceWidgetTest {

    private val manifest = File("src/main/AndroidManifest.xml").takeIf { it.exists() }
        ?: File("app/src/main/AndroidManifest.xml")

    @Test
    fun `manifest registers the glance widget receiver with its own provider`() {
        val xml = manifest.readText()
        assertTrue(xml.contains(".widget.GlanceWeatherWidget"))
        val receiverBlock = xml.substringAfter("GlanceWeatherWidget").substringBefore("</receiver>")
        assertTrue(receiverBlock.contains("android:exported=\"false\""))
        assertTrue(receiverBlock.contains("@xml/widget_glance_info"))
        assertTrue(receiverBlock.contains("@string/widget_glance_label"))
    }

    @Test
    fun `glance widget provider class exists`() {
        Class.forName("com.xx.weather.widget.GlanceWeatherWidget")
    }

    @Test
    fun `glance layout declares feels-like and wind text views`() {
        val layout = File("src/main/res/layout/widget_glance.xml").takeIf { it.exists() }
            ?: File("app/src/main/res/layout/widget_glance.xml")
        val xml = layout.readText()
        assertTrue(xml.contains("@+id/widget_feels"))
        assertTrue(xml.contains("@+id/widget_wind"))
        assertTrue(xml.contains("@+id/widget_loc"))
    }

    @Test
    fun `glance renderer paints feels-like wind zip and taps through to that zip`() {
        val src = File("src/main/java/com/xx/weather/widget/WeatherWidgets.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/xx/weather/widget/WeatherWidgets.kt")
        val text = src.readText()
        assertTrue(text.contains("widget_feels"))
        assertTrue(text.contains("widget_wind"))
        assertTrue(text.contains("place.zip"))
        assertTrue(text.contains("MainActivity.EXTRA_ZIP"))
        assertTrue(text.contains("Feels like"))
        assertTrue(text.contains("mph"))
    }
}