package com.elysium.nexus.fabric.tv.adb

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.security.KeyFactory

/**
 * ADB RSA authorization material.
 *
 * adbd authorizes clients by RSA-SHA1 signature over a
 * 20-byte token. On first contact the device emits
 * AUTH RSAPUBLICKEY; the client answers with its public
 * key in ADB text format (`base64(DER) + " adb-key\n"`),
 * and the TV shows the standard "Allow USB debugging"
 * dialog. Once accepted, every subsequent connection
 * proves ownership via SIGNATURE.
 *
 * Pure JVM (JCA only) — unit-testable.
 */
class AdbAuthorization(
    private val keyPair: KeyPair
) {
    constructor(publicKeyB64: String, privateKeyB64: String) :
        this(buildKeyPair(publicKeyB64, privateKeyB64))

    val publicKeyB64: String
        get() = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    val privateKeyB64: String
        get() = Base64.getEncoder().encodeToString(keyPair.private.encoded)

    /** RSA-SHA1 signature over the AUTH token. adbd requires exactly 20 bytes. */
    fun sign(token: ByteArray): ByteArray {
        val signature = Signature.getInstance(AdbProtocol.RSA_SIGNATURE_ALGORITHM)
        signature.initSign(keyPair.private)
        signature.update(token)
        return signature.sign()
    }

    /** ADB text format: `base64(x509 DER) + " adb-key\n"`. */
    fun publicKeyAdbFormat(): ByteArray =
        (Base64.getEncoder().encodeToString(keyPair.public.encoded) + " adb-key\n")
            .toByteArray(Charsets.UTF_8)

    fun fingerprintSha256(): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(keyPair.public.encoded).joinToString(":") { "%02X".format(it) }
    }

    /** PKCS#8 PEM of the private key — the on-disk `adbkey` format. */
    fun toPem(): String {
        val der = keyPair.private.encoded
        val b64 = Base64.getEncoder().encodeToString(der)
        return "-----BEGIN PRIVATE KEY-----\n" +
            b64.chunked(64).joinToString("\n") + "\n-----END PRIVATE KEY-----\n"
    }

    @Suppress("unused")
    fun modelName(): String = (keyPair.public as RSAPublicKey).modulus.toString(16)

    companion object {
        fun generate(): AdbAuthorization {
            val gen = KeyPairGenerator.getInstance("RSA")
            gen.initialize(2048)
            return AdbAuthorization(gen.generateKeyPair())
        }

        /**
         * Load the host machine's `~/.android/adbkey`
         * (PKCS#8 PEM). A TV that already authorized this
         * key accepts SIGNATURE immediately — no pairing
         * dialog. Used by the real-adbd integration test
         * and by the APK when a saved credential exists.
         */
        fun loadFromPem(pem: String): AdbAuthorization? {
            val body = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace(Regex("\\s"), "")
            return try {
                val kf = KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(
                    PKCS8EncodedKeySpec(Base64.getDecoder().decode(body))
                ) as RSAPrivateCrtKey
                val pub = kf.generatePublic(
                    RSAPublicKeySpec(priv.modulus, priv.publicExponent)
                )
                AdbAuthorization(KeyPair(pub, priv))
            } catch (e: Exception) {
                null
            }
        }

        /** Read `~/.android/adbkey` from disk, if present. */
        fun loadFromHomeDir(homeDir: String = System.getProperty("user.home") ?: ""): AdbAuthorization? {
            val f = java.io.File(homeDir, ".android/adbkey")
            if (!f.isFile) return null
            return try {
                loadFromPem(f.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                null
            }
        }

        private fun buildKeyPair(publicKeyB64: String, privateKeyB64: String): KeyPair {
            val kf = KeyFactory.getInstance("RSA")
            val pub = kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)))
            val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyB64)))
            return KeyPair(pub, priv)
        }
    }
}