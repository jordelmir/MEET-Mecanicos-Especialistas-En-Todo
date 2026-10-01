package com.elysium.nexus.fabric.ranking

import com.elysium.nexus.core.device.EvidenceLevel
import com.elysium.nexus.fabric.canonical.DeviceId

// ─── §52 Candidate Ranking V2 ────────────────────────────────────────────────

/**
 * Bayesian ranking for IR candidates.
 *
 * Instead of simple popularity ranking, this uses:
 * ```
 * P(candidate works | model, platform, region, firmware, evidence)
 * ```
 *
 * Factors:
 * - exact model match
 * - remote model match
 * - firmware version
 * - region
 * - OEM family
 * - WiFi identity
 * - past local success
 * - community success
 * - HIL verification
 * - regression count
 * - recent failures
 */
data class CandidateRanking(
    val candidateId: String,
    val codeSetId: String,
    val signalId: String,

    // ── Bayesian Score ────────────────────────────────────
    val priorProbability: Double,
    val likelihoodRatio: Double,
    val posteriorProbability: Double,

    // ── Component Scores ──────────────────────────────────
    val modelMatchScore: Double,
    val firmwareMatchScore: Double,
    val regionMatchScore: Double,
    val evidenceScore: Double,
    val communityScore: Double,
    val localSuccessScore: Double,
    val regressionPenalty: Double,

    // ── Metadata ──────────────────────────────────────────
    val evidenceLevel: EvidenceLevel,
    val totalTests: Int,
    val successCount: Int,
    val failureCount: Int,
    val regressionCount: Int,
    val lastTestedMs: Long?,
    val lastSuccessMs: Long?,
    val lastFailureMs: Long?,

    // ── Composite ─────────────────────────────────────────
    val finalScore: Double,
    val confidence: Double,
    val rank: Int
)

// ─── §53 Active Learning ─────────────────────────────────────────────────────

/**
 * Active learning strategy for candidate selection.
 *
 * Instead of testing candidates in order, select the next
 * candidate that maximizes:
 * ```
 * probability of success
 * + information gained
 * - user disruption
 * ```
 *
 * This can dramatically reduce setup time.
 */
sealed class SelectionStrategy {

    /** Test the highest-scoring candidate first. */
    data object HighestScore : SelectionStrategy()

    /** Test the candidate that maximizes information gain. */
    data object MaximumInformationGain : SelectionStrategy()

    /** Test the candidate with highest expected success probability. */
    data object HighestExpectedSuccess : SelectionStrategy()

    /** Minimize expected user disruption. */
    data object MinimumDisruption : SelectionStrategy()

    /** Balanced: score × (1 - disruption) × information_gain. */
    data object Balanced : SelectionStrategy()

    /** Custom scoring function. */
    data class Custom(
        val scorer: (CandidateRanking, UserContext) -> Double
    ) : SelectionStrategy()
}

data class UserContext(
    val userPresent: Boolean = true,
    val interruptionTolerance: InterruptionTolerance = InterruptionTolerance.LOW,
    val timeAvailableMs: Long = 30_000L,
    val previousAttempts: Int = 0
)

enum class InterruptionTolerance {
    NONE,      // No interruptions (e.g., movie playing)
    LOW,       // Minimal interruptions (e.g., browsing)
    MEDIUM,    // Moderate interruptions (e.g., setup)
    HIGH       // Full interruptions allowed (e.g., first-time setup)
}

// ─── §54 WiFi Oracle IR Autocalibration ──────────────────────────────────────

/**
 * Formalized WiFi Oracle calibration pipeline.
 *
 * ```
 * identify TV via LAN
 *   → rank IR candidates
 *   → read observable state
 *   → send IR candidate
 *   → read state again
 *   → causal change detected?
 *     → yes: candidate verified
 *     → no: try next candidate
 * ```
 *
 * Result: `WIFI_ORACLE_VERIFIED` evidence level.
 */
data class WifiOracleCalibration(
    val deviceId: DeviceId,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,

    // ── Discovery ─────────────────────────────────────────
    val tvIdentified: Boolean,
    val identificationMethod: String?,

    // ── State Read ────────────────────────────────────────
    val observableCapabilities: Set<String>,
    val initialState: Map<String, String>,

    // ── Candidates Tested ─────────────────────────────────
    val candidatesTested: List<CalibrationCandidate>,
    val totalCandidates: Int,
    val verifiedCandidates: Int,

    // ── Result ────────────────────────────────────────────
    val result: CalibrationResult,
    val verifiedSignalId: String?,
    val verifiedCodeSetId: String?,
    val evidenceGenerated: EvidenceLevel?
)

data class CalibrationCandidate(
    val codeSetId: String,
    val signalId: String,
    val action: String,
    val attemptNumber: Int,

    // ── State Change ──────────────────────────────────────
    val stateBefore: Map<String, String>,
    val stateAfter: Map<String, String>,
    val causalChangeDetected: Boolean,
    val changeMagnitude: Double,

    // ── Timing ────────────────────────────────────────────
    val sentAtMs: Long,
    val stateReadAtMs: Long,
    val latencyMs: Double,

    // ── Result ────────────────────────────────────────────
    val verified: Boolean,
    val failureReason: String?
)

enum class CalibrationResult {
    /** Candidate verified via WiFi state change. */
    VERIFIED,

    /** No candidates produced observable state change. */
    NO_CANDIDATE_VERIFIED,

    /** WiFi state reading not available. */
    WIFI_STATE_UNAVAILABLE,

    /** Device not identifiable via LAN. */
    DEVICE_NOT_IDENTIFIED,

    /** Calibration timed out. */
    TIMEOUT,

    /** Calibration was interrupted by user. */
    USER_CANCELLED,

    /** Partial calibration (some actions verified). */
    PARTIAL
}

// ─── §18 Firmware-Aware Compatibility ────────────────────────────────────────

/**
 * Tracks compatibility evidence per firmware version.
 *
 * Example:
 * ```
 * Firmware 4.2 → codeSet A
 * Firmware 5.0 → codeSet B
 * ```
 *
 * Future ranking learns from these transitions.
 */
data class FirmwareCompatibility(
    val deviceId: DeviceId,
    val firmwareVersion: String,
    val firmwareFamily: String?,

    // ── Compatibility ─────────────────────────────────────
    val codeSetId: String,
    val signalId: String,
    val transport: String,

    // ── Evidence ──────────────────────────────────────────
    val evidenceLevel: EvidenceLevel,
    val testResult: TestResult,
    val testedAtMs: Long,
    val testedBy: String,  // "local", "community", "hil", "wifi_oracle"

    // ── Context ───────────────────────────────────────────
    val region: String?,
    val modelNumber: String?,
    val notes: String = ""
)

enum class TestResult {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE,
    NOT_TESTED,
    REGRESSION
}

/**
 * Firmware transition record.
 *
 * When a device's firmware changes, we track what happened
 * to its compatibility.
 */
data class FirmwareTransition(
    val deviceId: DeviceId,
    val fromFirmware: String,
    val toFirmware: String,
    val transitionAtMs: Long,

    // ── Before ────────────────────────────────────────────
    val previousCodeSetId: String,
    val previousEvidenceLevel: EvidenceLevel,

    // ── After ─────────────────────────────────────────────
    val newCodeSetId: String?,
    val newEvidenceLevel: EvidenceLevel?,
    val compatible: Boolean,

    // ── Action ────────────────────────────────────────────
    val actionTaken: String,  // "migrated", "revalidated", "escalated"
    val notes: String = ""
)

// ─── §19 Profile Auto-Revalidation ───────────────────────────────────────────

/**
 * When the catalog is updated, profiles must be revalidated.
 *
 * ```
 * oldCatalogHash != newCatalogHash
 *   → revalidate every binding
 * ```
 *
 * For each binding:
 * ```
 * signalId exists?
 *   → yes: fingerprint same?
 *     → yes: keep
 *     → no: find exact physical fingerprint equivalent
 *       → found: migrate
 *       → not found: needsRevalidation
 *   → no: needsRevalidation
 * ```
 */
data class ProfileRevalidation(
    val profileId: String,
    val catalogHashAtInstall: String,
    val currentCatalogHash: String,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,

    // ── Binding Results ───────────────────────────────────
    val bindingResults: List<BindingRevalidationResult>,
    val totalBindings: Int,
    val keptBindings: Int,
    val migratedBindings: Int,
    val revalidationNeeded: Int,
    val failedBindings: Int,

    // ── Result ────────────────────────────────────────────
    val status: RevalidationStatus
)

data class BindingRevalidationResult(
    val bindingId: String,
    val signalId: String,
    val action: String,

    // ── Source ────────────────────────────────────────────
    val catalogHashAtInstall: String,
    val sourceRevisionAtInstall: String?,

    // ── Current ───────────────────────────────────────────
    val currentCatalogHash: String,
    val currentSourceRevision: String?,

    // ── Result ────────────────────────────────────────────
    val signalExists: Boolean,
    val fingerprintMatch: Boolean,
    val equivalentFound: Boolean,
    val equivalentSignalId: String?,

    // ── Action ────────────────────────────────────────────
    val actionTaken: String,  // "kept", "migrated", "revalidate", "failed"
    val notes: String = ""
)

enum class RevalidationStatus {
    NOT_NEEDED,
    IN_PROGRESS,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED
}
