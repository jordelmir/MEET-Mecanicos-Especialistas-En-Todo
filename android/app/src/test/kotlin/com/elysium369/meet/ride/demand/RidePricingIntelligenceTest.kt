package com.elysium369.meet.ride.demand

import org.junit.Assert.*
import org.junit.Test

class RidePricingIntelligenceTest {

    @Test
    fun returns_null_when_insufficient_samples() {
        val result = RidePricingIntelligence.calculateMarketRange(listOf(3000L, 3100L))
        assertNull(result)
    }

    @Test
    fun calculates_correct_market_range_from_sufficient_samples() {
        val fares = listOf(2800L, 2900L, 3000L, 3100L, 3200L, 3300L, 3400L, 3500L, 3600L, 3700L)
        val range = RidePricingIntelligence.calculateMarketRange(fares)

        assertNotNull(range)
        range!!
        assertEquals(10, range.sampleSize)
        assertTrue(range.lowMinor <= range.medianMinor)
        assertTrue(range.medianMinor <= range.highMinor)
    }

    @Test
    fun formatted_range_shows_colones_correctly() {
        val fares = listOf(290000L, 300000L, 310000L, 320000L, 340000L)
        val range = RidePricingIntelligence.calculateMarketRange(fares)!!

        assertTrue(range.formattedRange.contains("₡"))
        assertTrue(range.formattedRange.contains("–"))
    }

    @Test
    fun preserves_demand_level_in_range() {
        val fares = listOf(3000L, 3100L, 3200L, 3300L, 3400L)
        val range = RidePricingIntelligence.calculateMarketRange(fares, RideDemandLevel.HIGH)!!

        assertEquals(RideDemandLevel.HIGH, range.demandLevel)
    }
}
