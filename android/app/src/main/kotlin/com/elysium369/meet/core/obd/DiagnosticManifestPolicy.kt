package com.elysium369.meet.core.obd

enum class ProfessionalScanMode {
    QUICK_HEALTH,
    FULL_VEHICLE,
    POWERTRAIN_DEEP,
    NETWORK_INVENTORY,
    ECU_IDENTITY,
    EMISSIONS_READINESS,
    PRE_SCAN,
    POST_SCAN,
    CLEAR_VERIFY,
    UDS_DEEP_READONLY,
}

data class DiagnosticServiceAttempt(
    val moduleIdentity: String,
    val service: String,
    val confirmedModule: Boolean,
    val outcome: ModuleScanOutcome,
)

data class DiagnosticRetryTarget(val moduleIdentity: String, val service: String)

object DiagnosticCoverageEvaluator {
    fun completeness(attempts: List<DiagnosticServiceAttempt>): ScanCompleteness {
        val required = attempts.filter(DiagnosticServiceAttempt::confirmedModule)
        if (required.isEmpty()) return ScanCompleteness.INCONCLUSIVE
        val proven = required.count { it.outcome.provesBucketWasRead }
        return when {
            proven == required.size -> ScanCompleteness.COMPLETE
            proven > 0 -> ScanCompleteness.PARTIAL
            required.all { it.outcome == ModuleScanOutcome.CANCELLED } -> ScanCompleteness.PARTIAL
            else -> ScanCompleteness.FAILED
        }
    }

    fun canClaimNoDtc(attempt: DiagnosticServiceAttempt): Boolean =
        attempt.outcome == ModuleScanOutcome.NO_DTC
}

object DiagnosticRetryPlan {
    fun failedOnly(attempts: List<DiagnosticServiceAttempt>): List<DiagnosticRetryTarget> = attempts
        .filterNot { it.outcome.provesBucketWasRead || it.outcome == ModuleScanOutcome.UNSUPPORTED_SERVICE }
        .map { DiagnosticRetryTarget(it.moduleIdentity, it.service) }
        .distinct()
}
