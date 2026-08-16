package com.elysium369.meet.domain.diagnostics

import kotlin.math.absoluteValue

/** Repair completion and fault resolution are deliberately separate truths. */
enum class RepairVerificationState {
    PROCEDURE_COMPLETED, IMPROVED, NOT_OBSERVED, PENDING_DRIVE_CYCLE,
    VERIFIED_RESOLVED, RECURRED, INCONCLUSIVE,
}

data class PostScanCoverageProof(
    val scanId: String,
    val vehicleId: String,
    val vehicleBindingId: String,
    val coveredFindingIds: Set<String>,
    val observedFindingIds: Set<String>,
    val requiredCoverageScopes: Set<String>,
    val completedCoverageScopes: Set<String>,
    val evidenceIds: Set<String>,
) {
    val complete: Boolean get() = requiredCoverageScopes.isNotEmpty() &&
        completedCoverageScopes.containsAll(requiredCoverageScopes)
    fun findingObserved(findingId: String): Boolean = findingId in observedFindingIds
}

data class SignalComparisonEvidence(
    val signalId: String,
    val unit: String,
    val beforeDeviationFromSpecification: Double,
    val afterDeviationFromSpecification: Double,
    val evidenceIds: Set<String>,
) {
    val comparable: Boolean get() = signalId.isNotBlank() && unit.isNotBlank() &&
        beforeDeviationFromSpecification.isFinite() && afterDeviationFromSpecification.isFinite()
    val improved: Boolean get() = comparable &&
        afterDeviationFromSpecification.absoluteValue < beforeDeviationFromSpecification.absoluteValue
}

data class SignalComparisonProof(val comparisons: List<SignalComparisonEvidence>) {
    val evidenceIds: Set<String> get() = comparisons.flatMapTo(linkedSetOf()) { it.evidenceIds }
    val comparable: Boolean get() = comparisons.isNotEmpty() && comparisons.all { it.comparable }
    val improved: Boolean get() = comparable && comparisons.all { it.improved }
}

data class Mode06MetricEvidence(
    val testId: String,
    val measured: Double,
    val minimum: Double,
    val maximum: Double,
    val evidenceId: String,
) {
    val passed: Boolean get() = measured.isFinite() && minimum.isFinite() && maximum.isFinite() &&
        minimum <= maximum && measured in minimum..maximum
}

data class Mode06VerificationProof(val metrics: List<Mode06MetricEvidence>) {
    val evidenceIds: Set<String> get() = metrics.mapTo(linkedSetOf()) { it.evidenceId }
    val passed: Boolean? get() = if (metrics.isEmpty()) null else metrics.all { it.passed }
}

data class ReadinessVerificationProof(
    val requiredMonitorIds: Set<String>,
    val completeMonitorIds: Set<String>,
    val evidenceIds: Set<String>,
) {
    val complete: Boolean? get() = if (requiredMonitorIds.isEmpty()) null
        else completeMonitorIds.containsAll(requiredMonitorIds)
}

data class DriveCycleVerificationProof(
    val requiredMilestones: Set<String>,
    val completedMilestones: Set<String>,
    val evidenceIds: Set<String>,
) {
    val required: Boolean get() = requiredMilestones.isNotEmpty()
    val completed: Boolean? get() = if (requiredMilestones.isEmpty()) null
        else completedMilestones.containsAll(requiredMilestones)
}

data class RepairVerificationBundle(
    val findingId: String,
    val vehicleId: String,
    val vehicleBindingId: String,
    val procedureCompletedAt: Long,
    val preRepairObservationIds: Set<String>,
    val repairActionIds: Set<String>,
    val postScanCoverageProof: PostScanCoverageProof?,
    val postRepairObservationIds: Set<String>,
    val signalComparison: SignalComparisonProof?,
    val mode06: Mode06VerificationProof?,
    val readiness: ReadinessVerificationProof?,
    val driveCycle: DriveCycleVerificationProof?,
) {
    init {
        require(findingId.isNotBlank() && vehicleId.isNotBlank() && vehicleBindingId.isNotBlank())
        require(procedureCompletedAt > 0 && repairActionIds.none(String::isBlank))
    }

    val evidenceIds: Set<String> get() = buildSet {
        addAll(preRepairObservationIds)
        addAll(repairActionIds)
        addAll(postRepairObservationIds)
        addAll(postScanCoverageProof?.evidenceIds.orEmpty())
        addAll(signalComparison?.evidenceIds.orEmpty())
        addAll(mode06?.evidenceIds.orEmpty())
        addAll(readiness?.evidenceIds.orEmpty())
        addAll(driveCycle?.evidenceIds.orEmpty())
    }
}

data class RepairVerificationDecision(
    val state: RepairVerificationState,
    val reason: String,
    val evidenceIds: Set<String>,
)

object RepairVerificationEngine {
    fun evaluate(bundle: RepairVerificationBundle): RepairVerificationDecision {
        if (bundle.repairActionIds.isEmpty()) return decision(
            RepairVerificationState.PROCEDURE_COMPLETED,
            "Procedimiento marcado, pero falta una acción de reparación trazable.",
            bundle,
        )
        val scan = bundle.postScanCoverageProof ?: return decision(
            RepairVerificationState.INCONCLUSIVE,
            "Falta post-scan con prueba de cobertura.",
            bundle,
        )
        if (scan.vehicleId != bundle.vehicleId || scan.vehicleBindingId != bundle.vehicleBindingId ||
            bundle.findingId !in scan.coveredFindingIds
        ) return decision(
            RepairVerificationState.INCONCLUSIVE,
            "El post-scan no pertenece al mismo vehículo, binding y finding.",
            bundle,
        )
        if (!scan.complete || scan.evidenceIds.isEmpty()) return decision(
            RepairVerificationState.INCONCLUSIVE,
            "La cobertura del post-scan es incompleta o no resoluble.",
            bundle,
        )
        if (scan.findingObserved(bundle.findingId)) return decision(
            RepairVerificationState.RECURRED,
            "El mismo hallazgo volvió a observarse con cobertura válida.",
            bundle,
        )
        val signal = bundle.signalComparison
        if (signal == null || !signal.comparable || signal.evidenceIds.isEmpty()) return decision(
            RepairVerificationState.NOT_OBSERVED,
            "El finding no apareció, pero falta comparación física comparable.",
            bundle,
        )
        if (!signal.improved || bundle.mode06?.passed == false) return decision(
            RepairVerificationState.NOT_OBSERVED,
            "El finding no apareció, pero señales o Mode 06 no confirman reparación.",
            bundle,
        )
        val driveCycle = bundle.driveCycle
        if (driveCycle?.required == true &&
            (driveCycle.completed != true || bundle.readiness?.complete != true)
        ) return decision(
            RepairVerificationState.PENDING_DRIVE_CYCLE,
            "Falta completar el ciclo de manejo y readiness requerido.",
            bundle,
        )
        val evidenceReferencesComplete = bundle.preRepairObservationIds.isNotEmpty() &&
            bundle.postRepairObservationIds.isNotEmpty() &&
            bundle.evidenceIds.none(String::isBlank)
        if (!evidenceReferencesComplete) return decision(
            RepairVerificationState.IMPROVED,
            "Hay mejora, pero la cadena antes/después aún no está completa.",
            bundle,
        )
        return decision(
            RepairVerificationState.VERIFIED_RESOLVED,
            "Resolución verificada por bundle tipado del mismo vehículo y binding.",
            bundle,
        )
    }

    private fun decision(state: RepairVerificationState, reason: String, bundle: RepairVerificationBundle) =
        RepairVerificationDecision(state, reason, bundle.evidenceIds)
}
