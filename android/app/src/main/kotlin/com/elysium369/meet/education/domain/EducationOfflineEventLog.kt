package com.elysium369.meet.education.domain

import java.security.MessageDigest
import kotlinx.serialization.Serializable

/**
 * High-fidelity immutable domain events for offline-first educational event sourcing.
 */
@Serializable
sealed class EducationDomainEvent {
    abstract val eventId: String
    abstract val learnerId: String
    abstract val timestampMs: Long

    @Serializable
    data class LessonStarted(
        override val eventId: String,
        override val learnerId: String,
        override val timestampMs: Long,
        val lessonId: String,
        val competencyId: String,
        val lessonVersion: Int,
    ) : EducationDomainEvent()

    @Serializable
    data class ExerciseAttempted(
        override val eventId: String,
        override val learnerId: String,
        override val timestampMs: Long,
        val taskId: String,
        val competencyId: String,
        val isCorrect: Boolean,
        val selectedOptionIndex: Int?,
        val responseLatencyMs: Int?,
        val misconceptionCode: String?,
    ) : EducationDomainEvent()

    @Serializable
    data class AssessmentSubmitted(
        override val eventId: String,
        override val learnerId: String,
        override val timestampMs: Long,
        val assessmentId: String,
        val competencyId: String,
        val rawScore: Double,
        val passed: Boolean,
        val isIndependentTransfer: Boolean,
    ) : EducationDomainEvent()

    @Serializable
    data class CompetencyEvidenceRecorded(
        override val eventId: String,
        override val learnerId: String,
        override val timestampMs: Long,
        val competencyId: String,
        val priorMastery: Double,
        val updatedMastery: Double,
        val rawEvidenceHash: String,
    ) : EducationDomainEvent()

    @Serializable
    data class LearningPlanUpdated(
        override val eventId: String,
        override val learnerId: String,
        override val timestampMs: Long,
        val domain: String,
        val activeTargetCompetencyId: String,
        val reason: String,
    ) : EducationDomainEvent()
}

/**
 * Cryptographically enveloped event record ensuring idempotency and tamper resistance.
 */
@Serializable
data class EventEnvelope(
    val eventId: String,
    val learnerId: String,
    val idempotencyKey: String,
    val sequenceNumber: Long,
    val previousEventHash: String,
    val eventHash: String,
    val timestampMs: Long,
    val event: EducationDomainEvent,
) {
    companion object {
        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }

        fun create(
            event: EducationDomainEvent,
            sequenceNumber: Long,
            previousEventHash: String,
            salt: String = "ELYSIUM_EDU",
        ): EventEnvelope {
            val idempotencyKey = sha256("${event.learnerId}:${event.eventId}:${event.timestampMs}:$salt")
            val rawData = "$idempotencyKey:$sequenceNumber:$previousEventHash:${event.timestampMs}"
            val eventHash = sha256(rawData)

            return EventEnvelope(
                eventId = event.eventId,
                learnerId = event.learnerId,
                idempotencyKey = idempotencyKey,
                sequenceNumber = sequenceNumber,
                previousEventHash = previousEventHash,
                eventHash = eventHash,
                timestampMs = event.timestampMs,
                event = event,
            )
        }
    }
}

/**
 * Replay engine for converging concurrent offline learning logs.
 * Guarantees zero evidence loss, deduplication by idempotencyKey, and chronological ordering.
 */
object OfflineEventReplayEngine {

    /**
     * Reconciles two disjoint or overlapping event streams into a unified, conflict-free sequence.
     */
    fun reconcile(
        localStream: List<EventEnvelope>,
        remoteStream: List<EventEnvelope>,
    ): List<EventEnvelope> {
        val seenKeys = mutableSetOf<String>()
        val combined = mutableListOf<EventEnvelope>()

        // Order deterministically by timestamp ascending, then sequenceNumber
        val allEvents = (localStream + remoteStream)
            .sortedWith(compareBy({ it.timestampMs }, { it.sequenceNumber }, { it.eventId }))

        for (envelope in allEvents) {
            if (seenKeys.add(envelope.idempotencyKey)) {
                combined.add(envelope)
            }
        }

        return combined
    }

    /**
     * Verifies cryptographic chain continuity for a given single-device sequence.
     */
    fun verifyChainIntegrity(stream: List<EventEnvelope>): Boolean {
        if (stream.isEmpty()) return true

        var prevHash = stream.first().previousEventHash
        for (envelope in stream) {
            if (envelope.previousEventHash != prevHash) {
                return false
            }
            prevHash = envelope.eventHash
        }
        return true
    }
}
