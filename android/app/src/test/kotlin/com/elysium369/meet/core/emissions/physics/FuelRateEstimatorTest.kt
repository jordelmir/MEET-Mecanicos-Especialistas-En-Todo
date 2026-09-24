package com.elysium369.meet.core.emissions.physics

import com.elysium369.meet.core.emissions.domain.EngineProfile
import com.elysium369.meet.core.emissions.domain.EvidenceOrigin
import com.elysium369.meet.core.emissions.domain.TruthClass
import org.junit.Assert.*
import org.junit.Test

class FuelRateEstimatorTest {

    private val estimator = FuelRateEstimator()
    private val standardProfile = EngineProfile.standardGasoline(displacementL = 1.6)

    @Test
    fun `level 1 physical ECU fuel rate PID takes precedence as MEASURED`() {
        val result = estimator.estimate(
            ecuFuelRateLph = 2.4, // Physical PID 015E
            mafGps = 15.0,
            measuredLambda = 1.0,
            isClosedLoop = true,
            engineProfile = standardProfile
        )

        assertTrue(result is FuelRateResult.Available)
        val available = result as FuelRateResult.Available
        assertEquals(2.4, available.litersPerHour, 0.001)
        assertEquals(EvidenceOrigin.ECU_DIRECT, available.origin)
        assertEquals(TruthClass.MEASURED, available.truthClass)
    }

    @Test
    fun `level 2 physical MAF with independent lambda produces DERIVED truth class`() {
        val result = estimator.estimate(
            ecuFuelRateLph = null,
            mafGps = 14.7, // 14.7 g/s air
            measuredLambda = 1.0, // stoichiometric
            isClosedLoop = true,
            engineProfile = standardProfile
        )

        assertTrue(result is FuelRateResult.Available)
        val available = result as FuelRateResult.Available
        assertEquals(1.0, available.gramsPerSecond, 0.01) // 14.7 / 14.7 = 1.0 g/s fuel
        assertEquals(EvidenceOrigin.PHYSICS_DERIVED, available.origin)
        assertEquals(TruthClass.DERIVED, available.truthClass)
    }

    @Test
    fun `level 3 closed-loop assumption produces MODEL_ESTIMATED with explicit assumption in lineage`() {
        val result = estimator.estimate(
            ecuFuelRateLph = null,
            mafGps = 14.7,
            measuredLambda = null, // No measured lambda!
            isClosedLoop = true,
            engineProfile = standardProfile
        )

        assertTrue(result is FuelRateResult.Available)
        val available = result as FuelRateResult.Available
        assertEquals(EvidenceOrigin.MODEL_ESTIMATED, available.origin)
        assertEquals(TruthClass.ESTIMATED, available.truthClass)
        assertTrue(available.lineage.contains("ASSUMED_STOICHIOMETRIC_LAMBDA_CLOSED_LOOP"))
    }

    @Test
    fun `insufficient telemetry returns Unavailable`() {
        val result = estimator.estimate(
            ecuFuelRateLph = null,
            mafGps = null,
            measuredLambda = null,
            isClosedLoop = false,
            engineProfile = EngineProfile(displacementLiters = null) // No displacement either
        )

        assertTrue(result is FuelRateResult.Unavailable)
    }
}
