package com.elysium369.meet.core.obd

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.os.Build
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
    val publicKeyBase64: String,
    val certificateChainBase64: List<String>,
    val keySecurityLevel: String,
    val signatureVersion: Int = 1,
)

data class DiagnosticDeviceKeyRegistration(
    val deviceKeyId: String,
    val publicKeyBase64: String,
    val certificateChainBase64: List<String>,
    val securityLevel: String,
    val registeredAtMs: Long,
    val revocationState: String = "UNREGISTERED",
)

enum class DiagnosticSignatureVerification {
    VALID, INVALID, UNTRUSTED_KEY, REVOKED, UNSIGNED, UNSUPPORTED_VERSION,
}

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
                publicKeyBase64 = Base64.getEncoder().encodeToString(entry.certificate.publicKey.encoded),
                certificateChainBase64 = entry.certificateChain.map { certificate ->
                    Base64.getEncoder().encodeToString(certificate.encoded)
                },
                keySecurityLevel = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
                    info.isInsideSecureHardware -> "TRUSTED_EXECUTION_ENVIRONMENT"
                    else -> "SOFTWARE"
                },
            )
        }
}

/** Offline verification primitive; trust/revocation is supplied by signed public trust data. */
object DiagnosticManifestVerifier {
    fun verify(
        canonicalManifest: String,
        signed: SignedDiagnosticManifest?,
        trustedRegistration: DiagnosticDeviceKeyRegistration?,
    ): DiagnosticSignatureVerification {
        signed ?: return DiagnosticSignatureVerification.UNSIGNED
        if (signed.signatureVersion != 1) return DiagnosticSignatureVerification.UNSUPPORTED_VERSION
        trustedRegistration ?: return DiagnosticSignatureVerification.UNTRUSTED_KEY
        if (trustedRegistration.revocationState == "REVOKED") return DiagnosticSignatureVerification.REVOKED
        if (trustedRegistration.deviceKeyId != signed.keyId ||
            trustedRegistration.publicKeyBase64 != signed.publicKeyBase64 ||
            signed.signatureAlgorithm != "SHA256withECDSA"
        ) return DiagnosticSignatureVerification.INVALID
        val verified = runCatching {
            val key = KeyFactory.getInstance("EC").generatePublic(
                java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(signed.publicKeyBase64)),
            )
            Signature.getInstance(signed.signatureAlgorithm).run {
                initVerify(key)
                update(canonicalManifest.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(signed.signatureBase64))
            }
        }.getOrDefault(false)
        return if (verified) DiagnosticSignatureVerification.VALID else DiagnosticSignatureVerification.INVALID
    }
}
