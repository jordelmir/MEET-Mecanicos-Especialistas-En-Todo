package com.elysium369.meet.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticManifestPolicyTest {
    @Test fun quickScanCoverage() {
        val confirmed = NetworkModule("7E0", "ECM", true, responseId = "7E8")
        val plan = DiagnosticScanPlanCompiler.compile(
            DiagnosticScanMode.QUICK,
            listOf(confirmed),
            mapOf("7E1" to "Static TCM candidate"),
        )
        assertEquals(listOf("7E0"), plan.map(DiagnosticScanTarget::requestAddress))
    }

    @Test fun fullVehicleConfirmedModules() {
        val confirmed = NetworkModule("7E0", "ECM", true, responseId = "7E8")
        val plan = DiagnosticScanPlanCompiler.compile(
            DiagnosticScanMode.FULL_VEHICLE,
            listOf(confirmed),
            mapOf("7E1" to "Static TCM candidate"),
        )
        assertTrue(plan.single { it.requestAddress == "7E0" }.requiredForCompleteness)
        assertFalse(plan.single { it.requestAddress == "7E1" }.requiredForCompleteness)
    }

    @Test fun clearVerifyTargetsAffectedEcus() {
        val confirmed = listOf(
            NetworkModule("7E0", "ECM", true, responseId = "7E8"),
            NetworkModule("7E1", "TCM", true, responseId = "7E9"),
        )
        val target = ClearVerificationTarget(
            findingId = "finding",
            vehicleId = "vehicle",
            findingKey = DiagnosticFindingKey(
                vehicleId = "vehicle",
                namespace = DiagnosticNamespace.SAE_OBD,
                moduleIdentity = "7E0",
                rawDtcIdentity = "P0301",
                displayCode = "P0301",
            ),
            requiredSemantics = setOf(DiagnosticSemantic.SAE_ACTIVE_DTC),
            sourceService = "03",
        )
        val plan = DiagnosticScanPlanCompiler.compile(
            DiagnosticScanMode.CLEAR_VERIFY,
            confirmed,
            emptyMap(),
            ClearVerificationPlan(1L, listOf(target), null),
        )
        assertEquals(listOf("7E0"), plan.map(DiagnosticScanTarget::requestAddress))
    }

    @Test fun staticCandidateDoesNotReduceCompleteness() {
        val attempts = listOf(
            attempt("7E0", true, ModuleScanOutcome.NO_DTC),
            attempt("760", false, ModuleScanOutcome.NO_RESPONSE),
        )
        assertEquals(ScanCompleteness.COMPLETE, DiagnosticCoverageEvaluator.completeness(attempts))
    }

    @Test fun noDtcRequiresSuccessfulBucketRead() {
        assertTrue(DiagnosticCoverageEvaluator.canClaimNoDtc(attempt("7E0", true, ModuleScanOutcome.NO_DTC)))
        assertFalse(DiagnosticCoverageEvaluator.canClaimNoDtc(attempt("7E0", true, ModuleScanOutcome.NO_RESPONSE)))
    }

    @Test fun partialScanPreservesEvidence() {
        val attempts = listOf(
            attempt("7E0", true, ModuleScanOutcome.COMPLETE),
            attempt("7E1", true, ModuleScanOutcome.NO_RESPONSE),
        )
        assertEquals(ScanCompleteness.PARTIAL, DiagnosticCoverageEvaluator.completeness(attempts))
    }

    @Test fun retryOnlyFailedModule() {
        val attempts = listOf(
            attempt("7E0", true, ModuleScanOutcome.COMPLETE),
            attempt("7E1", true, ModuleScanOutcome.TIMEOUT),
        )
        assertEquals(listOf(DiagnosticRetryTarget("7E1", "03")), DiagnosticRetryPlan.failedOnly(attempts))
    }

    @Test fun transportLossPreservesPartialManifest() {
        val attempts = listOf(
            attempt("7E0", true, ModuleScanOutcome.NO_DTC),
            attempt("7E1", true, ModuleScanOutcome.CANCELLED),
        )
        assertEquals(ScanCompleteness.PARTIAL, DiagnosticCoverageEvaluator.completeness(attempts))
    }

    private fun attempt(module: String, confirmed: Boolean, outcome: ModuleScanOutcome) =
        DiagnosticServiceAttempt(module, "03", confirmed, outcome)
}
