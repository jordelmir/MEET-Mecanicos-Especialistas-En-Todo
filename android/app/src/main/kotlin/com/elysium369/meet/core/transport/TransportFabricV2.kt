package com.elysium369.meet.core.transport

import com.elysium369.meet.core.obd.TransportLinkEvent
import com.elysium369.meet.core.obd.TransportLinkState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Raw CAN frame model for Direct Frame Transports (SocketCAN, gs_usb / CandleLight).
 */
data class RawCanFrame(
    val arbitrationId: Int,
    val isExtended: Boolean,
    val isFd: Boolean,
    val data: ByteArray,
    val timestampNanos: Long = System.nanoTime(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RawCanFrame
        if (arbitrationId != other.arbitrationId) return false
        if (isExtended != other.isExtended) return false
        if (isFd != other.isFd) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = arbitrationId
        result = 31 * result + isExtended.hashCode()
        result = 31 * result + isFd.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    fun toHex(): String = data.joinToString(" ") { "%02X".format(it) }
}

enum class TransportFamily {
    BYTE_STREAM,
    CAN_FRAME,
    IP_PACKET,
}

/**
 * Root VehicleTransport Interface — Baseline for all transport families.
 */
sealed interface VehicleTransport {
    val transportFamily: TransportFamily
    val isConnected: Boolean
    val linkState: StateFlow<TransportLinkState>
    val linkEvents: SharedFlow<TransportLinkEvent>

    suspend fun connect()
    fun abortConnect()
    suspend fun disconnect()
}

/**
 * ByteStreamTransport — For character/byte-stream protocols (ELM327, STN, USB-UART CDC ACM/FTDI/CH340/CP210x, BT Classic, BLE, WiFi TCP).
 */
interface ByteStreamTransport : VehicleTransport, TransportInterface {
    override val transportFamily: TransportFamily get() = TransportFamily.BYTE_STREAM
}

/**
 * FrameTransport — For native raw CAN/LIN frame protocols (gs_usb, CandleLight, Linux SocketCAN).
 */
interface FrameTransport : VehicleTransport {
    override val transportFamily: TransportFamily get() = TransportFamily.CAN_FRAME

    suspend fun sendFrame(frame: RawCanFrame)
    fun receiveFrames(): Flow<RawCanFrame>
    suspend fun setBitrate(bitrateBps: Int): Boolean
}

/**
 * PacketTransport — For structured frame packets (DoIP ISO 13400, UDS over IP).
 */
interface PacketTransport : VehicleTransport {
    override val transportFamily: TransportFamily get() = TransportFamily.IP_PACKET

    suspend fun sendPacket(packet: ByteArray)
    fun receivePackets(): Flow<ByteArray>
}
