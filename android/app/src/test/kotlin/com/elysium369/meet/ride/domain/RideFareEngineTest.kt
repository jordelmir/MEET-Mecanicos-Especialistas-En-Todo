package com.elysium369.meet.ride.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RideFareEngineTest {
    @Test
    fun `CRC metered quote charges 300 per km and 60 per minute`() {
        val quote = RideFareEngine.quoteCostaRica(
            distanceMeters = 10_000,
            durationSeconds = 20 * 60L,
        )

        assertEquals(3_000L, quote.distanceFareMinor)
        assertEquals(1_200L, quote.timeFareMinor)
        assertEquals(4_200L, quote.estimatedTotalMinor)
        assertEquals(CurrencyCode.of("CRC"), quote.currency)
    }

    @Test
    fun `metered fare prorates partial metres and seconds without floating point`() {
        val quote = RideFareEngine.quoteCostaRica(1, 1)

        assertEquals(1L, quote.distanceFareMinor)
        assertEquals(1L, quote.timeFareMinor)
        assertEquals(2L, quote.estimatedTotalMinor)
    }

    @Test
    fun `only metered mode can change stops after publishing`() {
        assertFalse(RideFareEngine.canChangeStops(RideFareMode.OPEN_BID, "IN_PROGRESS"))
        assertTrue(
            RideFareEngine.canChangeStops(
                RideFareMode.METERED_TIME_DISTANCE,
                "IN_PROGRESS",
            ),
        )
        assertFalse(
            RideFareEngine.canChangeStops(
                RideFareMode.METERED_TIME_DISTANCE,
                "COMPLETED",
            ),
        )
    }

    @Test
    fun `negative route metrics are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RideFareEngine.quoteCostaRica(-1, 0)
        }
    }

    @Test
    fun `overflow is rejected instead of wrapping the fare`() {
        assertThrows(ArithmeticException::class.java) {
            RideFareEngine.quoteCostaRica(Long.MAX_VALUE, 0)
        }
    }
}
