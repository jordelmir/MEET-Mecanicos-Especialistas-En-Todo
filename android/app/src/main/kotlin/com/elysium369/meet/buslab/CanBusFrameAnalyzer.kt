package com.elysium369.meet.buslab

import com.elysium369.meet.core.transport.RawCanFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DbcSignalDefinition(
    val name: String,
    val startBit: Int,
    val bitLength: Int,
    val isLittleEndian: Boolean = true,
    val isSigned: Boolean = false,
    val scale: Double = 1.0,
    val offset: Double = 0.0,
    val unit: String = "",
)

data class DbcMessageDefinition(
    val arbitrationId: Int,
    val name: String,
    val signals: List<DbcSignalDefinition>,
)

data class DecodedCanSignal(
    val name: String,
    val rawValue: Long,
    val physicalValue: Double,
    val unit: String,
)

data class CanBusStatistics(
    val totalFramesReceived: Long = 0,
    val errorFrames: Long = 0,
    val busLoadPercent: Float = 0f,
    val averageFrameRateHz: Float = 0f,
)

/**
 * CanBusFrameAnalyzer — Vehicle Bus Lab engine for raw CAN 2.0 / CAN-FD frame parsing and DBC signal extraction.
 */
object CanBusFrameAnalyzer {

    fun decodeSignals(frame: RawCanFrame, messageDef: DbcMessageDefinition): List<DecodedCanSignal> {
        if (frame.arbitrationId != messageDef.arbitrationId || frame.data.isEmpty()) {
            return emptyList()
        }

        val results = mutableListOf<DecodedCanSignal>()
        val payloadLong = ByteBuffer.wrap(frame.data.copyOf(8).reversedArray())
            .order(ByteOrder.BIG_ENDIAN)
            .long

        for (sig in messageDef.signals) {
            val bitMask = (1L shl sig.bitLength) - 1L
            val rawUnshifted = payloadLong ushr (64 - (sig.startBit + sig.bitLength))
            val rawVal = rawUnshifted and bitMask

            val physicalVal = (rawVal * sig.scale) + sig.offset
            results.add(
                DecodedCanSignal(
                    name = sig.name,
                    rawValue = rawVal,
                    physicalValue = physicalVal,
                    unit = sig.unit,
                )
            )
        }

        return results
    }

    fun computeBusLoad(framesInWindow: Int, windowDurationMs: Long, nominalBitrate: Int = 500_000): Float {
        if (windowDurationMs <= 0) return 0f
        // Average CAN frame is ~110 bits on the wire
        val bitsTransferred = framesInWindow * 110L
        val maxBitsPossible = (nominalBitrate * windowDurationMs) / 1000.0
        val load = (bitsTransferred / maxBitsPossible) * 100f
        return load.toFloat().coerceIn(0f, 100f)
    }
}
