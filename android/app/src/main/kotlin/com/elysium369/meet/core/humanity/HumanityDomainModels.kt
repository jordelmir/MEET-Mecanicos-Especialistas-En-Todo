package com.elysium369.meet.core.humanity

/**
 * Truth hierarchy for all humanity and diagnostic knowledge.
 * AI or unverified sources NEVER elevate truth state to AUTHORITATIVE.
 */
enum class TruthState {
    AUTHORITATIVE,
    OBSERVED,
    PEER_REVIEWED,
    DERIVED,
    EXPERT_CONSENSUS,
    ESTIMATED,
    ANECDOTAL,
    DISPUTED,
    HYPOTHESIS,
    UNKNOWN,
}

/**
 * Execution truth state for physical or simulation activities.
 * Prevents virtual simulations from pretending to be physical vehicle actions.
 */
enum class ExecutionTruthState {
    NOT_EXECUTED,
    SIMULATED,
    GUIDED,
    OBSERVED,
    PHYSICALLY_VERIFIED,
}

/**
 * Deterministic safety clearance requirements.
 * Hardcoded safety kernel evaluates actions against these levels before LLM processing.
 */
enum class SafetyLevel {
    KNOWLEDGE_ONLY,
    SIMULATION_SAFE,
    LOW_RISK_PRACTICE,
    SUPERVISED_REQUIRED,
    LICENSE_REQUIRED,
    PROHIBITED_UNSUPERVISED,
}

/**
 * Progressive mastery levels for human capability records.
 */
enum class CapabilityLevel(val levelIndex: Int, val displayName: String) {
    L0_UNKNOWN(0, "Desconocido"),
    L1_EXPOSED(1, "Expuesto"),
    L2_UNDERSTOOD(2, "Comprendido"),
    L3_SIMULATED(3, "Simulado con Éxito"),
    L4_GUIDED_PRACTICE(4, "Práctica Guiada"),
    L5_DEMONSTRATED(5, "Demostrado"),
    L6_INDEPENDENT(6, "Autónomo / Independiente"),
    L7_EXPERT_VERIFIED(7, "Verificado por Experto"),
    L8_TEACHER(8, "Instructor / Maestro"),
}

/**
 * High-level knowledge domain (Automotive, Electrical, HVAC, Plumbing, etc.)
 */
data class KnowledgeDomain(
    val id: String,
    val name: String,
    val description: String,
    val parentDomainId: String? = null,
    val iconGlyph: String = "⚙",
)

/**
 * Atomic computable unit of human knowledge with strict provenance.
 */
data class KnowledgeNode(
    val id: String,
    val domainId: String,
    val title: String,
    val summary: String,
    val truthState: TruthState = TruthState.AUTHORITATIVE,
    val safetyLevel: SafetyLevel = SafetyLevel.KNOWLEDGE_ONLY,
    val prerequisiteNodeIds: List<String> = emptyList(),
    val enablesSkillIds: List<String> = emptyList(),
    val sources: List<KnowledgeSource> = emptyList(),
    val version: String = "1.0.0",
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)

/**
 * Provenance reference for authoritative claims.
 */
data class KnowledgeSource(
    val id: String,
    val title: String,
    val authorOrPublisher: String,
    val url: String = "",
    val sourceType: String = "OFFICIAL_STANDARD",
    val citationNote: String = "",
)

/**
 * An actionable skill that can be acquired, practiced, and demonstrated.
 */
data class Skill(
    val id: String,
    val domainId: String,
    val name: String,
    val description: String,
    val requiredKnowledgeIds: List<String> = emptyList(),
    val prerequisiteSkillIds: List<String> = emptyList(),
    val safetyLevel: SafetyLevel = SafetyLevel.LOW_RISK_PRACTICE,
    val minimumEvidenceForMastery: Int = 3,
)

/**
 * Step type within a mission.
 */
enum class MissionStepType {
    KNOWLEDGE_CHECK,
    SIMULATION,
    PHYSICAL_MEASUREMENT,
    VISUAL_INSPECTION,
    DIAGNOSTIC_EXECUTION,
    VERIFICATION,
}

/**
 * Atomic step in a learning/diagnostic mission.
 */
data class MissionStep(
    val stepNumber: Int,
    val title: String,
    val instruction: String,
    val stepType: MissionStepType,
    val safetyCheckNote: String = "",
    val expectedEvidenceType: EvidenceType? = null,
)

/**
 * Real-world or simulated mission connecting knowledge, skills, objects, and evidence.
 */
data class Mission(
    val id: String,
    val domainId: String,
    val title: String,
    val goal: String,
    val requiredSkillIds: List<String> = emptyList(),
    val targetObjectTypes: List<String> = emptyList(),
    val safetyLevel: SafetyLevel = SafetyLevel.LOW_RISK_PRACTICE,
    val steps: List<MissionStep> = emptyList(),
)

/**
 * Canonical evidence category for human capability demonstration.
 */
enum class EvidenceType {
    ASSESSMENT,
    SIMULATION,
    PHOTO,
    MEASUREMENT,
    OBD_SESSION,
    DIAGNOSTIC_REPORT,
    REPAIR_REPORT,
    EXPERT_REVIEW,
}

/**
 * Verifiable evidence ledger item.
 */
data class EvidenceItem(
    val id: String,
    val userId: String,
    val skillId: String,
    val missionId: String? = null,
    val evidenceType: EvidenceType,
    val executionTruth: ExecutionTruthState,
    val evidencePayloadHash: String,
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Capability ledger record demonstrating a user's verified mastery of a skill.
 */
data class CapabilityRecord(
    val userId: String,
    val skillId: String,
    val currentLevel: CapabilityLevel = CapabilityLevel.L0_UNKNOWN,
    val demonstratedEvidenceCount: Int = 0,
    val lastDemonstratedEpochMs: Long = 0L,
    val verifiedByExpert: Boolean = false,
)
