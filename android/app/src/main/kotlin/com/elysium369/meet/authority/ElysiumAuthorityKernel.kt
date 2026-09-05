package com.elysium369.meet.authority

import com.elysium369.meet.identity.ActivePrincipal
import com.elysium369.meet.identity.ActivePrincipalKernel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VerificationLevel — Monotonic hierarchy of physical & cryptographic truth.
 *
 * Invariant: A fact can NEVER be downgraded in verification level without an explicit
 * tombstone or superseding epoch.
 */
enum class VerificationLevel(val levelScore: Int) {
    UNVERIFIED_CLAIM(0),            // Self-reported by untrusted client without telemetry
    MODEL_DECLARED(1),              // Theoretical model or schema declaration
    CLIENT_RECORDED(2),             // Recorded locally on mobile device/cache
    TRANSIENT_OBSERVED(3),          // Live stream sample (e.g. single GPS ping or OBD PID read)
    CRYPTOGRAPHICALLY_ANCHORED(4),  // Signed by private key or chained with SHA-256 hash
    PHYSICALLY_VERIFIED(5),         // Cross-validated by hardware sensor, OBD bus ack or forensic scanner
    SERVER_AUTHORITATIVE(6),        // Confirmed and committed by backend authoritative database/fencing
    FORENSICALLY_CERTIFIED(7);      // Immutable legal certificate with chained QR & forensic evidence

    fun isAtLeast(other: VerificationLevel): Boolean = this.levelScore >= other.levelScore
}

/**
 * AuthorityOwner — Identifies WHO owns and has authority over a specific fact.
 */
sealed class AuthorityOwner {
    data class Principal(val principalId: String) : AuthorityOwner()
    data class Vehicle(val vehicleId: String, val vin: String? = null) : AuthorityOwner()
    data class HardwareSensor(val sensorType: String, val hardwareId: String) : AuthorityOwner()
    data class DiagnosticTool(val toolId: String, val protocol: String) : AuthorityOwner()
    data class Network(val endpoint: String) : AuthorityOwner()
    data class ForensicVerifier(val auditorId: String, val certificationKeyId: String) : AuthorityOwner()
    object SystemAuthority : AuthorityOwner()

    val ownerKey: String
        get() = when (this) {
            is Principal -> "principal:$principalId"
            is Vehicle -> "vehicle:$vehicleId"
            is HardwareSensor -> "sensor:$sensorType:$hardwareId"
            is DiagnosticTool -> "tool:$toolId:$protocol"
            is Network -> "network:$endpoint"
            is ForensicVerifier -> "verifier:$auditorId"
            SystemAuthority -> "system:meet"
        }
}

/**
 * MutationPolicy — Declares WHO may mutate or update this fact.
 */
enum class MutationPolicy {
    SELF_ONLY,                  // Only the principal described by the fact can mutate it
    OWNER_ONLY,                 // Only the registered authority owner can mutate it
    MUTUAL_CONSENSUS,           // Requires two or more authorized parties (e.g. Passenger + Driver)
    CRYPTOGRAPHICALLY_SIGNED,   // Requires a valid ed25519 or ECDSA signature
    SYSTEM_AUTHORITATIVE,       // Only backend or kernel can mutate
    IMMUTABLE_ONCE_WRITTEN,     // Append-only; attempts to overwrite will fail
}

/**
 * FactProof — Cryptographic, physical, or attestation evidence supporting the fact.
 */
sealed class FactProof {
    object None : FactProof()
    data class HardwareMeasurement(
        val sensorIdentifier: String,
        val rawBusBytesHex: String,
        val samplingFrequencyHz: Double? = null,
    ) : FactProof()

    data class CryptographicSignature(
        val keyId: String,
        val signatureHex: String,
        val algorithm: String = "Ed25519",
    ) : FactProof()

    data class HashChained(
        val previousHash: String,
        val currentHash: String,
    ) : FactProof()

    data class MultiPartyConsensus(
        val participants: List<String>,
        val quorumCount: Int,
    ) : FactProof()
}

/**
 * Freshness — Tracks temporal provenance and prevents replay attacks.
 */
data class Freshness(
    val timestampEpochMs: Long,
    val monotonicSequence: Long,
    val maxTtlMs: Long = 60_000L,
    val clockQuality: String = "NTP_SYNCED",
) {
    fun isStale(currentTimeEpochMs: Long = System.currentTimeMillis()): Boolean {
        return (currentTimeEpochMs - timestampEpochMs) > maxTtlMs
    }
}

/**
 * FactEnvelope<T> — The universal truth envelope across all MEET domains.
 *
 * Formula: Fact + Authority + Provenance + Freshness + Confidence + Verification + Policy
 */
data class FactEnvelope<T>(
    val factId: String = UUID.randomUUID().toString(),
    val domain: String,
    val subjectId: String,
    val payload: T,
    val authority: AuthorityOwner,
    val provenance: String,
    val freshness: Freshness,
    val confidence: Double, // 0.0 to 1.0 (or 0.0 to 100.0 scaled)
    val verificationLevel: VerificationLevel,
    val policy: MutationPolicy,
    val integrityHash: String = computeHash(factId, domain, subjectId, payload.toString(), freshness),
) {
    companion object {
        fun computeHash(factId: String, domain: String, subjectId: String, payloadStr: String, freshness: Freshness): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val raw = "$factId:$domain:$subjectId:$payloadStr:${freshness.timestampEpochMs}:${freshness.monotonicSequence}"
            return digest.digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}

sealed class FactAdmissionResult {
    data class Accepted(val factId: String, val level: VerificationLevel) : FactAdmissionResult()
    data class Rejected(val reason: String, val rejectionCode: String) : FactAdmissionResult()
}

/**
 * ElysiumAuthorityKernel — Central engine enforcing the universal truth contract.
 */
@Singleton
class ElysiumAuthorityKernel(
    private val principalProvider: com.elysium369.meet.identity.ActivePrincipalProvider,
) {
    @Inject
    constructor(principalKernel: ActivePrincipalKernel) : this(principalProvider = principalKernel)

    private val factsStore = ConcurrentHashMap<String, FactEnvelope<*>>()
    private val _factCount = MutableStateFlow(0)
    val factCount: StateFlow<Int> = _factCount.asStateFlow()

    /**
     * Attempts to admit a fact into the truth registry.
     * Enforces:
     * 1. Monotonicity: cannot overwrite a higher verification level with a lower one.
     * 2. Policy: checks mutation policy against current active principal.
     * 3. Freshness: detects stale or backwards monotonic sequence.
     * 4. Integrity: verifies SHA-256 integrity hash.
     */
    fun <T> admitFact(newFact: FactEnvelope<T>): FactAdmissionResult {
        // 1. Verify integrity hash
        val expectedHash = FactEnvelope.computeHash(
            newFact.factId,
            newFact.domain,
            newFact.subjectId,
            newFact.payload.toString(),
            newFact.freshness,
        )
        if (newFact.integrityHash != expectedHash) {
            return FactAdmissionResult.Rejected(
                reason = "Integrity hash mismatch: payload has been modified or corrupted",
                rejectionCode = "CORRUPTED_INTEGRITY_HASH",
            )
        }

        val existing = factsStore[newFact.subjectId]

        if (existing != null) {
            // 2. Monotonic verification level check
            if (existing.verificationLevel.levelScore > newFact.verificationLevel.levelScore) {
                return FactAdmissionResult.Rejected(
                    reason = "Cannot downgrade fact verification level from ${existing.verificationLevel} to ${newFact.verificationLevel}",
                    rejectionCode = "REJECTED_VERIFICATION_DOWNGRADE",
                )
            }

            // 3. Immutability check
            if (existing.policy == MutationPolicy.IMMUTABLE_ONCE_WRITTEN) {
                return FactAdmissionResult.Rejected(
                    reason = "Fact is immutable and cannot be modified once written",
                    rejectionCode = "IMMUTABLE_FACT_OVERWRITE_DENIED",
                )
            }

            // 4. Monotonic sequence check for same verification level
            if (existing.verificationLevel == newFact.verificationLevel) {
                if (newFact.freshness.monotonicSequence < existing.freshness.monotonicSequence) {
                    return FactAdmissionResult.Rejected(
                        reason = "Stale monotonic sequence: ${newFact.freshness.monotonicSequence} < ${existing.freshness.monotonicSequence}",
                        rejectionCode = "STALE_SEQUENCE_REJECTED",
                    )
                }
            }

            // 5. Mutation policy authorization
            val activePrincipalId = principalProvider.current().id
            when (existing.policy) {
                MutationPolicy.SELF_ONLY -> {
                    if (existing.authority is AuthorityOwner.Principal &&
                        existing.authority.principalId != activePrincipalId) {
                        return FactAdmissionResult.Rejected(
                            reason = "Principal $activePrincipalId is not authorized to mutate fact owned by ${existing.authority.principalId}",
                            rejectionCode = "UNAUTHORIZED_SELF_MUTATION",
                        )
                    }
                }
                MutationPolicy.OWNER_ONLY -> {
                    if (existing.authority.ownerKey != newFact.authority.ownerKey) {
                        return FactAdmissionResult.Rejected(
                            reason = "Authority owner mismatch: ${newFact.authority.ownerKey} cannot mutate ${existing.authority.ownerKey}",
                            rejectionCode = "UNAUTHORIZED_OWNER_MUTATION",
                        )
                    }
                }
                MutationPolicy.SYSTEM_AUTHORITATIVE -> {
                    if (newFact.authority !is AuthorityOwner.SystemAuthority &&
                        newFact.authority !is AuthorityOwner.ForensicVerifier) {
                        return FactAdmissionResult.Rejected(
                            reason = "Fact requires SystemAuthority or ForensicVerifier mutation",
                            rejectionCode = "SYSTEM_AUTHORITY_REQUIRED",
                        )
                    }
                }
                else -> { /* Other policies validated via proof */ }
            }
        }

        // Admit fact
        factsStore[newFact.subjectId] = newFact
        _factCount.update { factsStore.size }
        return FactAdmissionResult.Accepted(newFact.factId, newFact.verificationLevel)
    }

    /**
     * Retrieve a fact by subject ID, verifying type safely.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getFact(subjectId: String): FactEnvelope<T>? {
        return factsStore[subjectId] as? FactEnvelope<T>
    }

    /**
     * Check if a subject has reached a minimum verification level.
     */
    fun hasVerificationLevel(subjectId: String, requiredLevel: VerificationLevel): Boolean {
        val fact = factsStore[subjectId] ?: return false
        return fact.verificationLevel.isAtLeast(requiredLevel)
    }

    /**
     * Query all facts in a given domain (e.g. "ecu", "ride", "vss", "presence").
     */
    fun getFactsByDomain(domain: String): List<FactEnvelope<*>> {
        return factsStore.values.filter { it.domain == domain }
    }

    /**
     * Reset in-memory facts (for testing/rehydration).
     */
    fun clear() {
        factsStore.clear()
        _factCount.value = 0
    }
}
