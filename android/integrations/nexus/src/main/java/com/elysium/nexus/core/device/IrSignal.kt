package com.elysium.nexus.core.device

import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.IrWaveform

/**
 * Domain representation of an IR physical signal.
 */
sealed interface IrSignal {
    val carrierHz: Int

    /**
     * Protocol-encoded signal with address, optional sub-device, command, and repeat parameters.
     * P0-13: codecId + variantId preserve protocol variant through to encoder.
     */
    data class Encoded(
        override val carrierHz: Int,
        val protocol: IrProtocol,
        val address: Int,
        val subDevice: Int? = null,
        val command: Int,
        val repeats: Int = 0,
        val toggle: Int = 0,
        /** P0-13: Canonical codec ID (e.g., "NEC", "SIRC", "SAMSUNG"). Preserves identity through dispatch. */
        val codecId: String? = null,
        /** P0-13: Protocol variant ID (e.g., "SIRC_12", "SIRC_15", "NEC_32"). Selects encoding shape. */
        val variantId: String? = null
    ) : IrSignal

    /**
     * Raw microsecond timing sequence signal.
     */
    data class Raw(
        override val carrierHz: Int,
        val patternUs: IntArray
    ) : IrSignal {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Raw) return false
            return carrierHz == other.carrierHz && patternUs.contentEquals(other.patternUs)
        }

        override fun hashCode(): Int = 31 * carrierHz + patternUs.contentHashCode()
    }
}
