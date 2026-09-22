package com.elysium369.meet.safety.domain

enum class SafetyReportCategory {
    HOMICIDE,
    VIOLENT_INCIDENT,
    DRUG_SALE_ACTIVITY,
    THREAT,
    MISSING_PERSON,
    INSTITUTIONAL_CONDUCT,
    OTHER,
}

enum class ClaimState {
    ALLEGED,
    OBSERVED,
    DOCUMENTED,
    CORROBORATED,
    STRONGLY_CORROBORATED,
    DISPUTED,
    CONTRADICTED,
    RETRACTED,
    SUPERSEDED,
    UNRESOLVED,
}

enum class EvidenceIntegrityState {
    UNKNOWN,
    HASH_VERIFIED,
    PROVENANCE_PARTIAL,
    PROVENANCE_VERIFIED,
    TAMPER_SIGNAL,
}

enum class PublicationDecision {
    PRIVATE_ONLY,
    AGGREGATE_ONLY,
    DELAYED_AGGREGATE,
    PUBLIC_REDACTED,
    PUBLIC,
    SUPPRESSED,
}

enum class SourceRelation {
    DIRECT_WITNESS,
    FAMILY_OR_NEIGHBOR,
    SECOND_HAND,
    DOCUMENTARY,
    JOURNALISTIC,
    PUBLIC_RECORD,
    UNKNOWN,
}

enum class SafetyCaseLifecycle {
    INTAKE,
    TRIAGE,
    UNDER_CORROBORATION,
    REVIEW,
    PUBLIC_ELIGIBLE,
    PUBLIC,
    CLOSED,
    DISPUTED,
}

enum class RemoteAvailability {
    UNKNOWN,
    ONLINE,
    OFFLINE,
    DEGRADED,
}

enum class SafetyOutboxStatus {
    PENDING,
    IN_FLIGHT,
    RETRYABLE,
    AUTH_BLOCKED,
    ACKNOWLEDGED,
    DEAD_LETTER,
}

enum class SafetyCommandType {
    CREATE_REPORT,
    ATTACH_EVIDENCE,
    SUBMIT_COUNTERCLAIM,
    REQUEST_CORRECTION,
}

enum class InstitutionalAllegationType {
    FAILURE_TO_ACT,
    INFORMATION_LEAK,
    EVIDENCE_TAMPERING,
    COLLUSION,
    CORRUPTION,
    OBSTRUCTION,
    ABUSE_OF_AUTHORITY,
    OTHER,
}

enum class CaseMilestoneType {
    INCIDENT_OCCURRED,
    DEATH_DOCUMENTED,
    HOMICIDE_CLASSIFIED,
    REPORT_SUBMITTED,
    RECEIPT_DOCUMENTED,
    PUBLIC_ACTION_FOUND,
    SUSPECT_PUBLICLY_IDENTIFIED,
    ARREST_DOCUMENTED,
    CHARGE_DOCUMENTED,
    TRIAL_STARTED,
    JUDGMENT_RECORDED,
    APPEAL_RECORDED,
}

enum class GeoDisclosure {
    APPROXIMATE_1000M,
    APPROXIMATE_500M,
    STREET_SEGMENT,
    EXACT_PUBLIC_PLACE,
    SUPPRESSED,
}

enum class EntityResolutionState {
    SAME_ENTITY,
    DIFFERENT_ENTITY,
    POSSIBLY_SAME,
    UNRESOLVED,
}

enum class SafetySyncState {
    LOCAL,
    QUEUED,
    SYNCING,
    SYNCED,
    FAILED,
}

data class SafetyReport(
    val id: String,
    val reporterRef: String,
    val category: SafetyReportCategory,
    val createdAt: Long,
)

data class SafetyClaim(
    val id: String,
    val reportId: String?,
    val subjectRef: String?,
    val predicate: String,
    val state: ClaimState,
    val methodologyVersion: String,
)

data class SafetyEvent(
    val id: String,
    val eventType: String,
    val occurredAt: Long?,
)

data class SafetyCase(
    val id: String,
    val caseType: String,
    val lifecycle: SafetyCaseLifecycle,
)

data class InstitutionalConductClaim(
    val claimId: String,
    val institutionRef: String,
    val allegationType: InstitutionalAllegationType,
    val state: ClaimState,
    val sourceCount: Int,
    val independentSourceClusters: Int,
    val evidenceCount: Int,
)

data class SafetyFailure(
    val code: String,
    val message: String?,
    val correlationId: String? = null,
    val retryable: Boolean = false,
)

sealed class SafetyGatewayResult {
    data class Accepted(
        val state: String,
        val serverVersion: Long,
        val correlationId: String?,
    ) : SafetyGatewayResult()

    data class Rejected(
        val code: String,
        val message: String?,
        val correlationId: String?,
        val retryable: Boolean,
    ) : SafetyGatewayResult()

    data class TransportFailure(
        val code: String,
        val message: String?,
    ) : SafetyGatewayResult()
}

object SafetyRetryPolicy {
    private const val BASE_MS = 15_000L
    private const val MAX_MS = 15 * 60_000L

    fun delayMillis(attempt: Int, idempotencyKey: String): Long {
        val exponent = (attempt - 1).coerceIn(0, 10)
        val exponential = (BASE_MS shl exponent).coerceAtMost(MAX_MS)
        val jitterWindow = (exponential / 4).coerceAtMost(30_000L)
        val stable = idempotencyKey.hashCode().toLong() and 0x7fffffff
        val jitter = if (jitterWindow == 0L) 0 else stable % jitterWindow
        return (exponential + jitter).coerceAtMost(MAX_MS)
    }
}
