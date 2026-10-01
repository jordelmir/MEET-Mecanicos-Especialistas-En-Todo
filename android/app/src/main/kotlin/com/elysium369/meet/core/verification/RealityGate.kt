package com.elysium369.meet.core.verification

import kotlinx.serialization.Serializable

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   R E A L I T Y   G A T E
 *  ──────────────────────────────────────────────────────────────
 *  Governed by: ELYSIUM MASTER IMPLEMENTATION ORDER — ASCENSION MAXIMA §1
 *
 *  Core principle:
 *  "DOCUMENTATION IS A CLAIM. EXECUTABLE CODE + EXECUTED TEST + EVIDENCE IS PROOF."
 *  No feature or component may be marked DONE based solely on class existence.
 * ══════════════════════════════════════════════════════════════════════
 */
enum class VerificationState {
    DECLARED,
    IMPLEMENTED,
    UNIT_VERIFIED,
    INTEGRATION_VERIFIED,
    PHYSICALLY_VERIFIED,
    PRODUCTION_VERIFIED,
}

@Serializable
data class VerificationEvidence(
    val state: VerificationState,
    val gitSha: String,
    val testId: String?,
    val evidenceUri: String?,
    val verifiedAtEpochMs: Long?,
) {
    val isVerified: Boolean
        get() = state == VerificationState.INTEGRATION_VERIFIED ||
                state == VerificationState.PHYSICALLY_VERIFIED ||
                state == VerificationState.PRODUCTION_VERIFIED

    val hasPhysicalProof: Boolean
        get() = state == VerificationState.PHYSICALLY_VERIFIED ||
                state == VerificationState.PRODUCTION_VERIFIED
}
