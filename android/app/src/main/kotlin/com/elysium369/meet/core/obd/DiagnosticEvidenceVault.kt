package com.elysium369.meet.core.obd

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.elysium369.meet.data.local.entities.EncryptedEvidenceBlobEntity
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Unified encrypted authority for raw diagnostic payloads.
 *
 * Plaintext exists only in memory while the transport result is normalized and
 * encrypted. Room stores searchable protocol metadata plus this AES-GCM blob.
 */
object DiagnosticEvidenceVault {
    private const val KEY_ALIAS = "ELYSIUM_DIAGNOSTIC_EVIDENCE_V1"
    private const val KEY_VERSION = 1
    private const val CIPHER_SUITE = "AES-256-GCM"

    fun encrypt(
        sessionId: String,
        exchangeId: String,
        rawRequest: String,
        rawResponse: String,
        requestHash: String,
        responseHash: String,
        createdAtMs: Long,
        retentionClass: String,
    ): EncryptedEvidenceBlobEntity {
        require(sessionId.isNotBlank() && exchangeId.isNotBlank())
        val aad = canonicalBytes(sessionId, exchangeId, requestHash, responseHash, CIPHER_SUITE, KEY_VERSION.toString())
        val plaintext = canonicalBytes(rawRequest, rawResponse)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedEvidenceBlobEntity(
            blobId = UUID.randomUUID().toString(),
            cipherSuite = CIPHER_SUITE,
            keyVersion = KEY_VERSION,
            nonce = cipher.iv.copyOf(),
            aad = aad,
            ciphertext = ciphertext,
            ciphertextHash = sha256Hex(ciphertext),
            createdAtMs = createdAtMs,
            retentionClass = retentionClass,
        )
    }

    fun decrypt(blob: EncryptedEvidenceBlobEntity): ByteArray {
        require(blob.cipherSuite == CIPHER_SUITE && blob.keyVersion == KEY_VERSION)
        require(sha256Hex(blob.ciphertext) == blob.ciphertextHash) { "Ciphertext hash mismatch" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, blob.nonce))
        cipher.updateAAD(blob.aad)
        return cipher.doFinal(blob.ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun canonicalBytes(vararg fields: String): ByteArray = fields
        .joinToString(separator = "") { value -> "${value.toByteArray(Charsets.UTF_8).size}:$value" }
        .toByteArray(Charsets.UTF_8)

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
