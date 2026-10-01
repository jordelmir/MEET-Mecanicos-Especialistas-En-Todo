package com.elysium.nexus.fabric.identity

/**
 * V06-P9: Peer identity evidence model.
 *
 * Discovery must never produce different identities per protocol for the
 * same physical device, and never derive identity from an IP (which
 * changes) or a display name (two equal TVs exist).
 *
 * Identity evidence priority (audit §9):
 * 1. manufacturer stable UUID
 * 2. UPnP UDN
 * 3. pairing identity
 * 4. certificate fingerprint
 * 5. stable vendor device ID
 * 6. Matter node identity
 * 7. Elysium Receiver identity
 * 8. deterministic composite (weakest — only a fallback *identity*,
 *    never a merge signal by itself)
 *
 * Pure Kotlin — JVM-testable.
 */
enum class IdentityEvidenceKind(val priority: Int) {
    ManufacturerStableUuid(1),
    UpnpUdn(2),
    PairingIdentity(3),
    CertificateFingerprint(4),
    VendorDeviceId(5),
    MatterNodeId(6),
    ElysiumReceiverId(7),
    DeterministicComposite(8)
}

/**
 * One piece of identity evidence. [value] must be a normalized,
 * stable string (never an IP, never a display name).
 */
data class PeerIdentityEvidence(
    val kind: IdentityEvidenceKind,
    val value: String
) {
    init {
        require(value.isNotBlank()) { "Identity evidence value must be non-blank." }
    }
}

/** Strong evidence = anything that is NOT the deterministic composite. */
val PeerIdentityEvidence.isStrong: Boolean
    get() = kind != IdentityEvidenceKind.DeterministicComposite

/**
 * One observation of a peer from one discovery source.
 * [ipAddress] is carried for context but is NEVER identity.
 */
data class PeerObservation(
    val source: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val displayName: String? = null,
    val ipAddress: String? = null,
    val evidence: List<PeerIdentityEvidence> = emptyList()
)

/**
 * The canonical identity of a peer, resolved from observations.
 *
 * [stableId] is the highest-priority strong evidence value present,
 * or the deterministic composite when nothing stronger exists.
 */
data class PeerIdentity(
    val stableId: String,
    val label: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val evidence: List<PeerIdentityEvidence>,
    val compositeFallback: Boolean
) {
    val primaryKind: IdentityEvidenceKind?
        get() = evidence.minByOrNull { it.kind.priority }?.kind

    companion object {
        /**
         * Deterministic composite fallback (audit §9 item 8): derived from
         * stable-enough hints (manufacturer + model) PLUS the strongest
         * available evidence value, so two equal TVs do not collide.
         * Never used as a merge signal by [IdentityMergeEngine].
         */
        fun composite(
            manufacturer: String?,
            model: String?,
            strongestEvidence: String?
        ): String {
            val parts = listOfNotNull(
                manufacturer?.trim()?.takeIf { it.isNotBlank() },
                model?.trim()?.takeIf { it.isNotBlank() },
                strongestEvidence?.trim()?.takeIf { it.isNotBlank() }
            )
            val base = if (parts.isEmpty()) "anonymous" else parts.joinToString("|")
            return "composite:${Fingerprint.ofHex(base.toByteArray()).take(24)}"
        }
    }
}

/**
 * Resolve one observation into a canonical [PeerIdentity].
 *
 * - Strong evidence present → stableId = highest-priority strong value.
 * - No strong evidence → deterministic composite fallback
 *   (never IP, never displayName alone).
 */
fun PeerObservation.resolveIdentity(): PeerIdentity {
    val strong = evidence.filter { it.isStrong }
    val stableId = strong.minByOrNull { it.kind.priority }?.value
        ?: PeerIdentity.composite(
            manufacturer = manufacturer,
            model = model,
            strongestEvidence = null
        )
    return PeerIdentity(
        stableId = stableId,
        label = displayName ?: manufacturer ?: model ?: stableId,
        manufacturer = manufacturer,
        model = model,
        evidence = evidence,
        compositeFallback = stableId.startsWith("composite:")
    )
}