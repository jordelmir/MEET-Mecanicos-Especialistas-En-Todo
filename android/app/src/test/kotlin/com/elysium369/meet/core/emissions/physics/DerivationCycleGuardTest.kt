package com.elysium369.meet.core.emissions.physics

import org.junit.Assert.*
import org.junit.Test

class DerivationCycleGuardTest {

    private val guard = DerivationCycleGuard()

    @Test
    fun `lambda cannot use fuel rate derived from assumed lambda`() {
        val fuelRateLineage = setOf(
            "MAF_PHYSICAL",
            "ASSUMED_STOICHIOMETRIC_LAMBDA_CLOSED_LOOP"
        )

        val exception = assertThrows(DerivationCycleException::class.java) {
            guard.assertLambdaNotDerivedFromAssumedLambdaFuelRate(fuelRateLineage)
        }

        assertTrue(exception.message!!.contains("Tautology violation"))
    }

    @Test
    fun `direct circular dependency throws DerivationCycleException`() {
        val dependencies = setOf("MAF_PHYSICAL", "ENGINE_RPM", "LAMBDA")

        val exception = assertThrows(DerivationCycleException::class.java) {
            guard.assertAcyclic(target = "LAMBDA", dependencies = dependencies)
        }

        assertTrue(exception.message!!.contains("Circular derivation detected"))
    }

    @Test
    fun `clean acyclic lineage passes validation`() {
        val dependencies = setOf("MAP_PHYSICAL", "ENGINE_RPM", "IAT_PHYSICAL")
        // Should not throw
        guard.assertAcyclic(target = "SPEED_DENSITY_AIR", dependencies = dependencies)
    }
}
