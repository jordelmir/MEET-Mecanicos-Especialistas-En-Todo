package com.elysium369.meet.domain.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class RepairVerificationEngineTest {
    @Test
    fun missingPostScanIsInconclusive() {
        assertEquals(
            RepairVerificationState.INCONCLUSIVE,
            RepairVerificationEngine.evaluate(bundle(postScan = null)).state,
        )
    }

    @Test
    fun sameVehicleEvidenceBundleCanVerifyResolution() {
        assertEquals(RepairVerificationState.VERIFIED_RESOLVED, RepairVerificationEngine.evaluate(bundle()).state)
    }

    @Test
    fun differentBindingIsInconclusive() {
        assertEquals(
            RepairVerificationState.INCONCLUSIVE,
            RepairVerificationEngine.evaluate(
                bundle(postScan = postScan().copy(vehicleBindingId = "other-binding")),
            ).state,
        )
    }

    private fun bundle(postScan: PostScanCoverageProof? = postScan()) = RepairVerificationBundle(
        findingId = "finding",
        vehicleId = "vehicle",
        vehicleBindingId = "binding",
        procedureCompletedAt = 1,
        preRepairObservationIds = setOf("pre-observation"),
        repairActionIds = setOf("repair-action"),
        postScanCoverageProof = postScan,
        postRepairObservationIds = setOf("post-observation"),
        signalComparison = SignalComparisonProof(
            listOf(SignalComparisonEvidence("fuel-pressure", "kPa", 25.0, 2.0, setOf("signal"))),
        ),
        mode06 = Mode06VerificationProof(
            listOf(Mode06MetricEvidence("misfire", 0.0, 0.0, 3.0, "mode06")),
        ),
        readiness = ReadinessVerificationProof(setOf("fuel"), setOf("fuel"), setOf("readiness")),
        driveCycle = DriveCycleVerificationProof(setOf("warm-up"), setOf("warm-up"), setOf("drive-cycle")),
    )

    private fun postScan() = PostScanCoverageProof(
        scanId = "scan",
        vehicleId = "vehicle",
        vehicleBindingId = "binding",
        coveredFindingIds = setOf("finding"),
        observedFindingIds = emptySet(),
        requiredCoverageScopes = setOf("ECM:19"),
        completedCoverageScopes = setOf("ECM:19"),
        evidenceIds = setOf("scan-exchange"),
    )
}
