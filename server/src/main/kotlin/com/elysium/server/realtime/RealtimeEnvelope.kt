package com.elysium.server.realtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * ERP/1 — Elysium Realtime Protocol version 1.
 *
 * All messages between client and server use this envelope.
 * Sequence numbers are per-connection, monotonically increasing.
 * Cursor is the last sequence number the client has processed.
 */
@Serializable
data class RealtimeEnvelope(
    val protocolVersion: Int = 1,
    val eventId: String = java.util.UUID.randomUUID().toString(),
    val eventType: String,
    val occurredAt: Long = System.currentTimeMillis(),
    val aggregateType: String? = null,
    val aggregateId: String? = null,
    val aggregateVersion: Long? = null,
    val sequence: Long = 0,
    val correlationId: String? = null,
    val causationId: String? = null,
    val traceId: String? = null,
    val payloadVersion: Int = 1,
    val payload: JsonElement? = null,
)

/** Client → Server command types */
@Serializable
sealed class ClientCommand {
    @Serializable
    @SerialName("SUBSCRIBE")
    data class Subscribe(
        val channels: List<String>,
    ) : ClientCommand()

    @Serializable
    @SerialName("UNSUBSCRIBE")
    data class Unsubscribe(
        val channels: List<String>,
    ) : ClientCommand()

    @Serializable
    @SerialName("PING")
    data class Ping(
        val clientTimestamp: Long = System.currentTimeMillis(),
    ) : ClientCommand()

    @Serializable
    @SerialName("RESUME")
    data class Resume(
        val lastCursor: Long,
        val channels: List<String>? = null,
    ) : ClientCommand()
}

/** Server → Client control messages */
@Serializable
sealed class ServerControl {
    @Serializable
    @SerialName("WELCOME")
    data class Welcome(
        val protocolVersion: Int = 1,
        val serverVersion: String = "1.0.0",
        val heartbeatIntervalMs: Long = 25_000L,
        val connectionId: String,
        val maxBufferSize: Int = 1_000,
    ) : ServerControl()

    @Serializable
    @SerialName("PONG")
    data class Pong(
        val serverTimestamp: Long = System.currentTimeMillis(),
        val clientTimestamp: Long = 0L,
    ) : ServerControl()

    @Serializable
    @SerialName("SUBSCRIBED")
    data class Subscribed(
        val channels: List<String>,
        val cursor: Long = 0L,
    ) : ServerControl()

    @Serializable
    @SerialName("UNSUBSCRIBED")
    data class Unsubscribed(
        val channels: List<String>,
    ) : ServerControl()

    @Serializable
    @SerialName("EVENT")
    data class Event(
        val envelope: RealtimeEnvelope,
    ) : ServerControl()

    @Serializable
    @SerialName("REPLAY_START")
    data class ReplayStart(
        val fromCursor: Long,
        val toCursor: Long,
        val channel: String,
    ) : ServerControl()

    @Serializable
    @SerialName("REPLAY_END")
    data class ReplayEnd(
        val fromCursor: Long,
        val toCursor: Long,
        val channel: String,
        val eventCount: Int,
    ) : ServerControl()

    @Serializable
    @SerialName("GAP_DETECTED")
    data class GapDetected(
        val expectedCursor: Long,
        val receivedCursor: Long,
        val channel: String,
    ) : ServerControl()

    @Serializable
    @SerialName("ERROR")
    data class Error(
        val code: String,
        val message: String,
        val retryable: Boolean = false,
    ) : ServerControl()

    @Serializable
    @SerialName("BUFFER_OVERFLOW")
    data class BufferOverflow(
        val lastDeliveredCursor: Long,
        val message: String = "Client buffer overflow. Resume from last delivered cursor.",
    ) : ServerControl()
}
