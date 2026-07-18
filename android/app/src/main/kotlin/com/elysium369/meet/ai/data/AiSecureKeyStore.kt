package com.elysium369.meet.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface AiSecureKeyStore {
    suspend fun saveApiKey(providerId: String, rawApiKey: String): Result<Unit>
    suspend fun getApiKey(providerId: String): Result<String>
    suspend fun deleteApiKey(providerId: String): Result<Unit>
    suspend fun hasApiKey(providerId: String): Boolean
    suspend fun getMaskedKey(providerId: String): String?
}

@Singleton
class AiSecureKeyStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AiSecureKeyStore {

    private val sharedPrefs = context.getSharedPreferences("meet_ai_secure_prefs", Context.MODE_PRIVATE)
    private val keyStoreAlias = "MeetAiMasterKeyAlias"
    private val keyStoreType = "AndroidKeyStore"

    init {
        initMasterKey()
    }

    private fun initMasterKey() {
        try {
            val ks = KeyStore.getInstance(keyStoreType).apply { load(null) }
            if (!ks.containsAlias(keyStoreAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    keyStoreType
                )
                keyGenerator.init(
                    KeyGenParameterSpec.Builder(
                        keyStoreAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            android.util.Log.e("KeyStore", "Failed to init master key", e)
        }
    }

    private fun getSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(keyStoreType).apply { load(null) }
        return (ks.getEntry(keyStoreAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    override suspend fun saveApiKey(providerId: String, rawApiKey: String): Result<Unit> = runCatching {
        var cleanKey = rawApiKey.trim()
        
        if (cleanKey.startsWith("\"") && cleanKey.endsWith("\"")) {
            cleanKey = cleanKey.substring(1, cleanKey.length - 1).trim()
        }
        if (cleanKey.startsWith("'") && cleanKey.endsWith("'")) {
            cleanKey = cleanKey.substring(1, cleanKey.length - 1).trim()
        }
        
        cleanKey = cleanKey.replace("\r", "").replace("\n", "")

        if (cleanKey.isEmpty()) {
            throw IllegalArgumentException("La clave API no puede estar vacía.")
        }
        if (cleanKey.length < 8) {
            throw IllegalArgumentException("La clave API es sospechosamente corta.")
        }
        if (cleanKey.contains(" ")) {
            throw IllegalArgumentException("La clave API no puede contener espacios internos.")
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val encryptionBytes = cipher.doFinal(cleanKey.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv

        val ivStr = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encryptedStr = Base64.encodeToString(encryptionBytes, Base64.NO_WRAP)

        sharedPrefs.edit()
            .putString("key_$providerId", "$ivStr:$encryptedStr")
            .apply()
    }

    override suspend fun getApiKey(providerId: String): Result<String> = runCatching {
        val record = sharedPrefs.getString("key_$providerId", null)
            ?: throw IllegalStateException("No key saved for provider $providerId")
        
        val parts = record.split(":")
        if (parts.size != 2) {
            throw IllegalStateException("Record is malformed.")
        }

        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
        val decryptedBytes = cipher.doFinal(ciphertext)
        String(decryptedBytes, Charsets.UTF_8)
    }

    override suspend fun deleteApiKey(providerId: String): Result<Unit> = runCatching {
        sharedPrefs.edit()
            .remove("key_$providerId")
            .apply()
    }

    override suspend fun hasApiKey(providerId: String): Boolean {
        return sharedPrefs.contains("key_$providerId")
    }

    override suspend fun getMaskedKey(providerId: String): String? {
        val record = sharedPrefs.getString("key_$providerId", null) ?: return null
        return try {
            val rawKey = getApiKey(providerId).getOrNull() ?: return null
            if (rawKey.length > 8) {
                val prefix = rawKey.take(3)
                val suffix = rawKey.takeLast(4)
                "$prefix...$suffix"
            } else {
                "..."
            }
        } catch (e: Exception) {
            null
        }
    }
}
