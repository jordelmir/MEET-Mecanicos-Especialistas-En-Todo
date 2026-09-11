package com.elysium369.meet.ride.domain

import org.junit.Assert.*
import org.junit.Test

class RideTrackingTruthPolicyTest {
    @Test fun `USD minor units stay USD and preserve cents`() {
        assertEquals("USD $12.34", RideTrackingTruthPolicy.formatFare(1234, "USD"))
        assertEquals("CRC ₡1,234", RideTrackingTruthPolicy.formatFare(1234, "CRC"))
        assertEquals("Moneda no disponible", RideTrackingTruthPolicy.formatFare(1234, "ZZZ"))
        assertEquals("Tarifa no disponible", RideTrackingTruthPolicy.formatFare(-1, "USD"))
    }

    @Test fun `payment and rating require lifecycle settlement and connected authority`() {
        for (state in RideState.entries.filter { it != RideState.COMPLETED }) {
            assertEquals(RideTrackingActions(false, false), RideTrackingTruthPolicy.actions(state, false, true, true))
            assertEquals(RideTrackingActions(false, false), RideTrackingTruthPolicy.actions(state, true, true, true))
        }
        assertEquals(RideTrackingActions(true, false), RideTrackingTruthPolicy.actions(RideState.COMPLETED, false, true, true))
        assertEquals(RideTrackingActions(false, true), RideTrackingTruthPolicy.actions(RideState.COMPLETED, true, true, true))
        assertEquals(RideTrackingActions(false, false), RideTrackingTruthPolicy.actions(RideState.COMPLETED, true, false, false))
    }

    private fun freshness(now: Long, captured: Long = 100_000, received: Long = 100_000, source: String? = "server") =
        RideTrackingTruthPolicy.freshness(9.0, -84.0, 10f, captured, received, 1, source, now)

    @Test fun `tracking ages even when no further server events arrive`() {
        assertEquals(TrackingFreshness.LIVE, freshness(115_000))
        assertEquals(TrackingFreshness.RECENT, freshness(115_001))
        assertEquals(TrackingFreshness.STALE, freshness(160_001))
    }

    @Test fun `late delivery missing source future clock and poor GPS cannot become live`() {
        assertEquals(TrackingFreshness.STALE, freshness(200_000, received = 200_000))
        assertEquals(TrackingFreshness.UNKNOWN, freshness(100_000, source = null))
        assertEquals(TrackingFreshness.UNKNOWN, freshness(99_999))
        assertEquals(TrackingFreshness.UNKNOWN, RideTrackingTruthPolicy.freshness(9.0, -84.0, 101f, 100_000, 100_000, 1, "server", 100_000))
    }
}
