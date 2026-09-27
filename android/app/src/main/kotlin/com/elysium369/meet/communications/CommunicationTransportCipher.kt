package com.elysium369.meet.communications

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import java.security.spec.MGF1ParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CommunicationTransportEnvelope(
    val version: Int = 1,
    val nonce: String,
    val ciphertext: String,
    val recipients: Map<String, String>,
)

/** Hybrid authenticated encryption. Device private keys never leave Android Keystore.
 * This protocol does not provide forward secrecy or independent key verification.
 */
@Singleton
class CommunicationTransportCipher @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val oaep = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    private fun alias(principal: String) = "meet.communication.transport.v2.$principal"
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    fun publicKey(principal: String): String {
        val store = keyStore()
        if (!store.containsAlias(alias(principal))) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias(principal), KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_SIGN)
                    .setKeySize(3072)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                    .build())
            }.generateKeyPair()
        }
        return encode(keyStore().getCertificate(alias(principal)).publicKey.encoded)
    }

    fun encrypt(text: String, aad: String, recipientKeys: Map<String, String>): String {
        require(recipientKeys.isNotEmpty() && recipientKeys.size <= 512)
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val aes = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            updateAAD(aad.toByteArray(Charsets.UTF_8))
        }
        val wrapped = recipientKeys.mapValues { (_, encoded) ->
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decode(encoded)))
            val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            rsa.init(Cipher.ENCRYPT_MODE, publicKey, oaep)
            encode(rsa.doFinal(key.encoded))
        }
        return json.encodeToString(CommunicationTransportEnvelope(
            nonce = encode(nonce), ciphertext = encode(aes.doFinal(text.toByteArray(Charsets.UTF_8))), recipients = wrapped,
        ))
    }

    fun decrypt(envelope: String, principal: String, deviceId: String, aad: String): String {
        require(envelope.length <= 2_000_000)
        val wire = json.decodeFromString<CommunicationTransportEnvelope>(envelope)
        require(wire.version == 1)
        val wrapped = requireNotNull(wire.recipients[deviceId])
        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(Cipher.DECRYPT_MODE, keyStore().getKey(alias(principal), null), oaep)
        val key = javax.crypto.spec.SecretKeySpec(rsa.doFinal(decode(wrapped)), "AES")
        val nonce = decode(wire.nonce)
        require(nonce.size == 12)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            updateAAD(aad.toByteArray(Charsets.UTF_8))
            String(doFinal(decode(wire.ciphertext)), Charsets.UTF_8)
        }
    }

    fun sign(principal:String,payload:String):String = java.security.Signature.getInstance("SHA256withRSA").run {
        initSign(keyStore().getKey(alias(principal),null) as java.security.PrivateKey)
        update(payload.toByteArray(Charsets.UTF_8))
        encode(sign())
    }

    fun verify(publicKey:String,payload:String,signature:String):Boolean = runCatching {
        java.security.Signature.getInstance("SHA256withRSA").run {
            initVerify(KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decode(publicKey))))
            update(payload.toByteArray(Charsets.UTF_8))
            verify(decode(signature))
        }
    }.getOrDefault(false)

    fun isValidRecipientKey(publicKey:String):Boolean = runCatching {
        val key=KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decode(publicKey))) as java.security.interfaces.RSAPublicKey
        key.modulus.bitLength() in 3072..4096 && key.publicExponent == java.math.BigInteger.valueOf(65537)
    }.getOrDefault(false)

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
}
