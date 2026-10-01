package com.elysium.nexus.core.device

/**
 * Compatibility verification level for IR code sets per AGENTS.md Hard Rule #5.
 */
enum class VerificationStatus {
    IMPORTED_UNREVIEWED,
    STRUCTURALLY_VALID,
    PROTOCOL_VALIDATED,
    UNVERIFIED,
    PARTIALLY_VERIFIED,
    SESSION_VERIFIED,
    VERIFIED_COMMUNITY,
    VERIFIED_LAB,
    REGRESSION,
    BLOCKED
}

/**
 * Data provenance record tracking dataset origin and licensing compliance per ADR-002.
 */
data class CodeProvenance(
    val sourceName: String,
    val sourceUrl: String,
    val licenseSpdx: String,
    val commitSha: String? = null,
    val attributionText: String? = null
)

/**
 * Authoritative command binding linking action key to exact physical signal ID and signal payload.
 */
data class CatalogCommandBinding(
    val bindingId: String,
    val codeSetId: String,
    val action: IrAction,
    val signalId: String,
    val physicalSha256: String,
    val signal: IrSignal,
    val sourceRevisionId: String = "v0.4.0"
)

/**
 * P0.2: SelectedCommandBinding — The single authority for a command binding.
 *
 * Replaces the three parallel representations (commands, commandSignalIds, commandBindings)
 * with one atomic object that carries ALL information needed:
 * - signal data (IrSignal)
 * - signal identity (signalId)
 * - physical fingerprint (physicalSha256)
 * - source provenance (sourceId, sourceRevisionId)
 * - verification status
 *
 * IrCodeSet.selectedCommands is the authoritative map.
 * Consumers must use this instead of the fallback chain:
 *   commandSignalIds[action] ?: commandBindings.firstOrNull { ... }?.signalId
 */
data class SelectedCommandBinding(
    val bindingId: String,
    val codeSetId: String = "",
    val action: IrAction,
    val signalId: String,
    val signal: IrSignal,
    val physicalSha256: String,
    val sourceId: String,
    val sourceRevisionId: String,
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val evidenceLevel: EvidenceLevel = EvidenceLevel.INTERNAL_UNVERIFIED
)

/**
 * §15 Evidence levels for command bindings.
 *
 * Evidence levels form a strict partial order:
 * each level implies all lower levels. Evidence
 * is NEVER auto-promoted; a higher level requires
 * an explicit verification step.
 *
 * Levels (ascending confidence):
 * 1. INTERNAL_UNVERIFIED — imported from source, not yet validated
 * 2. MODEL_INFERRED — passes schema + structural checks
 * 3. WIFI_IDENTITY_MATCHED — device responded via WiFi identity
 * 4. SESSION_VERIFIED — worked during a probe session
 * 5. TV_COMPANION_VERIFIED — confirmed by the device owner
 * 6. WIFI_ORACLE_VERIFIED — WiFi oracle confirmed the device
 * 7. EXTERNAL_HIL_VERIFIED — hardware-in-the-loop test passed
 * 8. LAB_MATRIX_VERIFIED — full matrix tested in lab
 * 9. PRODUCTION_APPROVED — manufacturer verified compatibility
 */
enum class EvidenceLevel(val tier: Int, val displayName: String) {
    INTERNAL_UNVERIFIED(tier = 1, displayName = "Internal / Unverified"),
    MODEL_INFERRED(tier = 2, displayName = "Model Inferred"),
    WIFI_IDENTITY_MATCHED(tier = 3, displayName = "WiFi Identity Matched"),
    SESSION_VERIFIED(tier = 4, displayName = "Session Verified"),
    TV_COMPANION_VERIFIED(tier = 5, displayName = "TV Companion Verified"),
    WIFI_ORACLE_VERIFIED(tier = 6, displayName = "WiFi Oracle Verified"),
    EXTERNAL_HIL_VERIFIED(tier = 7, displayName = "External HIL Verified"),
    LAB_MATRIX_VERIFIED(tier = 8, displayName = "Lab Matrix Verified"),
    PRODUCTION_APPROVED(tier = 9, displayName = "Production Approved");

    /**
     * True if [this] is at least as strong as [other].
     */
    fun isAtLeast(other: EvidenceLevel): Boolean = this.tier >= other.tier

    /**
     * True if [this] can be promoted to [target]
     * in a single step (one tier at most).
     */
    fun canPromoteTo(target: EvidenceLevel): Boolean =
        target.tier == this.tier + 1

    companion object {
        /**
         * The minimum level required for production use.
         */
        val PRODUCTION_MINIMUM: EvidenceLevel = EXTERNAL_HIL_VERIFIED

        /**
         * The minimum level for IR code sets
         * per AGENTS.md Hard Rule #5.
         */
        val IR_PRODUCTION_MINIMUM: EvidenceLevel = SESSION_VERIFIED

        /**
         * Ordered list from lowest to highest.
         */
        val ASCENDING: List<EvidenceLevel> = entries.sortedBy { it.tier }

        /**
         * Find by tier number. Returns null if tier
         * is out of range.
         */
        fun fromTier(tier: Int): EvidenceLevel? =
            entries.firstOrNull { it.tier == tier }

        /**
         * Find by display name (case-insensitive).
         */
        fun fromDisplayName(name: String): EvidenceLevel? =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}

/**
 * Canonical IR CodeSet definition mapping semantic [IrAction]s to physical [IrSignal]s and exact [signalId]s.
 *
 * P0.2: selectedCommands is the single authority. The three parallel fields
 * (commands, commandSignalIds, commandBindings) are kept for backward compatibility
 * during migration but should be phased out.
 */
data class IrCodeSet(
    val id: String,
    val brand: String,
    val modelPatterns: Set<String>,
    val remoteModels: Set<String>,
    /** P0.2: DEPRECATED — use selectedCommands[action]?.signal instead. */
    val commands: Map<IrAction, IrSignal>,
    /** P0.2: DEPRECATED — use selectedCommands[action]?.signalId instead. */
    val commandSignalIds: Map<IrAction, String> = emptyMap(),
    /** P0.2: DEPRECATED — use selectedCommands instead. */
    val commandBindings: List<CatalogCommandBinding> = emptyList(),
    /** P0.2: The single authority for command bindings. */
    val selectedCommands: Map<IrAction, SelectedCommandBinding> = emptyMap(),
    val provenance: CodeProvenance,
    val verification: VerificationStatus = VerificationStatus.UNVERIFIED
)
