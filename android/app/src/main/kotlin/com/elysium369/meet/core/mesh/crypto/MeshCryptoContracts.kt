package com.elysium369.meet.core.mesh.crypto

/**
 * ══════════════════════════════════════════════════════════════════════
 *  M E S H   C R Y P T O   S U I T E   C O N T R A C T
 *  ────────────────────────────────────────────────────
 *  Fail-closed contract for authenticated mesh envelope encryption.
 *
 *  CRITICAL SAFETY RULE:
 *  Never deploy or claim unreviewed cryptographic implementations.
 *  Until an audited X25519 + ChaCha20-Poly1305 provider is installed,
 *  all encryption/decryption operations MUST fail closed with
 *  "MESH_CRYPTO_PROVIDER_NOT_VERIFIED".
 * ══════════════════════════════════════════════════════════════════════
 */

data class MeshCiphertext(
    val initializationVector: ByteArray,
    val ciphertext: ByteArray,
    val authenticationTag: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshCiphertext) return false
        return initializationVector.contentEquals(other.initializationVector) &&
                ciphertext.contentEquals(other.ciphertext) &&
                authenticationTag.contentEquals(other.authenticationTag)
    }

    override fun hashCode(): Int {
        var result = initializationVector.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + authenticationTag.contentHashCode()
        return result
    }
}

interface MeshCryptoSuite {
    suspend fun encrypt(
        recipientDeviceId: String,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): MeshCiphertext

    suspend fun decrypt(
        envelope: MeshCiphertext,
        associatedData: ByteArray,
    ): ByteArray
}

/**
 * Default fail-closed crypto provider.
 * Throws explicit errors instead of providing weak, mock, or fake encryption.
 */
class UnavailableMeshCrypto : MeshCryptoSuite {
    override suspend fun encrypt(
        recipientDeviceId: String,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): MeshCiphertext {
        error("MESH_CRYPTO_PROVIDER_NOT_VERIFIED")
    }

    override suspend fun decrypt(
        envelope: MeshCiphertext,
        associatedData: ByteArray,
    ): ByteArray {
        error("MESH_CRYPTO_PROVIDER_NOT_VERIFIED")
    }
}
