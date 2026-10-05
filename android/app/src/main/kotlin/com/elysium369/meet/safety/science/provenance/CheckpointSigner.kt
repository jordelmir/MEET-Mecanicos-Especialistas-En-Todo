package com.elysium369.meet.safety.science.provenance

import java.security.*

// ═══════════════════════════════════════════════════════════════════
// Phase 11 — Ed25519 CHECKPOINT SIGNING AND VERIFICATION (Kotlin)
//
// Parity with TypeScript ed25519-verifier.ts.
// Same canonical payload → same signature verification result.
// ═══════════════════════════════════════════════════════════════════

object CheckpointSigner {

    private const val PROTOCOL = "SAFETY-CUSTODY-V2"

    /**
     * Build the canonical signed payload for a checkpoint.
     * Byte-exact parity with TypeScript buildCheckpointSignedPayload().
     */
    fun buildSignedPayload(
        rootHash: String,
        eventCount: Int,
        firstEventHash: String? = null,
        lastEventHash: String? = null,
    ): ByteArray {
        val canonical =
            "$PROTOCOL-CHECKPOINT\n" +
            "root_hash:${rootHash.lowercase()}\n" +
            "event_count:$eventCount\n" +
            "first_event_hash:${(firstEventHash ?: "NONE").lowercase()}\n" +
            "last_event_hash:${(lastEventHash ?: "NONE").lowercase()}\n"
        return canonical.toByteArray(Charsets.UTF_8)
    }

    /**
     * Sign a checkpoint payload with Ed25519.
     * Returns Base64-encoded signature.
     */
    fun sign(
        privateKey: PrivateKey,
        rootHash: String,
        eventCount: Int,
        firstEventHash: String? = null,
        lastEventHash: String? = null,
    ): ByteArray {
        val payload = buildSignedPayload(rootHash, eventCount, firstEventHash, lastEventHash)
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(privateKey)
        sig.update(payload)
        return sig.sign()
    }

    /**
     * Verify a checkpoint signature with Ed25519.
     * Returns true if the signature is cryptographically valid.
     */
    fun verify(
        publicKey: PublicKey,
        signatureBytes: ByteArray,
        rootHash: String,
        eventCount: Int,
        firstEventHash: String? = null,
        lastEventHash: String? = null,
    ): Boolean {
        val payload = buildSignedPayload(rootHash, eventCount, firstEventHash, lastEventHash)
        return try {
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(publicKey)
            sig.update(payload)
            sig.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Generate a new Ed25519 key pair for testing/development.
     */
    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        return kpg.generateKeyPair()
    }
}
