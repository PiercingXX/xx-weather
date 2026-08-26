package com.xx.weather

import com.xx.weather.data.PlacesCodec
import com.xx.weather.data.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesCodecTest {

    private val denver = Place("80202", "Denver", "CO", 39.7392, -104.9903)
    private val austin = Place("78701", "Austin", "TX", 30.2672, -97.7431)

    @Test
    fun `round trip preserves order and fields`() {
        val encoded = PlacesCodec.encode(listOf(denver, austin))
        val decoded = PlacesCodec.decode(encoded)
        assertEquals(2, decoded.size)
        assertEquals(denver, decoded[0])
        assertEquals(austin, decoded[1])
    }

    @Test
    fun `blank and invalid payloads are empty`() {
        assertTrue(PlacesCodec.decode(null).isEmpty())
        assertTrue(PlacesCodec.decode("").isEmpty())
        assertTrue(PlacesCodec.decode("not-json").isEmpty())
        assertTrue(PlacesCodec.decode("[{\"zip\":\"12\",\"lat\":1,\"lon\":2}]").isEmpty())
    }

    @Test
    fun `duplicate zips stay as encoded`() {
        val encoded = PlacesCodec.encode(listOf(denver, denver.copy(city = "LoDo")))
        val decoded = PlacesCodec.decode(encoded)
        assertEquals(2, decoded.size)
        assertEquals("LoDo", decoded[1].city)
    }
}
