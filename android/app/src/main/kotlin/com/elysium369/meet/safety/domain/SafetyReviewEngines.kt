package com.elysium369.meet.safety.domain

enum class SafetySourceClass { CIVIL, JOURNALISTIC, PUBLIC_RECORD, DOCUMENTARY, INSTITUTIONAL }

data class ReconciliationInput(val eventId: String, val sourceClasses: List<SafetySourceClass>)
data class ReconciliationResult(
    val eventId: String,
    val civilSources: Int,
    val mediaSources: Int,
    val publicRecordSources: Int,
    val civilOnly: Boolean,
    val mediaOnly: Boolean,
    val publicRecordOnly: Boolean,
    val requiresReview: Boolean,
)

object SafetyReconciliationEngine {
    fun evaluate(input: ReconciliationInput): ReconciliationResult {
        val civil = input.sourceClasses.count { it == SafetySourceClass.CIVIL }
        val media = input.sourceClasses.count { it == SafetySourceClass.JOURNALISTIC }
        val records = input.sourceClasses.count { it == SafetySourceClass.PUBLIC_RECORD }
        val represented = listOf(civil, media, records).count { it > 0 }
        return ReconciliationResult(
            eventId = input.eventId,
            civilSources = civil,
            mediaSources = media,
            publicRecordSources = records,
            civilOnly = civil > 0 && media == 0 && records == 0,
            mediaOnly = media > 0 && civil == 0 && records == 0,
            publicRecordOnly = records > 0 && civil == 0 && media == 0,
            requiresReview = represented < 2,
        )
    }
}

data class EntityResolutionCandidate(val leftRef: String, val rightRef: String)
data class EntityResolutionDecision(
    val candidate: EntityResolutionCandidate,
    val state: EntityResolutionState,
    val reviewerId: String,
    val rationale: String,
    val decidedAt: Long,
)

object SafetyEntityResolutionPolicy {
    fun recordDecision(
        candidate: EntityResolutionCandidate,
        state: EntityResolutionState,
        reviewerId: String,
        rationale: String,
        decidedAt: Long,
    ): EntityResolutionDecision {
        require(candidate.leftRef.isNotBlank() && candidate.rightRef.isNotBlank())
        require(candidate.leftRef != candidate.rightRef)
        require(reviewerId.isNotBlank())
        require(rationale.trim().length >= 10)
        require(decidedAt > 0)
        return EntityResolutionDecision(candidate, state, reviewerId, rationale.trim(), decidedAt)
    }
}
