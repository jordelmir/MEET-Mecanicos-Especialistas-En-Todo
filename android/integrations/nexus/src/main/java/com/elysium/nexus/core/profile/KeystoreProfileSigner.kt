package com.elysium.nexus.core.profile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The §15 profile signer, backed by the Android
 * Keystore.
 *
 * `MASTER_ORDER.md` §15 calls for "Firmar
 * perfiles" — every profile the user authors is
 * signed with a per-user secret. The secret is
 * stored in the Android Keystore (a hardware-
 * backed keystore on devices with a TEE / StrongBox
 * Keymaster). The signer is the production
 * wiring of [ProfileSignature].
 *
 * ## Why the Android Keystore
 *
 * The Keystore stores the secret in a way that
 * is:
 *  - **Hardware-backed** (on devices with a
 *    TEE / StrongBox): the key never leaves the
 *    secure element. The host (the user's
 *    laptop, the Nexus Receiver, etc.) cannot
 *    extract the key — they can only *use* it
 *    (sign / verify) via the Keystore API.
 *  - **Per-user**: the key is in the user's
 *    profile storage. A factory reset wipes
 *    the key. A multi-user device has separate
 *    keys per user.
 *  - **Durable**: the key survives app upgrades.
 *    The first time the user signs a profile, the
 *    key is generated and stored; subsequent
 *    signatures use the same key.
 *
 * ## Why HMAC, not HMAC + RSA
 *
 * The Keystore's `KeyProperties.PURPOSE_SIGN` is
 * required for asymmetric keys (RSA, ECDSA, etc.)
 * AND for HMAC. The §15 spec describes the
 * signature as a "firma" (a tamper-proof MAC), not
 * as an asymmetric public-key scheme. HMAC is
 * sufficient; the Keystore's HMAC-backed key
 * storage is the right primitive.
 *
 * If §15 ever calls for *asymmetric* signing
 * (e.g. for sharing profiles between users without
 * sharing the signing key), the signer can be
 * migrated to ECDSA-P256 via the `KeyPairGenerator`
 * API.
 */
object KeystoreProfileSigner {

    /** The Android Keystore provider name. */
    private const val KEYSTORE_PROVIDER: String = "AndroidKeyStore"

    /**
     * The key alias. Every profile signature
     * uses the same key (per-user, per-device).
     * The alias is a fixed string; the Android
     * Keystore uses it to look up the key.
     */
    private const val KEY_ALIAS: String = "elysium_nexus_profile_signing_key_v1"

    /**
     * Generate or retrieve the profile-signing
     * key. The key is a 256-bit AES key (used in
     * HMAC mode). On first call, the key is
     * generated and stored in the Android
     * Keystore; subsequent calls return the
     * existing key.
     *
     * The key has:
     *  - `setUserAuthenticationRequired(false)`: the
     *    key can be used without the user being
     *    authenticated. The §15 spec does not call
     *    for biometric / PIN authentication for
     *    every profile signature; a Phase 2+ phase
     *    can re-enable this for the `LockScreen`
     *    feature.
     *  - `setRandomizedEncryptionRequired(false)`:
     *    the key is used only for HMAC, not for
     *    encryption, so this flag is irrelevant.
     *
     * @return the [SecretKey] handle. The key's
     *   raw bytes are *not* accessible from
     *   outside the Keystore.
     */
    fun getOrCreateKey(context: Context): SecretKey {
        val keystore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keystore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /**
     * Sign [profile] with the Keystore-backed key.
     * The function takes a [Context] because the
     * Keystore is per-application; the caller
     * (the activity) provides the application's
     * `Context`.
     *
     * @return the 64-character hex signature.
     */
    fun sign(context: Context, profile: Profile): String {
        val key = getOrCreateKey(context)
        val json = ProfileJson.toJson(profile)
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(key)
        val raw = mac.doFinal(json.toByteArray(Charsets.UTF_8))
        return raw.toHexString()
    }

    /**
     * Verify [signature] is a valid HMAC of
     * [profile] under the Keystore-backed key.
     *
     * @return `true` if the signature matches;
     *   `false` otherwise.
     */
    fun verify(context: Context, profile: Profile, signature: String): Boolean {
        val expected = sign(context, profile)
        if (expected.length != signature.length) return false
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].code xor signature[i].code)
        }
        return diff == 0
    }

    /**
     * Convert a `ByteArray` to a lowercase hex
     * string. Equivalent to
     * [ProfileSignature]'s helper; duplicated here
     * so [KeystoreProfileSigner] is self-contained.
     */
    private fun ByteArray.toHexString(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xff
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0f])
        }
        return sb.toString()
    }

    private val HEX_CHARS: CharArray = "0123456789abcdef".toCharArray()
}
