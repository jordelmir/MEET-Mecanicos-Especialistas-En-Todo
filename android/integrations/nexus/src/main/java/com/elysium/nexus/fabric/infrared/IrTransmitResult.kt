package com.elysium.nexus.fabric.infrared

/**
 * The typed result of an IR pattern transmission attempt via
 * [AndroidIrTransmitter].
 *
 * Replaces ambiguous Boolean return values to provide clear, actionable
 * status information to UI components and diagnostic logs.
 */
sealed interface IrTransmitResult {
    /**
     * The pattern was successfully accepted by the Android IR service.
     *
     * @property carrierHz The carrier frequency in Hz used for transmission.
     * @property durationUs The total duration of the IR burst in microseconds.
     * @property patternHash A SHA-256 fingerprint of the pattern for telemetry and deduplication.
     */
    data class Success(
        val carrierHz: Int,
        val durationUs: Long,
        val patternHash: String
    ) : IrTransmitResult

    /** The device does not possess a physical IR emitter or ConsumerIR service. */
    data object NoEmitter : IrTransmitResult

    /** The app lacks the `android.permission.TRANSMIT_IR` system permission. */
    data object PermissionDenied : IrTransmitResult

    /**
     * The requested carrier frequency is not supported by the hardware emitter.
     *
     * @property requestedHz The carrier frequency requested.
     * @property supportedRanges The list of hardware-supported frequency ranges.
     */
    data class UnsupportedCarrier(
        val requestedHz: Int,
        val supportedRanges: List<IntRange>
    ) : IrTransmitResult

    /**
     * The pattern failed validation (e.g. empty, non-positive slice, duration > 2s).
     *
     * @property reason Plain-language explanation of why the pattern was invalid.
     */
    data class InvalidPattern(val reason: String) : IrTransmitResult

    /** Transmission queue was busy; another burst was currently in flight. */
    data object Busy : IrTransmitResult

    /**
     * An unexpected system exception occurred during transmission.
     *
     * @property cause The underlying exception caught.
     */
    data class PlatformFailure(val cause: Throwable) : IrTransmitResult
}
