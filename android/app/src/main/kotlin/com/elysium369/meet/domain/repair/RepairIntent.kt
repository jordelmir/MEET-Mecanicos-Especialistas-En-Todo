package com.elysium369.meet.domain.repair

import java.time.Instant
import java.util.UUID

/**
 * Truth State classification for vehicle findings and diagnostic intelligence.
 * Enforces Doctrine #1: AI recommendations cannot create physical truth.
 */
enum class DiagnosticTruthState {
    PHYSICALLY_VERIFIED,
    OBSERVED,
    ESTIMATED,
    DERIVED,
    HYPOTHESIS
}

enum class RepairIntentState {
    OPEN,
    DISPATCHED,
    RESOLVED,
    DISCARDED
}

/**
 * Canonical aggregate bridging vehicle OBD diagnostics with service marketplace intent.
 * Precludes automatic hallucinated work orders by strictly segregating DERIVED (AI)
 * from OBSERVED / PHYSICALLY_VERIFIED (Sensors / Mechanics).
 */
data class RepairIntent(
    val intentId: UUID,
    val vehicleId: UUID,
    val diagnosticSessionId: UUID?,
    val clientId: UUID,
    val observedDtcs: List<String>,
    val reportedSymptoms: List<String>,
    val recommendedActionClass: String,
    val confidence: Double,
    val truthState: DiagnosticTruthState,
    val state: RepairIntentState = RepairIntentState.OPEN,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(confidence in 0.0..1.0) { "Confidence must be normalized between 0.0 and 1.0" }
        require(observedDtcs.isNotEmpty() || reportedSymptoms.isNotEmpty()) {
            "RepairIntent must be grounded in at least one observed DTC or reported symptom"
        }
    }
}
