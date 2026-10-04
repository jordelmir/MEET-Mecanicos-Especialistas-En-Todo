package com.elysium369.meet.safety.science.domain

import kotlinx.serialization.Serializable
import java.time.Instant

// ═══════════════════════════════════════════════════════════════════
// Phase 7 — TEMPORAL INTEGRITY
//
// Never trust device time as server truth.
// Never overwrite device timestamps.
// Always compare device vs server.
// ═══════════════════════════════════════════════════════════════════

@Serializable
enum class TemporalState {
    CONSISTENT,
    CLOCK_SKEW,
    FUTURE_DEVICE_TIME,
    MISSING_DEVICE_TIME,
    SERVER_AUTHORITATIVE,
}

object TemporalIntegrityAnalyzer {

    /** Maximum acceptable clock skew: 5 minutes */
    const val MAX_ACCEPTABLE_SKEW_MS = 5 * 60 * 1000L

    fun analyze(
        deviceCapturedAt: Long?,
        serverReceivedAt: Long,
    ): TemporalAnalysisResult {
        if (deviceCapturedAt == null) {
            return TemporalAnalysisResult(
                temporalState = TemporalState.MISSING_DEVICE_TIME,
                clockSkewMs = null,
                serverReceivedAt = serverReceivedAt,
                deviceCapturedAt = null,
            )
        }

        val skew = deviceCapturedAt - serverReceivedAt

        val state = when {
            skew > MAX_ACCEPTABLE_SKEW_MS ->
                TemporalState.FUTURE_DEVICE_TIME

            kotlin.math.abs(skew) > MAX_ACCEPTABLE_SKEW_MS ->
                TemporalState.CLOCK_SKEW

            else ->
                TemporalState.CONSISTENT
        }

        return TemporalAnalysisResult(
            temporalState = state,
            clockSkewMs = skew,
            serverReceivedAt = serverReceivedAt,
            deviceCapturedAt = deviceCapturedAt,
        )
    }
}

@Serializable
data class TemporalAnalysisResult(
    val temporalState: TemporalState,
    val clockSkewMs: Long?,
    val serverReceivedAt: Long,
    val deviceCapturedAt: Long?,
)

// ═══════════════════════════════════════════════════════════════════
// Phase 8 — SOURCE LINEAGE
//
// 10 copies of one Reuters article ≠ 10 independent sources.
// Source lineage tracks the derivation tree.
// ═══════════════════════════════════════════════════════════════════

@Serializable
enum class SourceDerivationType {
    ORIGINAL,
    DERIVED,
    REPUBLICATION,
    INDEPENDENT_ACQUISITION,
    UNKNOWN,
}

object SourceLineageAnalyzer {

    /**
     * Given a set of sources with lineage IDs,
     * count truly independent underlying sources.
     *
     * Sources sharing the same lineageId are ONE source,
     * regardless of how many instances exist.
     */
    fun countIndependentSources(
        sources: List<SourceWithLineage>,
    ): IndependenceResult {
        val groups = sources.groupBy { it.sourceLineageId }
        val independentCount = groups.size
        val totalInstances = sources.size
        val duplicateGroups = groups.filter { it.value.size > 1 }

        return IndependenceResult(
            totalInstances = totalInstances,
            independentSources = independentCount,
            duplicateGroupCount = duplicateGroups.size,
            duplicateGroups = duplicateGroups.mapValues { it.value.size },
        )
    }
}

@Serializable
data class SourceWithLineage(
    val sourceId: String,
    val sourceLineageId: String,
    val derivationType: SourceDerivationType,
)

@Serializable
data class IndependenceResult(
    val totalInstances: Int,
    val independentSources: Int,
    val duplicateGroupCount: Int,
    val duplicateGroups: Map<String, Int>,
)

// ═══════════════════════════════════════════════════════════════════
// Phase 14 — REMOTE FEATURE GATES
//
// Server remains authoritative.
// A disabled server gate MUST override local cache.
// ═══════════════════════════════════════════════════════════════════

interface SafetyScienceFeatureGateRepository {
    suspend fun isEnabled(gate: SafetyScienceGate): Boolean
    suspend fun refreshFromServer()
}

enum class SafetyScienceGate {
    SAFETY_SCIENCE,
    SAFETY_SCIENCE_CLAIMS,
    SAFETY_SCIENCE_RESEARCH,
    SAFETY_SCIENCE_REPLICATION,
    SAFETY_SCIENCE_PUBLICATION,
    SAFETY_SCIENCE_AI,
    SAFETY_SCIENCE_CHECKPOINT,
    SAFETY_SCIENCE_PUBLIC_PROJECTION,
}

// ═══════════════════════════════════════════════════════════════════
// Phase 15 — PUBLICATION AUTHORITY
//
// Publication state machine.
// reviewerA != reviewerB
// reviewer != publisher
// Publication decision MUST be server-generated.
// ═══════════════════════════════════════════════════════════════════

@Serializable
enum class PublicationState {
    DRAFT,
    UNDER_REVIEW,
    PEER_REVIEWED,
    PUBLICATION_ELIGIBLE,
    PUBLISHED,
    CORRECTED,
    RETRACTED,
}

object PublicationAuthorityValidator {

    /**
     * Validates that a publication transition is allowed.
     * Returns null if valid, or an error string.
     */
    fun validateTransition(
        from: PublicationState,
        to: PublicationState,
        reviewerId: String?,
        publisherId: String?,
        firstReviewerId: String?,
    ): String? {
        // Allowed transitions
        val allowed = when (from) {
            PublicationState.DRAFT -> to == PublicationState.UNDER_REVIEW
            PublicationState.UNDER_REVIEW -> to == PublicationState.PEER_REVIEWED
            PublicationState.PEER_REVIEWED -> to == PublicationState.PUBLICATION_ELIGIBLE
            PublicationState.PUBLICATION_ELIGIBLE -> to == PublicationState.PUBLISHED
            PublicationState.PUBLISHED -> to in setOf(PublicationState.CORRECTED, PublicationState.RETRACTED)
            PublicationState.CORRECTED -> to == PublicationState.RETRACTED
            PublicationState.RETRACTED -> false // terminal
        }

        if (!allowed) {
            return "ILLEGAL_PUBLICATION_TRANSITION: $from -> $to"
        }

        // Two-person rule for review
        if (to == PublicationState.PEER_REVIEWED && reviewerId != null && firstReviewerId != null) {
            if (reviewerId == firstReviewerId) {
                return "SAME_REVIEWER: second reviewer must differ from first"
            }
        }

        // Publisher != reviewer
        if (to == PublicationState.PUBLISHED && publisherId != null && reviewerId != null) {
            if (publisherId == reviewerId) {
                return "PUBLISHER_IS_REVIEWER: publisher must differ from reviewer"
            }
        }

        return null // valid
    }
}
