package com.elysium369.meet.safety.science.provenance

import java.time.Instant
import java.util.UUID

/**
 * Binary Merkle tree for batch-verifiable evidence checkpoints.
 *
 * Algorithm:
 * 1. Normalize all leaf hashes (lowercase, trim).
 * 2. Pair leaves left-to-right; odd leaf duplicates itself.
 * 3. Parent = SHA-256("left:right").
 * 4. Repeat until one root remains.
 *
 * This implementation is deterministic and stateless.
 */
class MerkleTree {

    fun root(hashes: List<String>): String {
        require(hashes.isNotEmpty()) { "Cannot compute Merkle root of empty list" }

        var level = hashes.map { normalize(it) }

        while (level.size > 1) {
            val next = ArrayList<String>((level.size + 1) / 2)

            var i = 0
            while (i < level.size) {
                val left = level[i]
                val right = level.getOrElse(i + 1) { left }
                next += sha256("$left:$right")
                i += 2
            }

            level = next
        }

        return level.single()
    }

    private fun normalize(value: String): String =
        value.lowercase().trim()
}

/**
 * Signed checkpoint anchoring a Merkle root to a point in time.
 *
 * The private key MUST NEVER be stored in Supabase or any remote service.
 */
data class MerkleCheckpoint(
    val checkpointId: UUID,
    val rootHash: String,
    val eventCount: Long,
    val firstEventHash: String?,
    val lastEventHash: String?,
    val createdAt: Instant,
    val signatureAlgorithm: String,
    val signatureBase64: String,
)

/**
 * Signs a Merkle root hash using Ed25519.
 *
 * @param privateKey Ed25519 private key — stored ONLY in Android Keystore.
 */
class EvidenceCheckpointSigner(
    private val privateKey: java.security.PrivateKey,
) {

    fun sign(rootHash: String): String {
        val signature = java.security.Signature.getInstance("Ed25519")
        signature.initSign(privateKey)
        signature.update(rootHash.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(signature.sign())
    }
}

/**
 * Verifies a signed Merkle checkpoint.
 *
 * @param publicKey Ed25519 public key.
 */
class EvidenceCheckpointVerifier(
    private val publicKey: java.security.PublicKey,
) {

    fun verify(rootHash: String, signatureBase64: String): Boolean {
        val sig = java.security.Signature.getInstance("Ed25519")
        sig.initVerify(publicKey)
        sig.update(rootHash.toByteArray(Charsets.UTF_8))
        return sig.verify(java.util.Base64.getDecoder().decode(signatureBase64))
    }
}
