package com.elysium369.meet.legal.domain

enum class LegalTriageState { AI_SUGGESTED, USER_CONFIRMED, LAWYER_RECLASSIFIED }
enum class LegalUrgency { NORMAL, HUMAN_REVIEW, TIME_CRITICAL }

data class LegalTaxonomy(
    val version: String,
    val categoryCodes: Set<String>,
) {
    init {
        require(version.isNotBlank())
        require(categoryCodes.isNotEmpty())
        require(categoryCodes.none(String::isBlank))
    }
}

data class LegalTriageResult(
    val primaryCategoryCode: String,
    val alternativeCategoryCodes: List<String>,
    val confidence: Double,
    val urgency: LegalUrgency,
    val riskFlags: Set<String>,
    val taxonomyVersion: String,
    val state: LegalTriageState = LegalTriageState.AI_SUGGESTED,
) {
    val isAuthoritative: Boolean get() = false
}

object LegalTriagePolicy {
    fun validate(result: LegalTriageResult, taxonomy: LegalTaxonomy): Boolean =
        result.taxonomyVersion == taxonomy.version &&
            result.confidence in 0.0..1.0 &&
            result.primaryCategoryCode in taxonomy.categoryCodes &&
            result.alternativeCategoryCodes.all { it in taxonomy.categoryCodes }
}

enum class LawyerCapabilityState { APPROVED, SUSPENDED, EXPIRED, UNVERIFIED }

data class LegalExposureEligibility(
    val authenticated: Boolean,
    val lawyerCapability: LawyerCapabilityState,
    val categoryEligible: Boolean,
    val jurisdictionEligible: Boolean,
    val credentialFresh: Boolean,
)

object LegalExchangePolicy {
    fun mayReceiveMatter(eligibility: LegalExposureEligibility): Boolean =
        eligibility.authenticated &&
            eligibility.lawyerCapability == LawyerCapabilityState.APPROVED &&
            eligibility.categoryEligible &&
            eligibility.jurisdictionEligible &&
            eligibility.credentialFresh

    fun maySubmitOffer(
        eligibility: LegalExposureEligibility,
        conflictDecision: LegalConflictDecision,
    ): Boolean = mayReceiveMatter(eligibility) && conflictDecision == LegalConflictDecision.CLEAR

    fun disclosureLevel(conflictDecision: LegalConflictDecision, engaged: Boolean): LegalDisclosureLevel = when {
        engaged -> LegalDisclosureLevel.ENGAGED
        conflictDecision == LegalConflictDecision.CLEAR -> LegalDisclosureLevel.CONFLICT_CLEARED
        else -> LegalDisclosureLevel.TRIAGE_ONLY
    }
}

enum class LegalCaseClockType {
    PROFESSIONAL_ACTION,
    COURT_WAIT,
    CLIENT_WAIT,
    COUNTERPARTY_WAIT,
    AUTHORITY_WAIT,
    THIRD_PARTY_WAIT,
    PAUSED,
    UNKNOWN,
}

data class LegalCaseClockSegment(
    val type: LegalCaseClockType,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
) {
    init { require(endedAtEpochMs >= startedAtEpochMs) }
    val durationMs: Long get() = endedAtEpochMs - startedAtEpochMs
}

object LegalCaseClock {
    fun professionalActionDuration(segments: List<LegalCaseClockSegment>): Long = segments
        .filter { it.type == LegalCaseClockType.PROFESSIONAL_ACTION }
        .sumOf(LegalCaseClockSegment::durationMs)

    fun professionalInactivity(
        nowEpochMs: Long,
        lastProfessionalActionAtEpochMs: Long,
        openExternalWait: LegalCaseClockType?,
    ): Long? = if (openExternalWait != null && openExternalWait != LegalCaseClockType.PROFESSIONAL_ACTION) {
        null
    } else {
        (nowEpochMs - lastProfessionalActionAtEpochMs).coerceAtLeast(0)
    }
}

enum class VerifiedTransactionState { NOT_ELIGIBLE, COMPLETED, DISPUTED_RESOLVED }

object ReputationPolicy {
    fun mayReview(
        reviewerIsParticipant: Boolean,
        transactionState: VerifiedTransactionState,
        existingReviewCount: Int,
    ): Boolean = reviewerIsParticipant &&
        transactionState in setOf(VerifiedTransactionState.COMPLETED, VerifiedTransactionState.DISPUTED_RESOLVED) &&
        existingReviewCount == 0

    fun bayesianRating(
        ratingMean: Double?,
        ratingCount: Int,
        globalMean: Double,
        priorWeight: Int,
    ): Double? {
        if (ratingMean == null || ratingCount <= 0) return null
        require(priorWeight > 0)
        return ((ratingCount * ratingMean) + (priorWeight * globalMean)) / (ratingCount + priorWeight)
    }
}

object MetricsMutationPolicy {
    fun clientMayMutate(): Boolean = false
}

data class LegalOfferAcceptance(
    val acceptedOfferId: String,
    val rejectedOfferIds: Set<String>,
    val engagementCreated: Boolean,
)

object LegalOfferAcceptancePolicy {
    fun accept(submittedOfferIds: Set<String>, selectedOfferId: String): LegalOfferAcceptance {
        require(selectedOfferId in submittedOfferIds)
        return LegalOfferAcceptance(
            acceptedOfferId = selectedOfferId,
            rejectedOfferIds = submittedOfferIds - selectedOfferId,
            engagementCreated = true,
        )
    }
}

enum class TransactionCancellationActor { PROVIDER, CUSTOMER, SYSTEM }

object PerformanceAttributionPolicy {
    fun providerCancellationIncrement(actor: TransactionCancellationActor): Int =
        if (actor == TransactionCancellationActor.PROVIDER) 1 else 0
}
