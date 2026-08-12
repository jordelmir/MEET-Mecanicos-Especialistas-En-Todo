package com.elysium369.meet.domain.diagnostics

/** Repair completion and fault resolution are deliberately separate truths. */
enum class RepairVerificationState {
    PROCEDURE_COMPLETED,
    IMPROVED,
    NOT_OBSERVED,
    PENDING_DRIVE_CYCLE,
    VERIFIED_RESOLVED,
    RECURRED,
    INCONCLUSIVE,
}

data class RepairVerificationEvidence(
    val findingId: String,
    val procedureCompletedAt: Long,
    val postScanId: String?,
    val postScanComplete: Boolean,
    val findingObservedAfterRepair: Boolean?,
    val relevantSignalsImproved: Boolean?,
    val mode06Passed: Boolean?,
    val readinessComplete: Boolean?,
    val driveCycleRequired: Boolean,
    val evidenceIds: Set<String>,
)

data class RepairVerificationDecision(
    val state: RepairVerificationState,
    val reason: String,
    val evidenceIds: Set<String>,
)

object RepairVerificationEngine {
    fun evaluate(evidence: RepairVerificationEvidence): RepairVerificationDecision {
        if (evidence.postScanId == null || !evidence.postScanComplete) {
            return decision(RepairVerificationState.PROCEDURE_COMPLETED, "Falta post-scan completo.", evidence)
        }
        if (evidence.findingObservedAfterRepair == true) {
            return decision(RepairVerificationState.RECURRED, "El hallazgo volvió a observarse.", evidence)
        }
        if (evidence.findingObservedAfterRepair == null) {
            return decision(RepairVerificationState.INCONCLUSIVE, "El post-scan no probó presencia ni ausencia.", evidence)
        }
        if (evidence.relevantSignalsImproved == false || evidence.mode06Passed == false) {
            return decision(RepairVerificationState.NOT_OBSERVED, "El DTC no apareció, pero la evidencia física no confirma reparación.", evidence)
        }
        if (evidence.driveCycleRequired && evidence.readinessComplete != true) {
            return decision(RepairVerificationState.PENDING_DRIVE_CYCLE, "Falta completar el ciclo de manejo/readiness.", evidence)
        }
        if (evidence.relevantSignalsImproved == true &&
            evidence.mode06Passed != false &&
            (!evidence.driveCycleRequired || evidence.readinessComplete == true)
        ) {
            return decision(RepairVerificationState.VERIFIED_RESOLVED, "Resolución verificada por evidencia posterior.", evidence)
        }
        return decision(RepairVerificationState.IMPROVED, "Hay mejora, pero la evidencia aún no cierra la reparación.", evidence)
    }

    private fun decision(
        state: RepairVerificationState,
        reason: String,
        evidence: RepairVerificationEvidence,
    ) = RepairVerificationDecision(state, reason, evidence.evidenceIds)
}
