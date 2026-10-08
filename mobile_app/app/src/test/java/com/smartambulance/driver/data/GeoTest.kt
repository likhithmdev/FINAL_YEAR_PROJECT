package com.smartambulance.driver.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Distance is the one derived figure the driver app shows, so it has to be right
 * when both positions are known and absent when they are not. Inventing a
 * distance for a hospital with no coordinates would be exactly the kind of
 * plausible-but-false number the app is meant to stop showing.
 */
class GeoTest {

    @Test
    fun `identical points are zero distance apart`() {
        val km = Geo.haversineKm(12.9716, 77.5946, 12.9716, 77.5946)
        assertEquals(0.0, km!!, 1e-9)
    }

    @Test
    fun `a known Bangalore pair matches the great-circle distance`() {
        // MG Road to Electronic City, roughly 12 km apart.
        val km = Geo.haversineKm(12.9756, 77.6068, 12.8452, 77.6602)
        assertEquals(15.4, km!!, 1.0)
    }

    @Test
    fun `a short hop is under a kilometre`() {
        val km = Geo.haversineKm(12.9716, 77.5946, 12.9760, 77.5990)
        assertEquals(true, km!! < 1.0)
    }

    @Test
    fun `any missing coordinate yields no distance instead of zero`() {
        assertNull(Geo.haversineKm(null, 77.5946, 12.9, 77.6))
        assertNull(Geo.haversineKm(12.9716, null, 12.9, 77.6))
        assertNull(Geo.haversineKm(12.9716, 77.5946, null, 77.6))
        assertNull(Geo.haversineKm(12.9716, 77.5946, 12.9, null))
        assertNull(Geo.haversineKm(null, null, null, null))
    }

    @Test
    fun `distance formatting switches units by magnitude`() {
        assertEquals("0 m", Geo.formatKm(0.0))
        assertEquals("420 m", Geo.formatKm(0.42))
        assertEquals("1.5 km", Geo.formatKm(1.5))
        assertEquals("12 km", Geo.formatKm(12.4))
    }

    @Test
    fun `an unknown distance formats as nothing at all`() {
        assertNull(Geo.formatKm(null))
    }
}
