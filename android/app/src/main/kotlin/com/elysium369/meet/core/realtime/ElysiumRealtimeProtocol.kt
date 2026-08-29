package com.elysium369.meet.core.realtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * ERP/1 — Elysium Realtime Protocol Specification Types.
 */
@Serializable
enum class RealtimeEventClass {
    DURABLE_DOMAIN,
    COMMAND,
    EPHEMERAL_STATE,
    HIGH_RATE_TELEMETRY,
    NOTIFICATION,
}

@Serializable
data class RealtimeEventEnvelope(
    val protocolVersion: Int = 1,
    val eventId: String,
    val eventType: String,
    val eventClass: RealtimeEventClass = RealtimeEventClass.DURABLE_DOMAIN,
    val occurredAt: String,
    val aggregateType: String,
    val aggregateId: String,
    val aggregateVersion: Long = 1,
    val streamSequence: Long = 0,
    val correlationId: String? = null,
    val causationId: String? = null,
    val traceId: String? = null,
    val payloadVersion: Int = 1,
    val payload: JsonElement,
)

@Serializable
sealed interface RealtimeControlFrame {
    @Serializable
    data class Hello(val clientVersion: String, val authToken: String, val deviceId: String) : RealtimeControlFrame

    @Serializable
    data class Welcome(val serverVersion: String, val connectionId: String, val heartbeatIntervalMs: Long) : RealtimeControlFrame

    @Serializable
    data class Subscribe(val channel: String, val lastCursor: Long? = null) : RealtimeControlFrame

    @Serializable
    data class Subscribed(val channel: String, val streamSequence: Long) : RealtimeControlFrame

    @Serializable
    data class Unsubscribe(val channel: String) : RealtimeControlFrame

    @Serializable
    data class Resume(val connectionId: String, val resumeToken: String, val cursors: Map<String, Long>) : RealtimeControlFrame

    @Serializable
    data class Resumed(val connectionId: String, val replayedEventsCount: Int) : RealtimeControlFrame

    @Serializable
    data class Ping(val timestamp: Long) : RealtimeControlFrame

    @Serializable
    data class Pong(val timestamp: Long) : RealtimeControlFrame

    @Serializable
    data class Error(val code: String, val message: String, val retryable: Boolean = false) : RealtimeControlFrame
}
