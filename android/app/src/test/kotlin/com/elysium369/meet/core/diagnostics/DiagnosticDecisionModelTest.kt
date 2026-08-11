package com.elysium369.meet.core.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticDecisionModelTest {
    @Test
    fun quantitativeScoreIsUnavailableWithoutCalibrationMatrix() {
        val score = DiagnosticTestDecisionModel.score(
            priors = listOf(CalibratedHypothesisPrior("relay", 0.5), CalibratedHypothesisPrior("pump", 0.5)),
            candidate = DiagnosticTestDecisionCandidate(
                testId = "voltage_drop",
                possibleOutcomes = emptyList(),
                normalizedCost = 0.1,
                normalizedTimePenalty = 0.1,
                normalizedSafetyRisk = 0.1,
                requiredTools = listOf("DVOM"),
                calibrationDatasetId = null,
            ),
        )
        assertFalse(score.calibrated)
        assertTrue(score.reason.startsWith("CALIBRATION_REQUIRED"))
    }

    @Test
    fun calibratedDiscriminatingTestProducesPositiveInformationGain() {
        val score = DiagnosticTestDecisionModel.score(
            priors = listOf(CalibratedHypothesisPrior("relay", 0.5), CalibratedHypothesisPrior("pump", 0.5)),
            candidate = DiagnosticTestDecisionCandidate(
                testId = "voltage_drop",
                possibleOutcomes = listOf(
                    CalibratedTestOutcome("pass", mapOf("relay" to 0.9, "pump" to 0.1)),
                    CalibratedTestOutcome("fail", mapOf("relay" to 0.1, "pump" to 0.9)),
                ),
                normalizedCost = 0.05,
                normalizedTimePenalty = 0.05,
                normalizedSafetyRisk = 0.05,
                requiredTools = listOf("DVOM"),
                calibrationDatasetId = "verified-repairs-v1",
            ),
        )
        assertTrue(score.calibrated)
        assertTrue(score.expectedInformationGainBits > 0.0)
    }
}
