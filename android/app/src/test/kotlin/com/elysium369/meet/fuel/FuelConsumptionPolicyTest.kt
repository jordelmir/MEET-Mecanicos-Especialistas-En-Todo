package com.elysium369.meet.fuel

import com.elysium369.meet.fuel.domain.FuelConsumptionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelConsumptionPolicyTest {
    @Test
    fun `consumption is unavailable without recorded distance`() {
        val result = FuelConsumptionPolicy.calculate(40_000, null)
        assertNull(result.litersPer100Km)
        assertEquals("INSUFFICIENT_EVIDENCE", result.truthState)
    }

    @Test
    fun `recorded volume and distance produce deterministic consumption`() {
        val result = FuelConsumptionPolicy.calculate(40_000, 500_000)
        assertEquals("8.00", result.litersPer100Km.toString())
        assertEquals("CALCULATED_FROM_RECORDED_DISTANCE", result.truthState)
    }

    @Test
    fun `zero or reversed odometer distance cannot produce consumption`() {
        assertNull(FuelConsumptionPolicy.calculate(40_000, 0).litersPer100Km)
        assertNull(FuelConsumptionPolicy.calculate(40_000, -10_000).litersPer100Km)
    }
}
