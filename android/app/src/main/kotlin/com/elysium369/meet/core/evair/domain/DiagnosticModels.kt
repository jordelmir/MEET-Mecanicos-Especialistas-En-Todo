package com.elysium369.meet.core.evair.domain

import kotlinx.serialization.Serializable

/**
 * Diagnostic domain models for EVAIR.
 *
 * These models enforce the separation between FACTS and HYPOTHESES.
 * The AI must differentiate what it observed from what it infers.
 *
 * DiagnosticResult is the structured output matching the JSON Schema
 * from the EVAIR master order (Section 19). No text parsing. No regex.
 * Strict contract.
 */
@Serializable
data class DiagnosticResult(
    val severity: DiagnosticSeverity,
    val summary: String,
    val hypotheses: List<DiagnosticHypothesis>,
    val recommendedTests: List<RecommendedDiagnosticTest>,
    val evidenceUsed: List<DiagnosticEvidence> = emptyList(),
    val requestId: String = "",
    val generatedAtMs: Long = 0L,
    val agentId: String = "",
)

@Serializable
enum class DiagnosticSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

/**
 * DiagnosticHypothesis — A ranked candidate cause.
 *
 * The confidence score must be explainable:
 * - What evidence supports it
 * - What evidence contradicts it
 * - What evidence is missing
 *
 * The LLM may propose hypotheses but the deterministic engine
 * computes the final confidence when calibrated rules exist.
 */
@Serializable
data class DiagnosticHypothesis(
    val id: String,
    val cause: String,
    val confidence: Double,
    val supportingEvidence: List<DiagnosticEvidence> = emptyList(),
    val contradictingEvidence: List<DiagnosticEvidence> = emptyList(),
    val missingEvidence: List<String> = emptyList(),
) {
    init {
        require(confidence in 0.0..1.0) {
            "Confidence must be between 0.0 and 1.0, got $confidence"
        }
    }
}

/**
 * DiagnosticEvidence — A verifiable piece of evidence.
 *
 * Every claim must trace back to a source. The user can navigate:
 * CONCLUSION → HYPOTHESIS → EVIDENCE → RAW SOURCE
 */
@Serializable
data class DiagnosticEvidence(
    val source: EvidenceSource,
    val key: String,
    val value: String,
    val timestampMs: Long? = null,
    val reliability: Double = 1.0,
) {
    init {
        require(reliability in 0.0..1.0) {
            "Reliability must be between 0.0 and 1.0, got $reliability"
        }
    }
}

@Serializable
enum class EvidenceSource {
    LIVE_PID,
    MODE_06,
    DTC,
    FREEZE_FRAME,
    VEHICLE_HISTORY,
    USER_REPORT,
    VISUAL_INSPECTION,
    REPAIR_HISTORY,
    BASELINE_COMPARISON,
    ANOMALY_DETECTION,
    DERIVED_METRIC,
}

@Serializable
data class RecommendedDiagnosticTest(
    val testId: String,
    val reason: String,
    val expectedInformationGain: Double? = null,
    val estimatedCost: String? = null,
    val requiredTools: List<String> = emptyList(),
    val riskLevel: ActionRisk = ActionRisk.NONE,
)

/**
 * DiagnosticAgentRequest — What gets sent to the AI agent.
 *
 * NOT the entire database. Specific, bounded retrieval.
 */
@Serializable
data class DiagnosticAgentRequest(
    val requestId: String,
    val vehicleId: String,
    val trigger: DiagnosticTrigger,
    val snapshot: VehicleSnapshot,
    val recentEvents: List<VehicleEvent> = emptyList(),
    val relevantHistory: List<VehicleEpisode> = emptyList(),
    val availableTools: Set<String> = emptySet(),
)

@Serializable
enum class DiagnosticTrigger {
    USER_REQUEST,
    DTC_APPEARED,
    ANOMALY_DETECTED,
    SCHEDULED_CHECK,
    VOICE_COMMAND,
}

/**
 * VehicleEpisode — A significant event in the vehicle's history.
 * Part of the Vehicle Memory system (episodic memory).
 */
@Serializable
data class VehicleEpisode(
    val episodeId: String,
    val vehicleId: String,
    val timestampMs: Long,
    val type: EpisodeType,
    val summary: String,
    val dtcCodes: List<String> = emptyList(),
    val evidence: List<DiagnosticEvidence> = emptyList(),
    val outcome: String? = null,
)

@Serializable
enum class EpisodeType {
    DIAGNOSTIC_SESSION,
    DTC_OCCURRENCE,
    REPAIR,
    MAINTENANCE,
    ANOMALY_DETECTED,
    BASELINE_UPDATED,
    HEALTH_CHECK,
}
