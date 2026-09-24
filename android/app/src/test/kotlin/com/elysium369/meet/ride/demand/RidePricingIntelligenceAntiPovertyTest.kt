package com.elysium369.meet.ride.demand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RidePricingIntelligenceAntiPovertyTest {

    @Test
    fun `floor does not apply when median is above floor`() {
        val fares = listOf(500L, 600L, 700L, 800L, 900L, 1000L)
        val result = RidePricingIntelligence.calculateMarketRange(
            recentAcceptedFaresMinor = fares,
            sustainableFloorMinor = 400L,
        )
        assertNotNull(result)
        assertFalse(result!!.sustainableFloorApplied)
        assertEquals(result.medianMinor, result.recommendedMinor)
    }

    @Test
    fun `floor raises recommended when median is below floor`() {
        val fares = listOf(100L, 150L, 200L, 250L, 300L) // median = 200
        val result = RidePricingIntelligence.calculateMarketRange(
            recentAcceptedFaresMinor = fares,
            sustainableFloorMinor = 350L,
        )
        assertNotNull(result)
        assertTrue(result!!.sustainableFloorApplied)
        assertEquals(350L, result.recommendedMinor)
        assertEquals(200L, result.medianMinor) // raw median preserved for transparency
    }

    @Test
    fun `floor raises low end but not high end`() {
        val fares = listOf(100L, 150L, 200L, 250L, 300L, 350L, 400L, 450L, 500L, 550L, 600L)
        val result = RidePricingIntelligence.calculateMarketRange(
            recentAcceptedFaresMinor = fares,
            sustainableFloorMinor = 250L,
        )
        assertNotNull(result)
        // P10 = index 1 = 150, but floor raises it to 250
        assertTrue(result!!.lowMinor >= 250L)
        // High end unaffected
        assertTrue(result.highMinor >= 500L)
    }

    @Test
    fun `null floor preserves pure historical behavior`() {
        val fares = listOf(100L, 200L, 300L, 400L, 500L)
        val withFloor = RidePricingIntelligence.calculateMarketRange(fares, sustainableFloorMinor = null)
        val legacy = RidePricingIntelligence.calculateMarketRange(fares)
        assertNotNull(withFloor)
        assertNotNull(legacy)
        assertEquals(legacy!!.medianMinor, withFloor!!.recommendedMinor)
        assertFalse(withFloor.sustainableFloorApplied)
    }

    @Test
    fun `fewer than 3 samples returns null regardless of floor`() {
        assertNull(RidePricingIntelligence.calculateMarketRange(listOf(100L, 200L), sustainableFloorMinor = 50L))
    }

    @Test
    fun `negative floor is coerced to zero`() {
        val fares = listOf(100L, 200L, 300L)
        val result = RidePricingIntelligence.calculateMarketRange(fares, sustainableFloorMinor = -50L)
        assertNotNull(result)
        // Floor coerced to 0, which is below all fares, so no floor applied
        assertFalse(result!!.sustainableFloorApplied)
    }
}
