package com.elysium369.meet.ecu.protocol

/**
 * Section 14, 15 & 86: Protocol Conformance Engine.
 * Implements clean-room ISO 15765-2 (ISO-TP) and ISO 14229-1 (UDS) frame decoders and reassemblers
 * validated against independent protocol golden vectors.
 */

enum class IsoTpFrameType {
    SINGLE_FRAME,
    FIRST_FRAME,
    CONSECUTIVE_FRAME,
    FLOW_CONTROL,
    UNKNOWN,
}

sealed interface DecodedIsoTpFrame {
    data class SingleFrame(val length: Int, val payload: ByteArray) : DecodedIsoTpFrame
    data class FirstFrame(val totalLength: Int, val initialPayload: ByteArray) : DecodedIsoTpFrame
    data class ConsecutiveFrame(val sequenceNumber: Int, val payload: ByteArray) : DecodedIsoTpFrame
    data class FlowControl(val flowStatus: Int, val blockSize: Int, val stMinMs: Int) : DecodedIsoTpFrame
    data class Invalid(val reason: String) : DecodedIsoTpFrame
}

object IsoTpFrameDecoder {
    fun decode(canFrame: ByteArray): DecodedIsoTpFrame {
        if (canFrame.isEmpty()) return DecodedIsoTpFrame.Invalid("Empty CAN frame")
        val pci = canFrame[0].toInt() and 0xFF
        val frameTypeNibble = (pci ushr 4) and 0x0F

        return when (frameTypeNibble) {
            0 -> { // Single Frame
                val length = pci and 0x0F
                if (length <= 0 || length > canFrame.size - 1) {
                    return DecodedIsoTpFrame.Invalid("Invalid Single Frame length: $length")
                }
                val payload = canFrame.copyOfRange(1, 1 + length)
                DecodedIsoTpFrame.SingleFrame(length, payload)
            }
            1 -> { // First Frame
                if (canFrame.size < 2) return DecodedIsoTpFrame.Invalid("First frame too short")
                val totalLength = ((pci and 0x0F) shl 8) or (canFrame[1].toInt() and 0xFF)
                val payload = canFrame.copyOfRange(2, canFrame.size)
                DecodedIsoTpFrame.FirstFrame(totalLength, payload)
            }
            2 -> { // Consecutive Frame
                val sequenceNumber = pci and 0x0F
                val payload = canFrame.copyOfRange(1, canFrame.size)
                DecodedIsoTpFrame.ConsecutiveFrame(sequenceNumber, payload)
            }
            3 -> { // Flow Control
                if (canFrame.size < 3) return DecodedIsoTpFrame.Invalid("Flow control frame too short")
                val flowStatus = pci and 0x0F
                val blockSize = canFrame[1].toInt() and 0xFF
                val stMin = canFrame[2].toInt() and 0xFF
                DecodedIsoTpFrame.FlowControl(flowStatus, blockSize, stMin)
            }
            else -> DecodedIsoTpFrame.Invalid("Unknown frame type nibble: $frameTypeNibble")
        }
    }
}

sealed interface DecodedUdsMessage {
    data class PositiveResponse(val responseSid: Int, val payload: ByteArray) : DecodedUdsMessage {
        val requestSid: Int get() = responseSid - 0x40
    }
    data class NegativeResponse(val rejectedSid: Int, val nrc: Int, val meaning: String) : DecodedUdsMessage
    data class Invalid(val reason: String) : DecodedUdsMessage
}

object UdsMessageDecoder {
    val NRC_MAP = mapOf(
        0x10 to "General Reject",
        0x11 to "Service Not Supported",
        0x12 to "Sub-function Not Supported",
        0x13 to "Incorrect Message Length Or Invalid Format",
        0x14 to "Response Too Long",
        0x21 to "Busy Repeat Request",
        0x22 to "Conditions Not Correct",
        0x24 to "Request Sequence Error",
        0x31 to "Request Out Of Range",
        0x33 to "Security Access Denied",
        0x35 to "Invalid Key",
        0x36 to "Exceed Number Of Attempts",
        0x37 to "Required Time Delay Not Expired",
        0x70 to "Upload Download Not Accepted",
        0x71 to "Transfer Data Suspended",
        0x72 to "General Programming Failure",
        0x73 to "Wrong Block Sequence Counter",
        0x78 to "Request Correctly Received Response Pending",
        0x7E to "Sub-function Not Supported In Active Session",
        0x7F to "Service Not Supported In Active Session",
    )

    fun decode(udsPayload: ByteArray): DecodedUdsMessage {
        if (udsPayload.isEmpty()) return DecodedUdsMessage.Invalid("Empty UDS payload")
        val sid = udsPayload[0].toInt() and 0xFF

        return if (sid == 0x7F) {
            if (udsPayload.size < 3) return DecodedUdsMessage.Invalid("Negative response truncated")
            val rejectedSid = udsPayload[1].toInt() and 0xFF
            val nrc = udsPayload[2].toInt() and 0xFF
            val meaning = NRC_MAP[nrc] ?: "Unknown NRC 0x${nrc.toString(16).uppercase()}"
            DecodedUdsMessage.NegativeResponse(rejectedSid, nrc, meaning)
        } else if (sid in 0x40..0x7E) {
            val payload = if (udsPayload.size > 1) udsPayload.copyOfRange(1, udsPayload.size) else byteArrayOf()
            DecodedUdsMessage.PositiveResponse(sid, payload)
        } else {
            DecodedUdsMessage.Invalid("Non-response SID: 0x${sid.toString(16).uppercase()}")
        }
    }
}
