package com.elysium369.meet.communications

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

data class LocalCipherPayload(
    val ciphertextBase64: String,
    val nonceBase64: String,
)

/**
 * Protects the local Room projection with a non-exportable Android Keystore key.
 * This is local device encryption, deliberately not described as E2EE.
 */
@Singleton
class DeviceMessageCipher @Inject constructor() {
    private val keyAlias = "elysium_communications_local_projection_v1"

    fun encrypt(plaintext: String, associatedData: String): LocalCipherPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData.toByteArray(StandardCharsets.UTF_8))
        return LocalCipherPayload(
            ciphertextBase64 = Base64.encodeToString(cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP),
            nonceBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    fun decrypt(payload: LocalCipherPayload, associatedData: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val nonce = Base64.decode(payload.nonceBase64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(128, nonce))
        cipher.updateAAD(associatedData.toByteArray(StandardCharsets.UTF_8))
        val plaintext = cipher.doFinal(Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP))
        return plaintext.toString(StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
