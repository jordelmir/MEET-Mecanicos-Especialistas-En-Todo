package com.elysium369.meet.safety.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class AeadBlob(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
) {
    fun toWire(): ByteArray = ByteBuffer
        .allocate(4 + nonce.size + ciphertext.size)
        .putInt(nonce.size)
        .put(nonce)
        .put(ciphertext)
        .array()

    companion object {
        fun fromWire(data: ByteArray): AeadBlob {
            val buffer = ByteBuffer.wrap(data)
            val nonceLen = buffer.int
            require(nonceLen in 12..32)
            val nonce = ByteArray(nonceLen)
            buffer.get(nonce)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)
            return AeadBlob(ciphertext = ciphertext, nonce = nonce)
        }
    }
}

@Singleton
class SafetyPayloadCipher @Inject constructor() {

    private val alias = "meet_safety_payload_v2"

    private fun key(): SecretKey {
        val keyStore = KeyStore
            .getInstance("AndroidKeyStore")
            .apply { load(null) }

        (keyStore.getKey(alias, null) as? SecretKey)
            ?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )

        return generator.generateKey()
    }

    fun encryptAead(plaintext: ByteArray, associatedData: ByteArray): AeadBlob {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)

        return AeadBlob(
            ciphertext = cipher.doFinal(plaintext),
            nonce = cipher.iv,
        )
    }

    fun decryptAead(blob: AeadBlob, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, blob.nonce),
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(blob.ciphertext)
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val blob = encryptAead(plaintext, byteArrayOf())
        return blob.toWire()
    }

    fun decrypt(blob: ByteArray): ByteArray {
        return decryptAead(AeadBlob.fromWire(blob), byteArrayOf())
    }
}

object SafetyDigest {

    private val domain = "MEET-SAFETY-REPORT-V1\u0000".encodeToByteArray()

    fun reportPayload(payload: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(domain)
        digest.update(payload)
        return digest.digest()
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun verify(payload: ByteArray, expected: String): Boolean {
        return reportPayload(payload) == expected
    }
}
