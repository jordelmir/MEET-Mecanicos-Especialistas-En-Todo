package com.elysium369.meet.education.domain

import kotlinx.serialization.Serializable

enum class LearnerPrivacyLevel {
    PROTECTED_STUDENT,
    PRIVATE_ADULT,
    RESTRICTED_CHILD,
}

@Serializable
data class LearnerProfile(
    val userId: String,
    val isMinor: Boolean = false,
    val guardianUserId: String? = null,
    val institutionId: String? = null,
    val activePlan: String = "REGULAR",
    val privacyLevel: LearnerPrivacyLevel = LearnerPrivacyLevel.PROTECTED_STUDENT,
)

@Serializable
data class ConceptKnowledgeState(
    val learnerId: String,
    val conceptId: String,
    val masteryEstimate: Double = 0.0,
    val confidence: Double = 0.20,
    val evidenceCount: Int = 0,
    val successfulTransferCount: Int = 0,
    val lastDemonstratedAtEpochMs: Long? = null,
    val retentionEstimate: Double = 1.0,
    val misconceptionCodes: List<String> = emptyList(),
    val prerequisitesSatisfied: Boolean = true,
    val nextReviewEpochDay: Long = 0,
    val epistemicStatus: EpistemicTruthState = EpistemicTruthState.DERIVED,
) {
    init {
        require(masteryEstimate in 0.0..1.0) { "masteryEstimate must be in [0.0, 1.0]" }
        require(confidence in 0.0..1.0) { "confidence must be in [0.0, 1.0]" }
        require(retentionEstimate in 0.0..1.0) { "retentionEstimate must be in [0.0, 1.0]" }
    }

    val isMastered: Boolean
        get() = masteryEstimate >= 0.85 && confidence >= 0.70 && successfulTransferCount >= 1

    val isTransferReady: Boolean
        get() = masteryEstimate >= 0.70 && successfulTransferCount == 0
}

@Serializable
data class LearningEvidenceRecord(
    val id: String,
    val learnerId: String,
    val conceptId: String,
    val taskId: String,
    val isCorrect: Boolean,
    val isTransferTask: Boolean = false,
    val misconceptionCode: String? = null,
    val responseLatencyMs: Int? = null,
    val environmentContext: String = "FORGE_3D_ROOM",
    val rawEvidenceHash: String,
    val timestampEpochMs: Long,
)

@Serializable
data class FrontierConcept(
    val conceptId: String,
    val conceptCode: String,
    val title: String,
    val unitTitle: String,
    val targetMonth: Int,
    val currentMastery: Double,
    val confidence: Double,
    val evidenceCount: Int,
    val needsTransfer: Boolean,
)

@Serializable
data class PersonalLearningFrontier(
    val learnerId: String,
    val subject: String,
    val grade: Int,
    val frontierConcepts: List<FrontierConcept>,
)
