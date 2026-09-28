package com.elysium.nexus.fabric.identity

import java.security.MessageDigest

/**
 * The §31.1 device identity.
 *
 * Every Elysium Nexus device (Android app,
 * Hub, Receiver, desktop agent) has a
 * stable identity: a **fingerprint** +
 * a **signing key**. The fingerprint is
 * the SHA-256 of a public key; the
 * signing key never leaves the device's
 * secure element (Android Keystore on
 * Android, TPM on the Hub, secure
 * element on the Receiver). The
 * fingerprint is the join key: every
 * other device remembers the fingerprints
 * it has paired with.
 *
 * The interface is JVM-testeable: tests
 * stub a [DeviceIdentity] with a
 * deterministic fingerprint. The
 * production [AndroidDeviceIdentity]
 * adapts the Android Keystore + the
 * Hub / Receiver equivalent.
 */
interface DeviceIdentity {
    /** A stable, opaque device id. UUID-shaped. */
    val deviceId: String
    /** A 32-byte fingerprint (SHA-256 of the public key). */
    val fingerprint: ByteArray
    /** A human-readable label. The user picks it. */
    val label: String
    /** The hardware class (Android phone, Hub, Receiver, …). */
    val hardwareClass: HardwareClass
    /**
     * Sign [payload] with the device's
     * private key. The signature is
     * verifiable by anyone with the public
     * key (which is the device's fingerprint
     * — see §31.2 mutual auth).
     */
    fun sign(payload: ByteArray): ByteArray
}

/**
 * The §31.1 hardware class. The class is
 * the policy unit: a phone has different
 * permissions than a Hub; the Hub has
 * different permissions than a Receiver.
 */
enum class HardwareClass {
    AndroidPhone,
    AndroidTablet,
    AndroidTv,
    Foldable,
    MacAgent,
    WindowsAgent,
    LinuxAgent,
    WebConsole,
    Hub,
    Receiver,
    Unknown
}

/**
 * The §31.1 fingerprint helper. A fingerprint
 * is the SHA-256 of the public key (or, when
 * the key is HMAC-only, the SHA-256 of the
 * key bytes). The helper is a pure function;
 * the caller's key material is opaque to it.
 */
object Fingerprint {
    /**
     * @return the SHA-256 of [publicKey] as a
     * 32-byte array. The result is hex-encoded
     * for logs.
     */
    fun of(publicKey: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(publicKey)
    }

    /**
     * @return the SHA-256 of [publicKey] as a
     * 64-character lowercase hex string. The
     * format is the same as `ssh-keygen -lf`
     * (without the trailing comment).
     */
    fun ofHex(publicKey: ByteArray): String {
        val bytes = of(publicKey)
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                append(HEX[(b.toInt() ushr 4) and 0x0F])
                append(HEX[b.toInt() and 0x0F])
            }
        }
    }

    private val HEX: CharArray = "0123456789abcdef".toCharArray()
}

/**
 * An in-memory [DeviceIdentity] for tests +
 * the desktop-agent bootstrap. The signing
 * key is held in process memory; the
 * production [AndroidDeviceIdentity] uses
 * the Android Keystore instead.
 */
class InMemoryDeviceIdentity(
    override val deviceId: String,
    override val label: String,
    override val hardwareClass: HardwareClass,
    private val privateKey: ByteArray,
    private val publicKey: ByteArray
) : DeviceIdentity {

    init {
        require(deviceId.isNotBlank()) {
            "DeviceIdentity.deviceId must be non-blank."
        }
        require(label.isNotBlank()) {
            "DeviceIdentity.label must be non-blank."
        }
        require(privateKey.isNotEmpty()) {
            "InMemoryDeviceIdentity.privateKey must be non-empty."
        }
        require(publicKey.isNotEmpty()) {
            "InMemoryDeviceIdentity.publicKey must be non-empty."
        }
    }

    override val fingerprint: ByteArray = Fingerprint.of(publicKey)

    override fun sign(payload: ByteArray): ByteArray {
        // The in-memory signer is HMAC-SHA256.
        // The Android adapter uses the
        // Keystore's HmacSHA256 primitive;
        // the Hub / Receiver use the secure
        // element's HMAC. The output is
        // a 32-byte signature.
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(privateKey, "HmacSHA256"))
        return mac.doFinal(payload)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceIdentity) return false
        return deviceId == other.deviceId && fingerprint.contentEquals(other.fingerprint)
    }

    override fun hashCode(): Int = 31 * deviceId.hashCode() + fingerprint.contentHashCode()
}
