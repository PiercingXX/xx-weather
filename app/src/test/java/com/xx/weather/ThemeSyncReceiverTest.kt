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

class AlertsWiringTest {

    @Test
    fun `refresh worker evaluates alerts on the existing 15-minute pass`() {
        val src = File("src/main/java/com/xx/weather/widget/RefreshScheduler.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/xx/weather/widget/RefreshScheduler.kt")
        val text = src.readText()
        assertTrue(text.contains("WeatherAlerts.onWeather"))
        assertTrue(text.contains("INTERVAL_MINUTES = 15L"))
        assertTrue(text.contains("PeriodicWorkRequestBuilder<RefreshWorker>"))
        assertTrue(!text.contains("AlertWorker"))
    }

    @Test
    fun `settings requests notification permission on first enable`() {
        val screen = File("src/main/java/com/xx/weather/ui/WeatherScreen.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/xx/weather/ui/WeatherScreen.kt")
        val settings = File("src/main/java/com/xx/weather/ui/SettingsDialog.kt").takeIf { it.exists() }
            ?: File("app/src/main/java/com/xx/weather/ui/SettingsDialog.kt")
        val screenText = screen.readText()
        assertTrue(screenText.contains("POST_NOTIFICATIONS"))
        assertTrue(screenText.contains("RequestPermission"))
        assertTrue(settings.readText().contains("Alerts stay off"))
    }
}
