package com.elysium.nexus.fabric.infrared

import android.util.Log
import com.elysium.nexus.core.device.IrSignal

private const val TAG = "ElysiumNexus.IrProtocol"

/**
 * Result of attempting to encode an [IrSignal] into an [IrWaveform].
 */
sealed interface EncodeResult {
    data class Success(val waveform: IrWaveform) : EncodeResult
    data class UnsupportedProtocol(val protocol: IrProtocol) : EncodeResult
    data class InvalidParameters(val reason: String) : EncodeResult
}

/**
 * Result of resolving a protocol string name from an IR catalog or source file.
 */
sealed interface ProtocolResolution {
    data class Supported(val protocol: IrProtocol) : ProtocolResolution
    data class RawPassthrough(val carrierHz: Int) : ProtocolResolution
    data class Unsupported(val originalName: String, val reason: String) : ProtocolResolution
}

/**
 * The §6.4 IR protocol catalog.
 */
enum class IrProtocol(
    /** Display name for the UI. */
    val displayName: String,
    /** Canonical carrier frequency in Hz. */
    val carrierHz: Int,
    /** The bit-encoding shape. */
    val encoding: IrEncoding
) {
    Nec("NEC", 38_000, IrEncoding.PulseDistance),
    NecExtended("NECx", 38_000, IrEncoding.PulseDistance),
    Rc5("RC5", 36_000, IrEncoding.Biphasic),
    Rc6("RC6", 36_000, IrEncoding.Biphasic),
    SonySirc("SIRC", 40_000, IrEncoding.PulseWidth),
    Samsung("Samsung", 38_000, IrEncoding.PulseDistance),
    Kaseikyo("Kaseikyo", 38_000, IrEncoding.PulseDistance),
    Aiwa("Aiwa", 38_123, IrEncoding.PulseDistance),
    Raw("Raw waveform", 38_000, IrEncoding.Raw);

    companion object {
        const val DEFAULT_CARRIER_HZ: Int = 38_000

        /**
         * Resolves protocol string name cleanly.
         * Evaluates NECx BEFORE NEC.
         * Returns [ProtocolResolution.Unsupported] for unknown protocols. Zero silent NEC fallback.
         */
        fun resolveProtocol(name: String, carrierHz: Int = DEFAULT_CARRIER_HZ): ProtocolResolution {
            val clean = name.trim()
            if (clean.equals("RAW", ignoreCase = true)) {
                return ProtocolResolution.RawPassthrough(carrierHz)
            }
            return when {
                clean.startsWith("NECx", ignoreCase = true) || clean.startsWith("NECext", ignoreCase = true) || clean.startsWith("NECx2", ignoreCase = true) ->
                    ProtocolResolution.Supported(NecExtended)
                clean.startsWith("NEC", ignoreCase = true) ->
                    ProtocolResolution.Supported(Nec)
                clean.startsWith("Samsung", ignoreCase = true) ->
                    ProtocolResolution.Supported(Samsung)
                clean.startsWith("SIRC", ignoreCase = true) || clean.startsWith("Sony", ignoreCase = true) ->
                    ProtocolResolution.Supported(SonySirc)
                clean.startsWith("RC5", ignoreCase = true) ->
                    ProtocolResolution.Supported(Rc5)
                clean.startsWith("RC6", ignoreCase = true) ->
                    ProtocolResolution.Supported(Rc6)
                clean.startsWith("Kaseikyo", ignoreCase = true) || clean.startsWith("Panasonic", ignoreCase = true) ->
                    ProtocolResolution.Supported(Kaseikyo)
                clean.startsWith("Aiwa", ignoreCase = true) ->
                    ProtocolResolution.Supported(Aiwa)
                else ->
                    ProtocolResolution.Unsupported(clean, "Protocol '$clean' is not registered in ProtocolCodecRegistry.")
            }
        }

        /**
         * Exhaustive encoder dispatching without silent fallback to NEC.
         * If a protocol is unsupported or parameters are invalid, returns explicit [EncodeResult].
         */
        fun encode(signal: IrSignal): EncodeResult {
            return when (signal) {
                is IrSignal.Raw -> {
                    if (signal.carrierHz !in 30_000..60_000) {
                        EncodeResult.InvalidParameters("Carrier frequency ${signal.carrierHz} Hz is out of valid IR range [30000, 60000] Hz")
                    } else {
                        try {
                            val waveform = IrWaveform(
                                carrierHz = signal.carrierHz,
                                pattern = signal.patternUs
                            )
                            EncodeResult.Success(waveform)
                        } catch (e: Exception) {
                            EncodeResult.InvalidParameters("Raw waveform invalid: ${e.message}")
                        }
                    }
                }
                is IrSignal.Encoded -> {
                    try {
                        when (signal.protocol) {
                            Nec -> EncodeResult.Success(
                                IrWaveform.encodeNec(signal.address, signal.command, carrierHz = signal.carrierHz)
                            )
                            NecExtended -> EncodeResult.Success(
                                IrWaveform.encodeNecExtended(signal.address, signal.command, carrierHz = signal.carrierHz)
                            )
                            Samsung -> EncodeResult.Success(
                                IrWaveform.encodeSamsung(signal.address, signal.command, carrierHz = signal.carrierHz)
                            )
                            SonySirc -> {
                                // V0.7 Phase 6: SIRC20 physical strictness.
                                // SIRC_12: address 5 bits
                                // SIRC_15: address 8 bits
                                // SIRC_20: address 5 bits + subDevice 8 bits (must be non-null)
                                when (signal.variantId) {
                                    "SIRC_12" -> {
                                        EncodeResult.Success(
                                            IrWaveform.encodeSonySirc(signal.address, signal.command, addressBits = 5, carrierHz = signal.carrierHz)
                                        )
                                    }
                                    "SIRC_15" -> {
                                        EncodeResult.Success(
                                            IrWaveform.encodeSonySirc(signal.address, signal.command, addressBits = 8, carrierHz = signal.carrierHz)
                                        )
                                    }
                                    "SIRC_20" -> {
                                        val subDev = signal.subDevice
                                            ?: return EncodeResult.InvalidParameters(
                                                "SIRC_20 requires a non-null 8-bit subDevice. Missing subdevice forbidden."
                                            )
                                        require(signal.address in 0..31) { "SIRC_20 5-bit address must be in [0, 31] (got ${signal.address})" }
                                        require(subDev in 0..255) { "SIRC_20 8-bit subDevice must be in [0, 255] (got $subDev)" }
                                        // 13-bit combined field: 5 bits address (LSB) + 8 bits subDevice (MSB)
                                        val combinedAddress = (subDev shl 5) or (signal.address and 0x1F)
                                        EncodeResult.Success(
                                            IrWaveform.encodeSonySirc(combinedAddress, signal.command, addressBits = 13, carrierHz = signal.carrierHz)
                                        )
                                    }
                                    null -> {
                                        return EncodeResult.InvalidParameters(
                                            "SIRC encoder: variantId is null. Caller must specify SIRC_12, SIRC_15, or SIRC_20."
                                        )
                                    }
                                    else -> {
                                        return EncodeResult.InvalidParameters(
                                            "SIRC encoder: unsupported variantId '${signal.variantId}'. " +
                                                "Available: SIRC_12, SIRC_15, SIRC_20"
                                        )
                                    }
                                }
                            }
                            Rc5 -> EncodeResult.Success(
                                IrWaveform.encodeRc5(signal.address, signal.command, signal.toggle, carrierHz = signal.carrierHz)
                            )
                            Rc6 -> EncodeResult.Success(
                                IrWaveform.encodeRc6(signal.address, signal.command, signal.toggle, carrierHz = signal.carrierHz)
                            )
                            Kaseikyo -> EncodeResult.Success(
                                IrWaveform.encodeKaseikyo(signal.address, signal.command, carrierHz = signal.carrierHz)
                            )
                            Aiwa -> {
                                // V0.7 Phase 7: Aiwa physical strictness.
                                // subDevice MUST NOT default to 0. Fail closed if null.
                                val subDev = signal.subDevice
                                    ?: return EncodeResult.InvalidParameters(
                                        "Aiwa protocol requires a non-null subDevice. Defaulting to 0 is forbidden."
                                    )
                                EncodeResult.Success(
                                    IrWaveform.encodeAiwa(
                                        signal.address,
                                        subDev,
                                        signal.command,
                                        carrierHz = signal.carrierHz
                                    )
                                )
                            }
                            Raw -> EncodeResult.InvalidParameters("Raw protocol must use IrSignal.Raw payload.")
                        }
                    } catch (e: Exception) {
                        EncodeResult.InvalidParameters("Encoder exception for ${signal.protocol}: ${e.message}")
                    }
                }
            }
        }
    }
}

/**
 * The bit-encoding shape.
 */
enum class IrEncoding {
    PulseDistance,
    PulseWidth,
    Biphasic,
    Raw
}
