package com.elysium.nexus.fabric.identity

import com.elysium.nexus.fabric.canonical.DeviceId

// ─── §71 Trust Model ─────────────────────────────────────────────────────────

/**
 * Trust states for device relationships.
 *
 * A device progresses through trust states as the
 * user interacts with it:
 *
 * ```
 * UNPAIRED
 *   ↓ user initiates pairing
 * USER_APPROVED
 *   ↓ pairing completed
 * PAIRED
 *   ↓ PIN verified / certificate pinned
 * PEER_PINNED
 *   ↓ manufacturer attestation verified
 * ATTESTED
 *   ↓ OEM certification received
 * MANUFACTURER_CERTIFIED
 * ```
 *
 * Each state grants different capabilities:
 * - UNPAIRED: discovery only
 * - USER_APPROVED: basic control
 * - PAIRED: full control
 * - PEER_PINNED: trusted control
 * - ATTESTED: verified control
 * - MANUFACTURER_CERTIFIED: certified control
 */
enum class TrustState(val tier: Int) {
    /** No relationship. Device discovered but not paired. */
    UNPAIRED(tier = 0),

    /** User explicitly approved the device. Basic control allowed. */
    USER_APPROVED(tier = 1),

    /** Pairing completed. Full control allowed. */
    PAIRED(tier = 2),

    /** PIN verified or certificate pinned. Trusted control. */
    PEER_PINNED(tier = 3),

    /** Manufacturer attestation verified. */
    ATTESTED(tier = 4),

    /** OEM certification received. */
    MANUFACTURER_CERTIFIED(tier = 5);

    fun isAtLeast(other: TrustState): Boolean = this.tier >= other.tier

    fun canUpgradeTo(other: TrustState): Boolean = other.tier == this.tier + 1
}

/**
 * Evidence supporting a trust claim.
 */
sealed class TrustEvidence {

    /** User explicitly approved on-screen. */
    data class UserApproval(
        val timestampMs: Long,
        val method: ApprovalMethod
    ) : TrustEvidence()

    /** PIN was verified. */
    data class PinVerification(
        val pinLength: Int,
        val timestampMs: Long
    ) : TrustEvidence()

    /** Certificate was pinned. */
    data class CertificatePinning(
        val fingerprint: String,
        val algorithm: String,
        val timestampMs: Long
    ) : TrustEvidence()

    /** Manufacturer attestation verified. */
    data class ManufacturerAttestation(
        val manufacturer: String,
        val attestationDocument: ByteArray,
        val timestampMs: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ManufacturerAttestation) return false
            return manufacturer == other.manufacturer &&
                    attestationDocument.contentEquals(other.attestationDocument)
        }
        override fun hashCode(): Int {
            return 31 * manufacturer.hashCode() + attestationDocument.contentHashCode()
        }
    }

    /** OEM certification received. */
    data class OemCertification(
        val oem: String,
        val certId: String,
        val expiresAtMs: Long,
        val timestampMs: Long
    ) : TrustEvidence()

    /** Network proximity (WiFi SSID match). */
    data class NetworkProximity(
        val ssid: String,
        val signalStrength: Int,
        val timestampMs: Long
    ) : TrustEvidence()

    /** Bluetooth proximity verified. */
    data class BluetoothProximity(
        val address: String,
        val rssi: Int,
        val timestampMs: Long
    ) : TrustEvidence()
}

enum class ApprovalMethod {
    /** User confirmed on the Android device. */
    ON_DEVICE,

    /** User confirmed on the target device (TV screen, etc.). */
    ON_TARGET_DEVICE,

    /** User entered a PIN. */
    PIN_ENTRY,

    /** NFC tap. */
    NFC_TAP,

    /** QR code scan. */
    QR_SCAN
}

// ─── Pairing Request/Result ──────────────────────────────────────────────────

/**
 * A request to pair with a device.
 */
data class PairingRequest(
    val deviceId: DeviceId,
    val method: PairingMethod,
    val timeoutMs: Long = 30_000L,
    val pin: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class PairingMethod {
    /** Simple user approval on both devices. */
    USER_APPROVAL,

    /** PIN-based pairing. */
    PIN_VERIFICATION,

    /** Certificate-based pairing. */
    CERTIFICATE_EXCHANGE,

    /** NFC tap pairing. */
    NFC_TAP,

    /** QR code pairing. */
    QR_CODE,

    /** Matter commissioning. */
    MATTER_COMMISSIONING
}

sealed class PairingResult {
    data class Success(
        val trustState: TrustState,
        val evidence: List<TrustEvidence>,
        val credentialsEstablished: Boolean
    ) : PairingResult()

    data class Failed(
        val reason: String,
        val error: PairingError
    ) : PairingResult()

    data object Timeout : PairingResult()

    data object UserCancelled : PairingResult()
}

enum class PairingError {
    DEVICE_NOT_FOUND,
    DEVICE_NOT_RESPONDING,
    PIN_INCORRECT,
    CERTIFICATE_INVALID,
    ATTESTATION_FAILED,
    NETWORK_ERROR,
    ALREADY_PAIRED,
    UNSUPPORTED_METHOD,
    PERMISSION_DENIED,
    TIMEOUT
}

// ─── Trust State Machine ─────────────────────────────────────────────────────

/**
 * Valid trust state transitions.
 */
object TrustStateMachine {
    private val validTransitions = mapOf(
        TrustState.UNPAIRED to setOf(TrustState.USER_APPROVED),
        TrustState.USER_APPROVED to setOf(TrustState.PAIRED, TrustState.UNPAIRED),
        TrustState.PAIRED to setOf(TrustState.PEER_PINNED, TrustState.UNPAIRED),
        TrustState.PEER_PINNED to setOf(TrustState.ATTESTED, TrustState.UNPAIRED),
        TrustState.ATTESTED to setOf(TrustState.MANUFACTURER_CERTIFIED, TrustState.UNPAIRED),
        TrustState.MANUFACTURER_CERTIFIED to setOf(TrustState.UNPAIRED)
    )

    fun canTransition(from: TrustState, to: TrustState): Boolean {
        return to in (validTransitions[from] ?: emptySet())
    }

    fun validTransitionsFrom(state: TrustState): Set<TrustState> {
        return validTransitions[state] ?: emptySet()
    }
}

// ─── Device Trust Record ─────────────────────────────────────────────────────

/**
 * The complete trust record for a device relationship.
 */
data class DeviceTrustRecord(
    val deviceId: DeviceId,
    val currentState: TrustState,
    val evidence: List<TrustEvidence>,
    val createdAtMs: Long,
    val lastActivityMs: Long,
    val lastVerifiedMs: Long? = null,
    val trustScore: Double = 0.0,
    val notes: String = ""
) {
    fun isExpired(maxInactiveMs: Long): Boolean {
        return System.currentTimeMillis() - lastActivityMs > maxInactiveMs
    }

    fun withActivity(): DeviceTrustRecord {
        return copy(lastActivityMs = System.currentTimeMillis())
    }

    fun withVerification(): DeviceTrustRecord {
        return copy(lastVerifiedMs = System.currentTimeMillis())
    }
}

// ─── §72 Zero Trust Local Network ────────────────────────────────────────────

/**
 * "On my WiFi" does NOT mean "trusted".
 *
 * All significant mutations require:
 * 1. Validated device identity
 * 2. Authorized pairing
 *
 * This enforces zero-trust principles on the local network:
 * - Every action requires authorization
 * - Every device must prove identity
 * - Every mutation is auditable
 * - No implicit trust based on network location
 */
object ZeroTrustPolicy {

    /**
     * Minimum trust state required for an action.
     */
    fun requiredTrustState(action: com.elysium.nexus.fabric.canonical.UniversalAction): TrustState {
        return when (action) {
            is com.elysium.nexus.fabric.canonical.UniversalAction.PowerOn,
            is com.elysium.nexus.fabric.canonical.UniversalAction.PowerOff,
            is com.elysium.nexus.fabric.canonical.UniversalAction.PowerToggle -> TrustState.USER_APPROVED

            is com.elysium.nexus.fabric.canonical.UniversalAction.VolumeUp,
            is com.elysium.nexus.fabric.canonical.UniversalAction.VolumeDown,
            is com.elysium.nexus.fabric.canonical.UniversalAction.Mute,
            is com.elysium.nexus.fabric.canonical.UniversalAction.SetVolume -> TrustState.USER_APPROVED

            is com.elysium.nexus.fabric.canonical.UniversalAction.ChannelUp,
            is com.elysium.nexus.fabric.canonical.UniversalAction.ChannelDown -> TrustState.USER_APPROVED

            is com.elysium.nexus.fabric.canonical.UniversalAction.Navigate,
            is com.elysium.nexus.fabric.canonical.UniversalAction.Ok,
            is com.elysium.nexus.fabric.canonical.UniversalAction.Back,
            is com.elysium.nexus.fabric.canonical.UniversalAction.Home,
            is com.elysium.nexus.fabric.canonical.UniversalAction.Menu -> TrustState.USER_APPROVED

            is com.elysium.nexus.fabric.canonical.UniversalAction.InputSelect -> TrustState.PAIRED

            is com.elysium.nexus.fabric.canonical.UniversalAction.SetTemperature,
            is com.elysium.nexus.fabric.canonical.UniversalAction.SetFanSpeed,
            is com.elysium.nexus.fabric.canonical.UniversalAction.SetMode -> TrustState.USER_APPROVED

            is com.elysium.nexus.fabric.canonical.UniversalAction.MediaPlay,
            is com.elysium.nexus.fabric.canonical.UniversalAction.MediaPause,
            is com.elysium.nexus.fabric.canonical.UniversalAction.MediaStop,
            is com.elysium.nexus.fabric.canonical.UniversalAction.MediaNext,
            is com.elysium.nexus.fabric.canonical.UniversalAction.MediaPrevious -> TrustState.USER_APPROVED

            is com.elysium.nexus.fabric.canonical.UniversalAction.Custom -> TrustState.PAIRED
        }
    }

    /**
     * Check if a device has sufficient trust for an action.
     */
    fun isAuthorized(trustRecord: DeviceTrustRecord?, action: com.elysium.nexus.fabric.canonical.UniversalAction): Boolean {
        if (trustRecord == null) return false
        val required = requiredTrustState(action)
        return trustRecord.currentState.isAtLeast(required)
    }

    /**
     * Audit log entry for a trust-checked action.
     */
    data class TrustAuditEntry(
        val deviceId: DeviceId,
        val action: String,
        val requiredTrust: TrustState,
        val actualTrust: TrustState,
        val authorized: Boolean,
        val timestampMs: Long = System.currentTimeMillis()
    )
}
