package com.elysium369.meet.core.emissions.domain

import org.junit.Assert.*
import org.junit.Test

class EmissionTruthModelTest {

    @Test
    fun `unknown measurement never becomes zero`() {
        val unknownMetric = EmissionMetric.unknown(id = "EXHAUST_HC", unit = "ppm")

        assertNull("Unknown measurement value must remain null, never silently becoming zero", unknownMetric.value)
        assertEquals(TruthClass.UNKNOWN, unknownMetric.truthClass)
        assertEquals(Evaluation.INCONCLUSIVE, unknownMetric.evaluation)
    }

    @Test
    fun `invariant forbids MEASURED truth class from statistical models`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            EmissionMetric(
                id = "EXHAUST_CO",
                value = 0.25,
                unit = "% vol",
                origin = EvidenceOrigin.MODEL_ESTIMATED, // Model estimated!
                truthClass = TruthClass.MEASURED // Forbidden!
            )
        }
        assertTrue(exception.message!!.contains("Metrological Invariant Violated"))
    }

    @Test
    fun `MEASURED truth class is permitted from ECU_DIRECT or PHYSICAL_GAS_ANALYZER`() {
        val ecuMetric = EmissionMetric(
            id = "ENGINE_RPM",
            value = 750.0,
            unit = "rpm",
            origin = EvidenceOrigin.ECU_DIRECT,
            truthClass = TruthClass.MEASURED
        )
        assertEquals(TruthClass.MEASURED, ecuMetric.truthClass)

        val probeMetric = EmissionMetric(
            id = "PHYSICAL_HC",
            value = 45.0,
            unit = "ppm",
            origin = EvidenceOrigin.PHYSICAL_GAS_ANALYZER,
            truthClass = TruthClass.MEASURED
        )
        assertEquals(TruthClass.MEASURED, probeMetric.truthClass)
    }
}
