package com.elysium.nexus.core.profile

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The §15 profile signature.
 *
 * `MASTER_ORDER.md` §15 says "Firmar perfiles" —
 * a profile is a document the user authors, and
 * the document is *signed* by the author. The
 * signature is an HMAC-SHA256 of the profile's
 * canonical JSON serialisation, keyed by a
 * per-user secret.
 *
 * ## Why HMAC, not RSA / ECDSA
 *
 * The §15 spec does not call for *asymmetric*
 * signing (a public-key signature verifiable by
 * any host). It calls for a *signature* that
 * proves the profile was not tampered with
 * after the author signed it. HMAC-SHA256 is
 * sufficient for this:
 *
 *  - The author signs with a secret key they
 *    keep on the device.
 *  - The host (or any consumer of the profile)
 *    verifies with the same secret.
 *  - The signature proves the document's
 *    integrity (no bit was changed) but does
 *    not prove the author's identity to a
 *    third party.
 *
 * If §15 ever calls for *asymmetric* signing
 * (e.g. for sharing profiles between users
 * without sharing the signing key), the
 * signature can be migrated to Ed25519 or
 * ECDSA-P256.
 *
 * ## Why the secret is a function parameter,
 * not a class field
 *
 * The secret is *per user*; the production
 * implementation stores it in the Android
 * Keystore (or a `SharedPreferences` file in
 * dev mode). The function takes the secret as a
 * parameter so the test surface does not depend
 * on Android's keystore. The activity wires the
 * keystore secret at the call site.
 */
object ProfileSignature {

    private const val ALGORITHM: String = "HmacSHA256"

    /**
     * Compute the signature of [profile] with
     * [secret] as the HMAC key. The signature is
     * the HMAC-SHA256 of the profile's JSON
     * serialisation.
     *
     * @return a 32-byte signature as a lowercase
     *   hex string (64 characters). The hex form
     *   is the storage shape (the signature is
     *   embedded in the profile document as a
     *   string column).
     */
    fun sign(profile: Profile, secret: ByteArray): String {
        val json = ProfileJson.toJson(profile)
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret, ALGORITHM))
        val raw = mac.doFinal(json.toByteArray(Charsets.UTF_8))
        return raw.toHex()
    }

    /**
     * Verify [signature] is a valid HMAC of
     * [profile] under [secret]. Used by the host
     * (or any consumer of the profile) to
     * confirm the document was not tampered
     * with.
     *
     * @return `true` if the signature matches;
     *   `false` if it does not (or if the
     *   signature is malformed).
     *
     * The comparison is constant-time to avoid
     * timing attacks: an attacker who can
     * observe the verification time should not
     * be able to recover the secret byte by
     * byte.
     */
    fun verify(profile: Profile, signature: String, secret: ByteArray): Boolean {
        val expected = sign(profile, secret)
        if (expected.length != signature.length) return false
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].code xor signature[i].code)
        }
        return diff == 0
    }

    /**
     * Convert a `ByteArray` to a lowercase hex
     * string. Used for the signature's storage
     * shape (a `String` field in the profile
     * document).
     */
    private fun ByteArray.toHex(): String {
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
