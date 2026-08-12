package com.elysium369.meet.core.obd

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

data class SignedDiagnosticManifest(
    val keyId: String,
    val signatureAlgorithm: String,
    val signatureBase64: String,
    val signedAtMs: Long,
)

object DiagnosticManifestSigner {
    private const val KEY_ALIAS = "ELYSIUM_DIAGNOSTIC_MANIFEST_V1"

    fun sign(canonicalManifest: String, nowMs: Long = System.currentTimeMillis()): Result<SignedDiagnosticManifest> =
        runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!store.containsAlias(KEY_ALIAS)) {
                val generator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
                generator.initialize(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                    )
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build(),
                )
                generator.generateKeyPair()
            }
            val entry = store.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: error("Diagnostic manifest key unavailable")
            val privateKey = entry.privateKey as ECPrivateKey
            val info = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
                .getKeySpec(privateKey, KeyInfo::class.java)
            check(info.isInsideSecureHardware) { "Diagnostic manifest key is not hardware protected" }
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey)
                update(canonicalManifest.toByteArray(Charsets.UTF_8))
                sign()
            }
            val keyId = MessageDigest.getInstance("SHA-256")
                .digest(entry.certificate.encoded)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            SignedDiagnosticManifest(
                keyId = keyId,
                signatureAlgorithm = "SHA256withECDSA",
                signatureBase64 = Base64.getEncoder().encodeToString(signature),
                signedAtMs = nowMs,
            )
        }
}
