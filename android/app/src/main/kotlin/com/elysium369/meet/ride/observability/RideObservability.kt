package com.elysium369.meet.ride.observability

import android.util.Log
import com.elysium369.meet.observability.MeetTelemetry
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class RideTelemetryEventType {
    RIDE_CREATED, RIDE_PUBLISHED, OFFER_SUBMITTED, OFFER_ACCEPTED, ASSIGNMENT_WON,
    ASSIGNMENT_LOST, DRIVER_EN_ROUTE, DRIVER_ARRIVED, PIN_ISSUED, PIN_VERIFIED,
    RIDE_STARTED, RIDE_COMPLETED, RIDE_CANCELLED, SAFETY_CHECK_TRIGGERED,
    SUPPORT_CASE_OPENED, SYNC_RECOVERED, SYNC_FAILED,
}

@Serializable
data class RideTelemetryEvent(
    val eventType: String,
    val eventId: String,
    val correlationId: String? = null,
    val commandId: String? = null,
    val tripId: String? = null,
    val tenantId: String? = null,
    val version: Long? = null,
    val latencyMs: Long? = null,
    val errorCode: String? = null,
    val occurredAtEpochMs: Long,
)

/** Low-cardinality ride diagnostics. Never logs names, phones, coordinates, or tokens. */
object RideObservability {
    private const val TAG = "MeetRidesEvent"
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    fun event(name: String, outcome: String = "INFO", requestId: String? = null, count: Int? = null, detail: String? = null) {
        val fields = buildString {
            append("event=").append(name.take(64))
            append(" outcome=").append(outcome.take(32))
            append(" trace=").append(UUID.randomUUID().toString().take(8))
            requestId?.let { append(" request=").append(it.takeLast(8)) }
            count?.let { append(" count=").append(it.coerceAtLeast(0)) }
            detail?.let { append(" detail=").append(it.replace(Regex("[^A-Za-z0-9_.:/=-]"), "_").take(120)) }
        }
        when (outcome) {
            "FAILED", "REJECTED" -> Log.w(TAG, fields)
            else -> Log.i(TAG, fields)
        }
    }

    fun event(
        type: RideTelemetryEventType,
        commandId: String? = null,
        tripId: String? = null,
        version: Long? = null,
        latencyMs: Long? = null,
        correlationId: String? = null,
        errorCode: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): RideTelemetryEvent = RideTelemetryEvent(
        eventType = type.name,
        eventId = UUID.randomUUID().toString(),
        correlationId = correlationId.safeIdentifier(),
        commandId = commandId.safeIdentifier(),
        tripId = tripId.safeIdentifier(),
        version = version?.takeIf { it >= 0L },
        latencyMs = latencyMs?.coerceAtLeast(0L),
        errorCode = errorCode?.uppercase()?.takeIf { it.matches(Regex("[A-Z0-9_]{1,80}")) },
        occurredAtEpochMs = nowEpochMs,
    )

    fun encode(event: RideTelemetryEvent): String {
        return json.encodeToString(event)
    }

    fun record(event: RideTelemetryEvent) {
        Log.i(TAG, encode(event))
        MeetTelemetry.event(
            name = "ride.${event.eventType.lowercase()}",
            attributes = mapOf("vertical" to "RIDES", "operation" to event.eventType, "latencyMs" to event.latencyMs, "failureCode" to event.errorCode),
            correlationId = event.correlationId ?: event.eventId,
        )
    }

    private fun String?.safeIdentifier(): String? = this?.trim()?.takeIf { it.length in 1..160 }?.takeIf { value ->
        value.all { character -> character.isLetterOrDigit() || character in setOf('-', '_', '.', ':') }
    }
}
