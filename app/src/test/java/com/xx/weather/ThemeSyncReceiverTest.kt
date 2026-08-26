package com.xx.weather

import com.xx.weather.theme.ThemeSyncReceiver
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeSyncReceiverTest {

    private val manifest = File("src/main/AndroidManifest.xml").takeIf { it.exists() }
        ?: File("app/src/main/AndroidManifest.xml")

    @Test
    fun `manifest wires the exported theme receiver behind the family permission`() {
        val xml = manifest.readText()
        assertTrue(xml.contains(".theme.ThemeSyncReceiver"))
        assertTrue(xml.contains(ThemeSyncReceiver.ACTION_THEME_CHANGED))
        val receiverBlock = xml.substringAfter("ThemeSyncReceiver").substringBefore("</receiver>")
        assertTrue(receiverBlock.contains("android:exported=\"true\""))
        assertTrue(
            receiverBlock.contains("android:permission=\"${ThemeSyncReceiver.PERMISSION_THEME_SYNC}\""),
        )
        assertTrue(xml.contains("<uses-permission android:name=\"${ThemeSyncReceiver.PERMISSION_THEME_SYNC}\""))
        assertTrue(xml.contains("android:name=\".WeatherApp\""))
    }

    @Test
    fun `receiver class exists`() {
        Class.forName("com.xx.weather.theme.ThemeSyncReceiver")
    }
}
