package com.elysium369.meet.mobility.data.protocol

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun JsonObject.requireString(
    field: String,
): String {
    return this[field]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
        ?: throw ProtocolViolation(
            field = field,
            violation = "required non-empty string missing",
        )
}

internal fun JsonObject.requireUuid(
    field: String,
): UUID {
    val raw = requireString(field)
    return runCatching {
        UUID.fromString(raw)
    }.getOrElse {
        throw ProtocolViolation(
            field = field,
            violation = "invalid UUID: $raw",
        )
    }
}

internal fun JsonObject.requireLong(
    field: String,
): Long {
    return this[field]
        ?.jsonPrimitive
        ?.longOrNull
        ?: throw ProtocolViolation(
            field = field,
            violation = "required integer missing",
        )
}

internal fun JsonObject.requireDouble(
    field: String,
    alternateField: String? = null,
): Double {
    val element = this[field] ?: (alternateField?.let { this[it] })
    val raw = element?.jsonPrimitive?.contentOrNull
    return raw?.toDoubleOrNull()
        ?: throw ProtocolViolation(
            field = field,
            violation = "required double missing or invalid: $raw",
        )
}

internal fun JsonObject.requireInstant(
    field: String,
): Instant {
    val raw = requireString(field)
    return runCatching {
        Instant.parse(raw)
    }.getOrElse {
        throw ProtocolViolation(
            field = field,
            violation = "invalid RFC-3339 timestamp: $raw",
        )
    }
}

internal fun JsonObject.optionalString(
    field: String,
): String? {
    return this[field]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
}

internal fun JsonObject.optionalUuid(
    field: String,
): UUID? {
    val raw = optionalString(field) ?: return null
    return runCatching {
        UUID.fromString(raw)
    }.getOrElse {
        throw ProtocolViolation(
            field = field,
            violation = "invalid UUID: $raw",
        )
    }
}

internal fun JsonObject.optionalLong(
    field: String,
): Long? {
    return this[field]
        ?.jsonPrimitive
        ?.longOrNull
}

internal fun JsonObject.optionalDouble(
    field: String,
): Double? {
    val raw = optionalString(field) ?: return null
    return raw.toDoubleOrNull()
        ?: throw ProtocolViolation(
            field = field,
            violation = "invalid double: $raw",
        )
}

internal fun JsonObject.optionalFloat(
    field: String,
): Float? {
    val raw = optionalString(field) ?: return null
    return raw.toFloatOrNull()
        ?: throw ProtocolViolation(
            field = field,
            violation = "invalid float: $raw",
        )
}

internal fun JsonObject.optionalInstant(
    field: String,
): Instant? {
    val raw = optionalString(field) ?: return null
    return runCatching {
        Instant.parse(raw)
    }.getOrElse {
        throw ProtocolViolation(
            field = field,
            violation = "invalid RFC-3339 timestamp: $raw",
        )
    }
}
