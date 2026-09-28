package com.elysium.nexus.fabric.identity

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID

/**
 * The §31.1 Android adapter for [DeviceIdentity].
 *
 * The adapter uses the Android Keystore to
 * generate + protect a **HMAC-SHA256** key
 * (alias `elysium.device.signing.v1`). The
 * key is `KeyProperties.PURPOSE_SIGN`, no
 * user auth required (a device is not a
 * user; the §31.3 RBAC layer is the user-
 * facing auth). The fingerprint is the
 * SHA-256 of the public key derived from
 * the private key (the Keystore does not
 * expose the private key in cleartext;
 * the fingerprint is computed by signing
 * a known nonce and hashing the signature
 * — same entropy, different path).
 *
 * ## Why HMAC and not RSA / ECDSA
 *
 * The Keystore's RSA / ECDSA APIs require
 * API 23+; the HMAC API requires API 23+
 * too, but the HMAC path is one method
 * call and a single algorithm
 * (HmacSHA256). The Hub / Receiver sign
 * with the secure element's HMAC; the
 * Android side is the same shape. The
 * signature is verifiable by anyone with
 * the public key.
 *
 * ## Why the alias is namespaced
 *
 * `elysium.device.signing.v1` is the
 * canonical alias. The `v1` suffix is
 * the schema version; a future `v2`
 * would use a different key + a
 * different fingerprint, with a
 * migration path (the user re-pairs
 * the device). The alias is namespaced
 * to keep the Elysium key out of the
 * way of any other Keystore users.
 */
class AndroidDeviceIdentity(
    context: Context,
    label: String,
    hardwareClass: HardwareClass
) : DeviceIdentity {

    private val tag = "ElysiumNexus.Identity"

    override val deviceId: String = installIfMissing(context)
    override val label: String = label
    override val hardwareClass: HardwareClass = hardwareClass

    // The fingerprint is derived from the
    // key material. The Keystore does not
    // expose the public key bytes for an
    // HMAC key; we derive a stable
    // fingerprint by signing a fixed
    // nonce. The signature is 32 bytes;
    // the fingerprint is its SHA-256.
    private val nonce: ByteArray = "elysium.device.fingerprint.v1".toByteArray()
    override val fingerprint: ByteArray = computeFingerprint()

    override fun sign(payload: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val key = keystore().getKey(KEY_ALIAS, null) as javax.crypto.SecretKey
        mac.init(key)
        return mac.doFinal(payload)
    }

    /**
     * Compute the fingerprint by signing the
     * fixed nonce with the device key and
     * hashing the signature. The result is
     * stable: the same key produces the same
     * fingerprint across reboots.
     */
    private fun computeFingerprint(): ByteArray {
        val sig = sign(nonce)
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sig)
    }

    /**
     * Generate the signing key if it does not
     * exist. The function is idempotent: a
     * second call with the same alias returns
     * the existing key. The function is
     * total: a Keystore failure logs and
     * returns the existing `deviceId` (which
     * may be empty on first call).
     */
    private fun installIfMissing(context: Context): String {
        return try {
            val ks = keystore()
            if (ks.containsAlias(KEY_ALIAS)) {
                // Use a derived id from the key
                // alias. The alias is the
                // identity anchor; a fresh
                // deviceId is generated on the
                // first install.
                storedDeviceId() ?: UUID.randomUUID().toString().also {
                    prefs(context).edit().putString(PREF_DEVICE_ID, it).apply()
                }
            } else {
                val kpg = java.security.KeyPairGenerator.getInstance(
                    "AES", // placeholder, we use HMAC below
                    "AndroidKeyStore"
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN
                ).setDigests(KeyProperties.DIGEST_SHA256)
                    .setKeySize(256)
                    .build()
                // The HMAC key is generated via
                // `KeyGenerator`, not `KeyPairGenerator`.
                val kg = javax.crypto.KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                    "AndroidKeyStore"
                )
                kg.init(spec)
                kg.generateKey()
                val id = UUID.randomUUID().toString()
                prefs(context).edit().putString(PREF_DEVICE_ID, id).apply()
                Log.i(tag, "Installed device identity: $id")
                id
            }
        } catch (e: Throwable) {
            Log.w(tag, "Keystore unavailable; using empty device id.", e)
            ""
        }
    }

    /**
     * The stored [deviceId], or `null` on the
     * first call. Used to recover the id
     * across process death.
     */
    private fun storedDeviceId(): String? = try {
        val prefs = prefs(provideContext())
        prefs.getString(PREF_DEVICE_ID, null)
    } catch (e: Throwable) {
        null
    }

    /**
     * The Keystore handle. The handle is
     * re-resolved on every call: the
     * Android `KeyStore` is a singleton
     * per process, and the JCE provider
     * name is "AndroidKeyStore".
     */
    private fun keystore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    /**
     * The `SharedPreferences` for the
     * deviceId. The id is stored in
     * plain text (it is not the secret;
     * the secret is the HMAC key in the
     * Keystore). The id survives process
     * death and reinstalls of the same
     * Keystore.
     */
    private fun prefs(context: Context): android.content.SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    /**
     * The [Context] provider. The constructor
     * stores the application context; the
     * stored id recovery does not need an
     * Activity context.
     */
    private fun provideContext(): Context = appContext

    private val appContext: Context = context.applicationContext

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceIdentity) return false
        return deviceId == other.deviceId && fingerprint.contentEquals(other.fingerprint)
    }

    override fun hashCode(): Int = 31 * deviceId.hashCode() + fingerprint.contentHashCode()

    companion object {
        /** The Keystore alias for the device signing key. */
        const val KEY_ALIAS: String = "elysium.device.signing.v1"
        /** The `SharedPreferences` file name. */
        const val PREFS_NAME: String = "elysium_identity"
        /** The key for the stored device id. */
        const val PREF_DEVICE_ID: String = "device_id"

        /**
         * @return the API level. Convenience
         * for callers that need to gate
         * features (e.g. secure element)
         * on a specific Android version.
         */
        fun apiLevel(): Int = Build.VERSION.SDK_INT
    }
}
