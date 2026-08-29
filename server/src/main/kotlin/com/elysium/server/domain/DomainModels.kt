package com.elysium.server.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class AggregateType {
    USER,
    VEHICLE,
    DIAGNOSTIC_SESSION,
    COMMUNICATION,
    RIDE,
    MARKET_REQUEST,
    WORK_ORDER,
    REPAIR,
    PAYMENT,
    LEGAL_MATTER,
}

@Serializable
enum class OutboxEventClass {
    DURABLE_DOMAIN,
    COMMAND,
    EPHEMERAL_STATE,
    HIGH_RATE_TELEMETRY,
    NOTIFICATION,
}

@Serializable
data class DomainEvent(
    val eventId: String,
    val sourceDomain: String,
    val sourceType: String,
    val sourceId: String,
    val aggregateType: AggregateType,
    val aggregateId: String,
    val aggregateVersion: Long,
    val eventType: String,
    val eventClass: OutboxEventClass = OutboxEventClass.DURABLE_DOMAIN,
    val payload: JsonElement,
    val correlationId: String? = null,
    val causationId: String? = null,
    val traceId: String? = null,
)
