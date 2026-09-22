package com.elysium369.meet.safety.domain

enum class ModerationActionType {
    HOLD,
    REQUEST_MORE_EVIDENCE,
    APPROVE_REDACTED,
    REJECT_PUBLICATION,
    WITHDRAW_PUBLICATION,
    RECORD_CORRECTION,
}

data class ModerationAction(
    val actionId: String,
    val subjectId: String,
    val type: ModerationActionType,
    val reviewerId: String,
    val rationale: String,
    val decidedAt: Long,
    val previousActionHash: String?,
    val actionHash: String,
)

data class PublicationAssessment(
    val hasManualReview: Boolean,
    val hasPrivateLocation: Boolean,
    val hasDirectIdentifiers: Boolean,
    val sourceClusterCount: Int,
    val unresolvedDisputeCount: Int,
)

/** A conservative local policy preview. Only a server projection can publish. */
object SafetyPublicationPolicy {
    fun eligibleDecision(input: PublicationAssessment): PublicationDecision = when {
        !input.hasManualReview -> PublicationDecision.PRIVATE_ONLY
        input.hasPrivateLocation || input.hasDirectIdentifiers -> PublicationDecision.PUBLIC_REDACTED
        input.sourceClusterCount < 2 -> PublicationDecision.AGGREGATE_ONLY
        input.unresolvedDisputeCount > 0 -> PublicationDecision.DELAYED_AGGREGATE
        else -> PublicationDecision.PUBLIC_REDACTED
    }
}

data class CorrectionRecord(
    val correctionId: String,
    val publicationId: String,
    val replacesRevision: Long,
    val reason: String,
    val createdAt: Long,
)

object SafetyCorrectionPolicy {
    fun create(
        correctionId: String,
        publicationId: String,
        replacesRevision: Long,
        reason: String,
        createdAt: Long,
    ): CorrectionRecord {
        require(correctionId.isNotBlank() && publicationId.isNotBlank())
        require(replacesRevision > 0)
        require(reason.trim().length >= 10)
        require(createdAt > 0)
        return CorrectionRecord(correctionId, publicationId, replacesRevision, reason.trim(), createdAt)
    }
}
