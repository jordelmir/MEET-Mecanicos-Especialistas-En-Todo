package com.elysium369.meet.evidence

import java.security.MessageDigest

enum class CryptoAlgorithm {
    ECDSA_P256,
    ML_DSA_65, // NIST FIPS 204
    HYBRID_ECDSA_MLDSA,
}

data class SignatureEnvelope(
    val algorithm: CryptoAlgorithm,
    val envelopeVersion: Int = 1,
    val keyId: String,
    val signatureHex: String,
    val attestationSha256: String?,
)

data class EvidencePassportRecord(
    val recordId: String,
    val vehicleBindingId: String,
    val merkleRootHash: String,
    val previousPassportHash: String?,
    val timestampMonotonicMs: Long,
    val envelope: SignatureEnvelope,
) {
    fun computeRecordHash(): String {
        val canonicalRepresentation = "$recordId|$vehicleBindingId|$merkleRootHash|${previousPassportHash ?: "ROOT"}|$timestampMonotonicMs|${envelope.algorithm.name}|${envelope.keyId}|${envelope.signatureHex}"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(canonicalRepresentation.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/**
 * VehicleEvidencePassportV2 — Crypto-agile verifiable ledger for automotive diagnostic history.
 * Supports hardware-backed ECDSA P-256 today and post-quantum ML-DSA (FIPS 204) envelopes.
 */
object VehicleEvidencePassportV2 {

    fun verifyChainIntegrity(records: List<EvidencePassportRecord>): Boolean {
        if (records.isEmpty()) return true

        for (i in 1 until records.size) {
            val prev = records[i - 1]
            val curr = records[i]

            val expectedPrevHash = prev.computeRecordHash()
            if (curr.previousPassportHash != expectedPrevHash) {
                return false
            }
        }
        return true
    }

    fun buildNextRecord(
        recordId: String,
        vehicleBindingId: String,
        merkleRootHash: String,
        previousRecord: EvidencePassportRecord?,
        timestampMonotonicMs: Long,
        envelope: SignatureEnvelope,
    ): EvidencePassportRecord {
        return EvidencePassportRecord(
            recordId = recordId,
            vehicleBindingId = vehicleBindingId,
            merkleRootHash = merkleRootHash,
            previousPassportHash = previousRecord?.computeRecordHash(),
            timestampMonotonicMs = timestampMonotonicMs,
            envelope = envelope,
        )
    }
}
