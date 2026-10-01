package com.elysium.nexus.fabric.discovery

import android.util.Log
import com.elysium.nexus.fabric.identity.DeviceIdentityRepository
import com.elysium.nexus.fabric.identity.IdentityMergeEngine
import com.elysium.nexus.fabric.identity.IdentityMergeResult
import com.elysium.nexus.fabric.identity.PeerIdentity
import com.elysium.nexus.fabric.identity.PeerIdentityEvidence
import com.elysium.nexus.fabric.identity.PeerObservation
import com.elysium.nexus.fabric.identity.IdentityEvidenceKind
import com.elysium.nexus.fabric.identity.resolveIdentity

private const val TAG = "DiscoveryIdentityBridge"

/**
 * V0.6.2 PR4 Phase 17 — Bridge between discovery pipeline and identity engine.
 *
 * Converts [RawDiscoveryRecord] into [PeerObservation] with proper
 * [PeerIdentityEvidence], feeds observations through [IdentityMergeEngine]
 * for cross-protocol deduplication, and persists resolved identities
 * via [DeviceIdentityRepository].
 *
 * §9: Discovery must never produce different identities per protocol for
 * the same physical device. The bridge ensures identity evidence is
 * extracted from stable fields (serial, UDN, MAC, Matter node ID) and
 * fed through the merge engine before any DeviceTwin is emitted.
 */
class DiscoveryIdentityBridge(
    private val identityRepository: DeviceIdentityRepository? = null
) {

    /**
     * Convert a raw discovery record into a [PeerObservation] with
     * proper identity evidence. IP and display name are NEVER identity.
     */
    fun toObservation(record: RawDiscoveryRecord): PeerObservation {
        val evidence = mutableListOf<PeerIdentityEvidence>()

        // §9 identity evidence priority order
        record.serialNumber?.takeIf { it.isNotBlank() }?.let {
            evidence.add(PeerIdentityEvidence(IdentityEvidenceKind.VendorDeviceId, it))
        }
        record.upnpUdn?.takeIf { it.isNotBlank() }?.let {
            evidence.add(PeerIdentityEvidence(IdentityEvidenceKind.UpnpUdn, it))
        }
        record.macAddress?.takeIf { it.isNotBlank() }?.let {
            evidence.add(PeerIdentityEvidence(IdentityEvidenceKind.PairingIdentity, it))
        }
        record.matterNodeId?.takeIf { it.isNotBlank() }?.let {
            evidence.add(PeerIdentityEvidence(IdentityEvidenceKind.MatterNodeId, it))
        }
        record.bluetoothAddress?.takeIf { it.isNotBlank() }?.let {
            evidence.add(PeerIdentityEvidence(IdentityEvidenceKind.PairingIdentity, it))
        }
        // Certificate fingerprint from Elysium Link public key (TXT record)
        record.rawProperties["publicKeyB64"]?.takeIf { it.isNotBlank() }?.let {
            evidence.add(PeerIdentityEvidence(IdentityEvidenceKind.CertificateFingerprint, it))
        }

        Log.d(TAG, "toObservation: ${record.providerProtocol} ${record.displayName} evidence=${evidence.map { it.kind.name }}")
        return PeerObservation(
            source = record.providerProtocol.name,
            manufacturer = record.manufacturer,
            model = record.model ?: record.modelNumber,
            displayName = record.friendlyName ?: record.hostname,
            ipAddress = record.ipAddress,
            evidence = evidence
        )
    }

    /**
     * Merge observations from multiple protocols for the same physical
     * device. Returns the resolved [PeerIdentity] or null if ambiguous.
     *
     * This is the core of §9: never produce different identities per
     * protocol for the same physical device.
     */
    fun mergeObservations(observations: List<PeerObservation>): PeerIdentity? {
        if (observations.isEmpty()) return null
        if (observations.size == 1) return observations.first().resolveIdentity()

        // Use IdentityMergeEngine to check if all observations refer to the same device
        var mergedResult: IdentityMergeResult = IdentityMergeEngine.mergeAll(observations)
        return when (mergedResult) {
            is IdentityMergeResult.SamePhysicalDevice -> {
                // All observations agree — resolve canonical identity from strongest evidence
                val allEvidence = observations.flatMap { it.evidence }
                    .sortedBy { it.kind.priority }
                val strongEvidence = allEvidence.filter { it.kind != IdentityEvidenceKind.DeterministicComposite }
                val bestEvidence = strongEvidence.firstOrNull()

                PeerIdentity(
                    stableId = bestEvidence?.value
                        ?: PeerIdentity.composite(
                            manufacturer = observations.firstOrNull()?.manufacturer,
                            model = observations.firstOrNull()?.model,
                            strongestEvidence = null
                        ),
                    label = observations.mapNotNull { it.displayName }.firstOrNull()
                        ?: observations.firstOrNull { it.manufacturer != null }?.manufacturer
                        ?: "Merged Device",
                    manufacturer = observations.mapNotNull { it.manufacturer }.firstOrNull(),
                    model = observations.mapNotNull { it.model }.firstOrNull(),
                    evidence = allEvidence.distinctBy { it.kind },
                    compositeFallback = bestEvidence == null
                )
            }
            is IdentityMergeResult.DifferentPhysicalDevice -> {
                Log.w(TAG, "Observations refer to different physical devices — returning first")
                observations.first().resolveIdentity()
            }
            is IdentityMergeResult.Ambiguous -> {
                Log.w(TAG, "Ambiguous identity merge for ${observations.size} observations")
                observations.first().resolveIdentity()
            }
        }
    }

    /**
     * Process a batch of records for a single stable key: convert to
     * observations, merge identities, and persist the result.
     *
     * Returns the resolved [PeerIdentity] or null.
     */
    suspend fun processBatch(
        stableKey: String,
        records: List<RawDiscoveryRecord>
    ): PeerIdentity? {
        Log.d(TAG, "processBatch: key=$stableKey records=${records.size}")
        val observations = records.map { toObservation(it) }
        val identity = mergeObservations(observations) ?: return null
        Log.d(TAG, "processBatch: resolved stableId=${identity.stableId} label=${identity.label} composite=${identity.compositeFallback}")

        // Persist the resolved identity
        try {
            identityRepository?.remember(
                identity = identity,
                observation = observations.first(),
                observedAtEpochMs = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist identity for $stableKey: ${e.message}")
        }

        return identity
    }
}
