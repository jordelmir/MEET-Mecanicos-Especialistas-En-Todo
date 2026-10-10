package com.elysium369.meet.education.engine

import kotlinx.serialization.Serializable

/**
 * Multi-tier assessment progression levels.
 */
enum class AssessmentMasteryState {
    NOT_ATTEMPTED,
    ATTEMPTED,
    COMPLETED,
    PASSED,
    MASTERED,
    INDEPENDENTLY_VERIFIED,
}

/**
 * Result of evaluating an assessment attempt.
 */
@Serializable
data class AssessmentAttemptEvaluation(
    val attemptId: String,
    val learnerId: String,
    val competencyId: String,
    val rawScore: Double,
    val isTransferTask: Boolean,
    val priorState: AssessmentMasteryState,
    val newState: AssessmentMasteryState,
    val computedMastery: Double,
    val daysUntilNextRetentionCheck: Int,
    val notes: String,
)

/**
 * Universal Assessment Engine enforcing Bloom 2-Sigma mastery criteria,
 * independent transfer verification, and delayed retention checking (7, 30, 90 days).
 */
object UniversalAssessmentEngine {

    const val NON_TRANSFER_CEILING = 0.750
    const val MASTERY_THRESHOLD = 0.850
    const val PASSING_SCORE = 0.700

    /**
     * Evaluates an assessment submission enforcing constitutional invariants:
     * 1. Without transfer tasks, mastery score cannot exceed 0.750.
     * 2. State cannot transition to MASTERED or INDEPENDENTLY_VERIFIED without successful transfer.
     * 3. Retention checks are scheduled at progressive intervals (7, 30, 90 days).
     */
    fun evaluateAttempt(
        attemptId: String,
        learnerId: String,
        competencyId: String,
        rawScore: Double,
        isTransferTask: Boolean,
        priorState: AssessmentMasteryState,
        priorMastery: Double,
        consecutiveSuccessfulReviews: Int = 0,
    ): AssessmentAttemptEvaluation {
        require(rawScore in 0.0..1.0) { "Raw score must be normalized in [0.0, 1.0]" }

        val passed = rawScore >= PASSING_SCORE

        val (newMastery, newState) = if (!passed) {
            val penalized = maxOf(0.0, priorMastery - 0.20)
            penalized to if (priorState == AssessmentMasteryState.NOT_ATTEMPTED) AssessmentMasteryState.ATTEMPTED else priorState
        } else {
            if (isTransferTask) {
                val boosted = minOf(1.0, maxOf(priorMastery, rawScore))
                val state = if (boosted >= MASTERY_THRESHOLD) {
                    if (priorState == AssessmentMasteryState.MASTERED) AssessmentMasteryState.INDEPENDENTLY_VERIFIED
                    else AssessmentMasteryState.MASTERED
                } else {
                    AssessmentMasteryState.PASSED
                }
                boosted to state
            } else {
                // Invariant: Non-transfer tasks are capped at 0.750 max!
                val capped = minOf(NON_TRANSFER_CEILING, maxOf(priorMastery, rawScore))
                val state = if (capped >= PASSING_SCORE) AssessmentMasteryState.PASSED else AssessmentMasteryState.COMPLETED
                capped to state
            }
        }

        // Delayed retrieval retention intervals (7, 30, 90 days)
        val daysUntilRetention = when (consecutiveSuccessfulReviews) {
            0 -> 7
            1 -> 30
            else -> 90
        }

        val note = when {
            !passed -> "Intento no aprobado. Se programa repaso correctivo inmediato."
            passed && !isTransferTask -> "Aprobado a nivel de comprensión. Requiere tarea de transferencia para alcanzar maestría."
            passed && isTransferTask && newState == AssessmentMasteryState.MASTERED -> "Maestría demostrada en contexto novedoso."
            passed && isTransferTask && newState == AssessmentMasteryState.INDEPENDENTLY_VERIFIED -> "Dominio independiente verificado con retención demostrada."
            else -> "Progreso registrado."
        }

        return AssessmentAttemptEvaluation(
            attemptId = attemptId,
            learnerId = learnerId,
            competencyId = competencyId,
            rawScore = (rawScore * 1000).toLong() / 1000.0,
            isTransferTask = isTransferTask,
            priorState = priorState,
            newState = newState,
            computedMastery = (newMastery * 1000).toLong() / 1000.0,
            daysUntilNextRetentionCheck = daysUntilRetention,
            notes = note,
        )
    }

    /**
     * Determines whether a retention check is currently due.
     */
    fun isRetentionCheckDue(
        lastDemonstratedEpochDay: Long,
        todayEpochDay: Long,
        scheduledIntervalDays: Int,
    ): Boolean {
        return (todayEpochDay - lastDemonstratedEpochDay) >= scheduledIntervalDays
    }
}
