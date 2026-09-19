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

@Singleton
class SafetyPayloadCipher @Inject constructor() {

    private val alias = "meet_safety_payload_v1"

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

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        return ByteBuffer
            .allocate(4 + iv.size + ciphertext.size)
            .putInt(iv.size)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(blob)

        val ivLength = buffer.int
        require(ivLength in 12..32)

        val iv = ByteArray(ivLength)
        buffer.get(iv)

        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, iv),
        )

        return cipher.doFinal(ciphertext)
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
