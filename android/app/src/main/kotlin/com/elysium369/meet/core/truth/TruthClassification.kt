package com.elysium369.meet.core.truth

import kotlinx.serialization.Serializable

/**
 * Universal Truth & Authority Matrix for MEET / Elysium Vanguard.
 *
 * Enforces the NO SYNTHETIC SUCCESS doctrine across all subsystems:
 * - Missing evidence ≠ success
 * - Missing measurement ≠ nominal
 * - Missing integration ≠ simulated successful integration
 * - Missing credential ≠ valid credential
 * - Missing action acknowledgment ≠ action executed
 * - Missing financial record ≠ zero cost
 */
@Serializable
enum class TruthState {
    OBSERVED,       // Direct physical sensor/ECU measurement with verified protocol response
    AUTHORITATIVE,  // Cryptographically signed or official backend/registry attested authority
    DERIVED,        // Deterministically computed from verified observed facts (e.g. formula/rules)
    ESTIMATED,      // Model/heuristic estimate with explicit confidence boundary (never stated as fact)
    SIMULATED,      // Test/sandbox/demo environment, not physical hardware
    UNKNOWN,        // No evidence or measurement currently captured
    NOT_INTEGRATED, // Integration or OEM API does not exist or is not configured
    NOT_EXECUTED    // Action was proposed or authorized but not physically dispatched/acknowledged
}

@Serializable
enum class MaturityStage {
    MODEL_EXISTS,
    CLIENT_IMPLEMENTED,
    SERVER_AUTHORITATIVE,
    DEVICE_VERIFIED,
    PHYSICALLY_VERIFIED
}

@Serializable
data class TruthProof<T>(
    val value: T?,
    val truthState: TruthState,
    val maturityStage: MaturityStage,
    val provenance: String,
    val timestampMs: Long = System.currentTimeMillis()
)
