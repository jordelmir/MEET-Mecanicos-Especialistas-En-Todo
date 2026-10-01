package com.elysium.nexus.fabric.canonical

/**
 * §80 Error Taxonomy.
 *
 * Every external operation in the system can
 * fail. The taxonomy classifies failures into
 * typed, recoverable categories. The UI shows
 * the user a human-readable message; the
 * automation engine reacts to the category;
 * the telemetry pipeline records the code.
 *
 * ## Rules
 *
 * 1. No failure without explanation — the UI
 *    always shows what happened and what
 *    Elysium did about it.
 * 2. Every error is retryable or not — the
 *    taxonomy declares the retry policy.
 * 3. Every error has a severity — the UI
 *    decides how prominently to display it.
 * 4. Every error is logged — the flight
 *    recorder captures the full context.
 *
 * ## Hierarchy
 *
 * - DiscoveryError — finding devices
 * - IdentityError — device identity / signing
 * - PairingError — protocol pairing / bonding
 * - AuthenticationError — credentials / auth
 * - TransportError — network / protocol transport
 * - CommandError — device rejected / couldn't execute
 * - StateError — state reconciliation / observation
 * - PermissionError — Android / OS permissions
 * - ResourceError — hardware / memory / battery
 * - ConfigError — user configuration / schema
 */
sealed class NexusError {

    /** Human-readable error message. */
    abstract val message: String

    /** Error code for telemetry and automation. */
    abstract val code: NexusErrorCode

    /** Severity level. */
    abstract val severity: ErrorSeverity

    /** Whether this error is retryable. */
    abstract val isRetryable: Boolean

    /** The protocol involved, if any. */
    open val protocol: Protocol? get() = null

    /** The device involved, if any. */
    open val deviceId: DeviceId? get() = null

    /** Timestamp. */
    val timestampMs: Long = System.currentTimeMillis()

    // ── Discovery Errors ──────────────────────────

    data class DiscoveryFailed(
        override val message: String,
        override val protocol: Protocol? = null,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Warning
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.DiscoveryFailed
        override val isRetryable: Boolean = true
    }

    data class DeviceUnreachable(
        override val message: String,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Warning
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.DeviceUnreachable
        override val isRetryable: Boolean = true
    }

    // ── Identity Errors ───────────────────────────

    data class IdentityNotFound(
        override val message: String,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Error
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.IdentityNotFound
        override val isRetryable: Boolean = false
    }

    data class SignatureVerificationFailed(
        override val message: String,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Critical
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.SignatureVerificationFailed
        override val isRetryable: Boolean = false
    }

    // ── Pairing Errors ────────────────────────────

    data class PairingFailed(
        override val message: String,
        override val protocol: Protocol? = null,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Error
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.PairingFailed
        override val isRetryable: Boolean = true
    }

    data class PairingTimeout(
        override val message: String,
        override val protocol: Protocol? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Warning
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.PairingTimeout
        override val isRetryable: Boolean = true
    }

    // ── Authentication Errors ─────────────────────

    data class AuthFailed(
        override val message: String,
        override val protocol: Protocol? = null,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Error
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.AuthFailed
        override val isRetryable: Boolean = false
    }

    data class TokenExpired(
        override val message: String,
        override val protocol: Protocol? = null,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Warning
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.TokenExpired
        override val isRetryable: Boolean = true
    }

    // ── Transport Errors ──────────────────────────

    data class NetworkError(
        override val message: String,
        override val protocol: Protocol? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Warning
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.NetworkError
        override val isRetryable: Boolean = true
    }

    data class ProtocolError(
        override val message: String,
        override val protocol: Protocol? = null,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Error
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.ProtocolError
        override val isRetryable: Boolean = true
    }

    data class Timeout(
        override val message: String,
        override val protocol: Protocol? = null,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Warning
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.Timeout
        override val isRetryable: Boolean = true
    }

    // ── Command Errors ────────────────────────────

    data class CommandRejected(
        override val message: String,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Warning
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.CommandRejected
        override val isRetryable: Boolean = false
    }

    data class CommandUnsupported(
        override val message: String,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Info
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.CommandUnsupported
        override val isRetryable: Boolean = false
    }

    // ── State Errors ──────────────────────────────

    data class StateReconciliationFailed(
        override val message: String,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Warning
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.StateReconciliationFailed
        override val isRetryable: Boolean = true
    }

    data class StateUnknown(
        override val message: String,
        override val deviceId: DeviceId? = null,
        override val severity: ErrorSeverity = ErrorSeverity.Info
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.StateUnknown
        override val isRetryable: Boolean = true
    }

    // ── Permission Errors ─────────────────────────

    data class PermissionDenied(
        override val message: String,
        val missingPermissions: List<String> = emptyList(),
        override val severity: ErrorSeverity = ErrorSeverity.Error
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.PermissionDenied
        override val isRetryable: Boolean = false
    }

    // ── Resource Errors ───────────────────────────

    data class HardwareUnavailable(
        override val message: String,
        override val severity: ErrorSeverity = ErrorSeverity.Error
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.HardwareUnavailable
        override val isRetryable: Boolean = false
    }

    data class ResourceExhausted(
        override val message: String,
        override val severity: ErrorSeverity = ErrorSeverity.Warning
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.ResourceExhausted
        override val isRetryable: Boolean = true
    }

    // ── Config Errors ─────────────────────────────

    data class InvalidConfig(
        override val message: String,
        override val severity: ErrorSeverity = ErrorSeverity.Error
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.InvalidConfig
        override val isRetryable: Boolean = false
    }

    data class SchemaVersionMismatch(
        override val message: String,
        override val severity: ErrorSeverity = ErrorSeverity.Error
    ) : NexusError() {
        override val code: NexusErrorCode = NexusErrorCode.SchemaVersionMismatch
        override val isRetryable: Boolean = false
    }

    /**
     * Convert to a user-facing message. The message
     * includes what happened and what Elysium did.
     */
    fun toUserMessage(): String = when (this) {
        is DiscoveryFailed -> "Could not discover devices: $message. Retrying..."
        is DeviceUnreachable -> "Device is unreachable: $message. Checking connection..."
        is IdentityNotFound -> "Device identity not found: $message. Please re-pair."
        is SignatureVerificationFailed -> "Security check failed: $message. Device may be compromised."
        is PairingFailed -> "Pairing failed: $message. Please try again."
        is PairingTimeout -> "Pairing timed out: $message. Please try again."
        is AuthFailed -> "Authentication failed: $message. Please check credentials."
        is TokenExpired -> "Session expired: $message. Refreshing..."
        is NetworkError -> "Network error: $message. Retrying..."
        is ProtocolError -> "Protocol error: $message. Trying alternate route..."
        is Timeout -> "Timed out: $message. Retrying..."
        is CommandRejected -> "Device rejected command: $message."
        is CommandUnsupported -> "Command not supported: $message."
        is StateReconciliationFailed -> "State mismatch: $message. Retrying..."
        is StateUnknown -> "Device state unknown: $message."
        is PermissionDenied -> "Permission denied: $message. Grant permission in Settings."
        is HardwareUnavailable -> "Hardware unavailable: $message."
        is ResourceExhausted -> "Resource exhausted: $message. Retrying later..."
        is InvalidConfig -> "Configuration error: $message. Please check settings."
        is SchemaVersionMismatch -> "Schema mismatch: $message. Update may be required."
    }
}

/**
 * Error codes for telemetry and automation routing.
 */
enum class NexusErrorCode {
    DiscoveryFailed,
    DeviceUnreachable,
    IdentityNotFound,
    SignatureVerificationFailed,
    PairingFailed,
    PairingTimeout,
    AuthFailed,
    TokenExpired,
    NetworkError,
    ProtocolError,
    Timeout,
    CommandRejected,
    CommandUnsupported,
    StateReconciliationFailed,
    StateUnknown,
    PermissionDenied,
    HardwareUnavailable,
    ResourceExhausted,
    InvalidConfig,
    SchemaVersionMismatch
}

/**
 * Error severity for UI display and logging.
 */
enum class ErrorSeverity {
    /** Informational; no action needed. */
    Info,
    /** Something unexpected; user may want to act. */
    Warning,
    /** Something failed; user should act. */
    Error,
    /** Something critical; immediate attention. */
    Critical
}

/**
 * Retry policy for errors.
 */
data class RetryPolicy(
    val maxRetries: Int,
    val backoffMs: Long,
    val backoffMultiplier: Double = 2.0,
    val maxBackoffMs: Long = 30_000L
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be non-negative." }
        require(backoffMs >= 0) { "backoffMs must be non-negative." }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0." }
    }

    /**
     * Calculate the backoff delay for [attempt] (0-indexed).
     */
    fun backoffForAttempt(attempt: Int): Long {
        val delay = (backoffMs * Math.pow(backoffMultiplier, attempt.toDouble())).toLong()
        return delay.coerceAtMost(maxBackoffMs)
    }

    companion object {
        /** Default for network errors. */
        val NETWORK = RetryPolicy(maxRetries = 3, backoffMs = 1_000L)

        /** Default for protocol errors. */
        val PROTOCOL = RetryPolicy(maxRetries = 2, backoffMs = 500L)

        /** Default for discovery. */
        val DISCOVERY = RetryPolicy(maxRetries = 5, backoffMs = 2_000L)

        /** No retries. */
        val NONE = RetryPolicy(maxRetries = 0, backoffMs = 0L)
    }
}
