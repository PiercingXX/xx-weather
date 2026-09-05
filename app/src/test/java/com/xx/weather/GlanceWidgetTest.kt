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
}