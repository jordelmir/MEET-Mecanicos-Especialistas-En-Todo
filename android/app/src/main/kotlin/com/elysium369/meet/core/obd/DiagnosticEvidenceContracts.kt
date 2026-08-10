package com.elysium369.meet.core.obd

/** Immutable target captured before a destructive diagnostic-memory request. */
data class ClearVerificationTarget(
    val findingId: String,
    val vehicleId: String,
    val findingKey: DiagnosticFindingKey,
    val requiredSemantics: Set<DiagnosticSemantic>,
    val sourceService: String,
)

data class ClearVerificationPlan(
    val requestedAtMs: Long,
    val targets: List<ClearVerificationTarget>,
    val preClearReport: DtcScanReport?,
) {
    companion object {
        fun empty(): ClearVerificationPlan = ClearVerificationPlan(
            requestedAtMs = System.currentTimeMillis(),
            targets = emptyList(),
            preClearReport = null,
        )
    }
}

data class ClearCommandEvidence(
    val protocol: DiagnosticApplicationProtocol,
    val requestScope: DiagnosticRequestScope,
    val command: String,
    val rawResponse: String,
    val positiveService: Int?,
    val acceptedByEcu: Boolean,
    val adapterAcknowledged: Boolean,
    val negativeResponse: NegativeDiagnosticResponse? = null,
    val timestampMs: Long = System.currentTimeMillis(),
)

sealed interface ClearDtcResult {
    val commandEvidence: List<ClearCommandEvidence>
    val postClearReport: DtcScanReport?
    val verifiedFindingIds: Set<String>
    val message: String

    data class Verified(
        override val commandEvidence: List<ClearCommandEvidence>,
        override val postClearReport: DtcScanReport,
        override val verifiedFindingIds: Set<String>,
        override val message: String,
    ) : ClearDtcResult

    data class PartiallyVerified(
        override val commandEvidence: List<ClearCommandEvidence>,
        override val postClearReport: DtcScanReport,
        override val verifiedFindingIds: Set<String>,
        val unverifiedFindingIds: Set<String>,
        override val message: String,
    ) : ClearDtcResult

    data class AcceptedButNotVerified(
        override val commandEvidence: List<ClearCommandEvidence>,
        override val postClearReport: DtcScanReport?,
        override val verifiedFindingIds: Set<String> = emptySet(),
        override val message: String,
    ) : ClearDtcResult

    data class Rejected(
        override val commandEvidence: List<ClearCommandEvidence>,
        override val postClearReport: DtcScanReport? = null,
        override val verifiedFindingIds: Set<String> = emptySet(),
        override val message: String,
    ) : ClearDtcResult

    data class Inconclusive(
        override val commandEvidence: List<ClearCommandEvidence>,
        override val postClearReport: DtcScanReport?,
        override val verifiedFindingIds: Set<String> = emptySet(),
        override val message: String,
    ) : ClearDtcResult

    data class Cancelled(
        override val commandEvidence: List<ClearCommandEvidence>,
        override val postClearReport: DtcScanReport?,
        override val verifiedFindingIds: Set<String> = emptySet(),
        override val message: String,
    ) : ClearDtcResult
}

sealed interface UdsNrcAction {
    data class AwaitFinalResponse(val p2StarDelayMs: Long) : UdsNrcAction
    data class RetryAfterDelay(val delayMs: Long, val remainingAttempts: Int) : UdsNrcAction
    data class CapabilityBarrier(val reason: String) : UdsNrcAction
    data class PreconditionsRequired(val reason: String) : UdsNrcAction
    data class UnsupportedForRequest(val reason: String) : UdsNrcAction
    data class Reject(val reason: String) : UdsNrcAction
}

object UdsNegativeResponsePolicy {
    fun actionFor(
        response: NegativeDiagnosticResponse,
        attempt: Int,
        maxAttempts: Int = 3,
    ): UdsNrcAction = when (response.semantics) {
        NegativeResponseSemantics.RETRY_PENDING ->
            UdsNrcAction.AwaitFinalResponse(p2StarDelayMs = 1_000L)
        NegativeResponseSemantics.RETRY_AFTER_DELAY ->
            if (attempt < maxAttempts) {
                UdsNrcAction.RetryAfterDelay(delayMs = 350L * attempt.coerceAtLeast(1), remainingAttempts = maxAttempts - attempt)
            } else {
                UdsNrcAction.Reject("ECU busy: bounded retry budget exhausted")
            }
        NegativeResponseSemantics.SECURITY_REQUIRED ->
            UdsNrcAction.CapabilityBarrier("Security access is required; blind retry forbidden")
        NegativeResponseSemantics.CONDITIONS_NOT_CORRECT ->
            UdsNrcAction.PreconditionsRequired("Vehicle/ECU conditions are not correct")
        NegativeResponseSemantics.REQUEST_OUT_OF_RANGE,
        NegativeResponseSemantics.UNSUPPORTED ->
            UdsNrcAction.UnsupportedForRequest("Request is unsupported by this ECU capability")
        NegativeResponseSemantics.GENERAL_REJECT,
        NegativeResponseSemantics.UNKNOWN ->
            UdsNrcAction.Reject("ECU rejected the diagnostic request")
    }
}

data class ClearVerificationEvaluation(
    val verifiedFindingIds: Set<String>,
    val unverifiedFindingIds: Set<String>,
)

/** Pure evidence evaluator shared by runtime and integration-contract tests. */
object ClearVerificationEvaluator {
    fun evaluate(
        plan: ClearVerificationPlan,
        postClearReport: DtcScanReport,
        commandEvidence: List<ClearCommandEvidence>,
    ): ClearVerificationEvaluation {
        val verified = plan.targets.filter { target ->
            commandEvidence.any { it.acceptsTarget(target) } && postClearReport.provesAbsent(target)
        }.mapTo(linkedSetOf(), ClearVerificationTarget::findingId)
        return ClearVerificationEvaluation(
            verifiedFindingIds = verified,
            unverifiedFindingIds = plan.targets.mapTo(linkedSetOf(), ClearVerificationTarget::findingId) - verified,
        )
    }

    private fun ClearCommandEvidence.acceptsTarget(target: ClearVerificationTarget): Boolean {
        if (!acceptedByEcu) return false
        val protocolMatches = when (target.findingKey.namespace) {
            DiagnosticNamespace.SAE_OBD -> protocol == DiagnosticApplicationProtocol.SAE_OBD
            DiagnosticNamespace.UDS -> protocol in setOf(
                DiagnosticApplicationProtocol.UDS,
                DiagnosticApplicationProtocol.OBD_ON_UDS,
            )
            DiagnosticNamespace.KWP2000 -> protocol == DiagnosticApplicationProtocol.KWP2000
            DiagnosticNamespace.OEM -> protocol == DiagnosticApplicationProtocol.OEM
        }
        if (!protocolMatches) return false
        return when (val scope = requestScope) {
            is DiagnosticRequestScope.Functional ->
                target.findingKey.namespace == DiagnosticNamespace.SAE_OBD
            is DiagnosticRequestScope.Physical ->
                DiagnosticModuleIdentity.canonical(
                    scope.endpoint.requestAddress,
                    scope.endpoint.responseAddress,
                    scope.endpoint.moduleRole,
                ) == target.findingKey.moduleIdentity
            is DiagnosticRequestScope.Logical ->
                (scope.endpoint.logicalAddress ?: scope.endpoint.responseAddress ?: scope.endpoint.requestAddress)
                    ?.equals(target.findingKey.moduleIdentity, ignoreCase = true) == true
            DiagnosticRequestScope.LegacyUnaddressed ->
                target.findingKey.namespace == DiagnosticNamespace.SAE_OBD &&
                    target.findingKey.moduleIdentity == "LEGACY"
        }
    }

    private fun DtcScanReport.provesAbsent(target: ClearVerificationTarget): Boolean {
        val module = modules.firstOrNull { it.moduleIdentity == target.findingKey.moduleIdentity } ?: return false
        val covered = when (target.findingKey.namespace) {
            DiagnosticNamespace.SAE_OBD -> target.requiredSemantics.all { semantic ->
                val bucket = when (semantic) {
                    DiagnosticSemantic.SAE_ACTIVE_DTC -> DtcBucket.ACTIVE
                    DiagnosticSemantic.SAE_PENDING_DTC -> DtcBucket.PENDING
                    DiagnosticSemantic.SAE_PERMANENT_DTC -> DtcBucket.PERMANENT
                    else -> return@all false
                }
                module.completedBucket(bucket)
            }
            DiagnosticNamespace.UDS,
            DiagnosticNamespace.KWP2000,
            DiagnosticNamespace.OEM -> module.completedSemantics(target.requiredSemantics)
        }
        if (!covered) return false
        return records.none { record ->
            record.namespace == target.findingKey.namespace &&
                DiagnosticModuleIdentity.canonical(record.targetAddress, record.responseAddress, record.moduleName) ==
                    target.findingKey.moduleIdentity &&
                record.codeIdentity.stableRawIdentity == target.findingKey.rawDtcIdentity
        }
    }
}
