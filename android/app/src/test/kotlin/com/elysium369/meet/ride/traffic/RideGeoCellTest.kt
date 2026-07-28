package com.elysium369.meet.ride.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RideGeoCellTest {
    @Test
    fun `encodes deterministic bounded geohash`() {
        val sanJose = RideGeoCell.encode(9.9281, -84.0907)

        assertEquals(6, sanJose.length)
        assertEquals(sanJose, RideGeoCell.encode(9.9281, -84.0907))
        assertNotEquals(sanJose, RideGeoCell.encode(10.6333, -85.4333))
    }
}
