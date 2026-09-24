package com.elysium369.meet.core.emissions.physics

import org.junit.Assert.*
import org.junit.Test

class SpeedDensityEstimatorTest {

    private val estimator = SpeedDensityEstimator()

    @Test
    fun `speed density refuses calculation when displacement is unknown`() {
        val result = estimator.estimate(
            displacementLiters = null, // Unknown displacement!
            rpm = 2500.0,
            mapKpa = 45.0,
            iatC = 35.0
        )

        assertTrue(
            "Estimator must refuse calculation when displacement is null",
            result is Estimate.Unavailable
        )
        val unavailable = result as Estimate.Unavailable
        assertEquals("ENGINE_DISPLACEMENT_REQUIRED", unavailable.reason)
    }

    @Test
    fun `speed density computes realistic air mass with confirmed displacement`() {
        // Hyundai Accent 1.6L at 2500 RPM, 45 kPa MAP, 25°C IAT, VE 0.85
        val result = estimator.estimate(
            displacementLiters = 1.6,
            rpm = 2500.0,
            mapKpa = 45.0,
            iatC = 25.0,
            volumetricEfficiency = 0.85
        )

        assertTrue(result is Estimate.Available)
        val available = result as Estimate.Available

        // Expected air mass: (45 * 1.6 * 2500 * 0.85) / (120 * 0.287058 * 298.15) ≈ 14.88 g/s
        assertEquals(14.88, available.value, 0.5)
        assertEquals("SPEED_DENSITY_ESTIMATE", available.source)
    }
}
