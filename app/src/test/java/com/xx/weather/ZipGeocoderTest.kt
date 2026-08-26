package com.xx.weather

import com.xx.weather.data.ZipGeocoder
import com.xx.weather.theme.CUSTOM_THEME_NAME
import com.xx.weather.theme.resolveSyncedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipGeocoderTest {

    @Test
    fun `virgin islands zips resolve locally without a network lookup`() {
        val stThomas = ZipGeocoder.localPlace("00802")
        assertNotNull(stThomas)
        assertEquals("Charlotte Amalie", stThomas!!.city)
        assertEquals("VI", stThomas.state)
        assertTrue(stThomas.lat in 18.0..19.0)
        assertTrue(stThomas.lon in -65.5..-64.0)

        val stJohn = ZipGeocoder.localPlace("00830")
        assertEquals("Cruz Bay", stJohn!!.city)
        val stCroix = ZipGeocoder.localPlace("00820")
        assertEquals("Christiansted", stCroix!!.city)
    }

    @Test
    fun `008xx tries the vi zippopotam country first`() {
        assertEquals(listOf("vi", "us"), ZipGeocoder.countriesFor("00802"))
        assertEquals(listOf("us"), ZipGeocoder.countriesFor("80202"))
        assertEquals(listOf("pr", "us"), ZipGeocoder.countriesFor("00901"))
    }

    @Test
    fun `normalize accepts five-digit codes including leading zeros`() {
        assertEquals("00802", ZipGeocoder.normalize("00802"))
        assertEquals("00802", ZipGeocoder.normalize(" 00802 "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalize rejects short codes`() {
        ZipGeocoder.normalize("802")
    }
}

class ThemeSyncTest {

    @Test
    fun `named presets resolve by display name`() {
        val forest = resolveSyncedTheme("Forest Night", null)
        assertNotNull(forest)
        assertEquals(0xFF10261B, forest!!.background)
        assertTrue(forest.isDark)
    }

    @Test
    fun `custom uses the carried background`() {
        val custom = resolveSyncedTheme(CUSTOM_THEME_NAME, 0xFF224466)
        assertNotNull(custom)
        assertEquals(0xFF224466, custom!!.background)
        assertNull(custom.presetKey)
    }

    @Test
    fun `unknown names with no background are ignored`() {
        assertNull(resolveSyncedTheme("Not A Theme", null))
        assertNull(resolveSyncedTheme(CUSTOM_THEME_NAME, null))
    }
}
