package com.elysium369.meet.core.vehicleaccess.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.elysium369.meet.core.reports.HashEngine
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Hardware-backed Cryptographic Authorization Kernel using AndroidKeyStore.
 * Produces non-repudiable SHA256withECDSA signatures for vehicle access commands.
 * 
 * Safety:
 * - Keys are generated inside the device Secure Element / TEE.
 * - Private keys NEVER leave the hardware security module and are NEVER persisted in SQLite/Room.
 */
object VehicleAccessAuthorizationKernel {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ACCESS_SIGNING_KEY_ALIAS = "meet_vehicle_access_auth_key"

    init {
        ensureSigningKeyExists()
    }

    private fun ensureSigningKeyExists() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(ACCESS_SIGNING_KEY_ALIAS)) {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                ACCESS_SIGNING_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .build()

            keyPairGenerator.initialize(parameterSpec)
            keyPairGenerator.generateKeyPair()
        }
    }

    /**
     * Signs an access command or provisioning request with the hardware-backed private key.
     * Throws SecurityException if Keystore is unavailable — NEVER falls back to an insecure hash.
     */
    fun signAuthorizationPayload(payload: String): String {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val privateKeyEntry = keyStore.getEntry(ACCESS_SIGNING_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: throw SecurityException("SIGNATURE_UNAVAILABLE: Keystore private key alias missing")

            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKeyEntry.privateKey)
            signature.update(payload.toByteArray(Charsets.UTF_8))
            val signedBytes = signature.sign()
            signedBytes.joinToString("") { "%02x".format(it) }
        }.getOrThrow()
    }

    /**
     * Verifies the cryptographic proof of an authorization receipt.
     */
    fun verifyAuthorizationPayload(payload: String, signatureHex: String): Boolean {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val certificate = keyStore.getCertificate(ACCESS_SIGNING_KEY_ALIAS) ?: return false
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(certificate.publicKey)
            signature.update(payload.toByteArray(Charsets.UTF_8))

            val sigBytes = signatureHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            signature.verify(sigBytes)
        }.getOrDefault(false)
    }
}
