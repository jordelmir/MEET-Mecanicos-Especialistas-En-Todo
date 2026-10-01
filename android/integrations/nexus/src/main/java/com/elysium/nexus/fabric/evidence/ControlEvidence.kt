package com.elysium.nexus.fabric.evidence

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol
import java.security.MessageDigest

/**
 * The §8 control evidence store.
 *
 * Records every control action attempt with enough
 * data for diagnostics and compatibility tracking,
 * but **no PII**. Device IDs are hashed; no user
 * names, locations, or account data are stored.
 *
 * ## Ring buffer
 *
 * The store keeps the last [maxEvents] events in
 * memory. Older events are evicted FIFO. For
 * persistent storage, the store can be drained
 * to a database (future work).
 *
 * ## Query
 *
 * [query] filters by time range, action type,
 * result type, and protocol.
 */
class ControlEvidenceStore(
    private val maxEvents: Int = 1000
) {
    init {
        require(maxEvents > 0) { "maxEvents must be positive (got $maxEvents)." }
    }

    private val events = ArrayDeque<ControlEvent>(maxEvents)

    /** Record a control event. */
    fun record(event: ControlEvent) {
        if (events.size >= maxEvents) {
            events.removeFirst()
        }
        events.addLast(event)
    }

    /** Total events stored. */
    val size: Int get() = events.size

    /** All events (newest last). */
    fun all(): List<ControlEvent> = events.toList()

    /** Clear all events. */
    fun clear() = events.clear()

    /**
     * Query events with optional filters.
     * All filters are AND-combined.
     */
    fun query(
        fromNs: Long? = null,
        toNs: Long? = null,
        actionType: String? = null,
        result: EventResult? = null,
        protocol: Protocol? = null
    ): List<ControlEvent> = events.filter { event ->
        (fromNs == null || event.timestampNs >= fromNs) &&
        (toNs == null || event.timestampNs <= toNs) &&
        (actionType == null || event.actionType == actionType) &&
        (result == null || event.result == result) &&
        (protocol == null || event.protocol == protocol)
    }

    /** Count events by result type. */
    fun countByResult(): Map<EventResult, Int> =
        events.groupBy { it.result }.mapValues { it.value.size }

    /** Average latency for successful events, or null if none. */
    fun averageSuccessLatencyMs(): Double? {
        val successes = events.filter { it.result == EventResult.Success && it.latencyMs != null }
        if (successes.isEmpty()) return null
        return successes.mapNotNull { it.latencyMs }.average()
    }

    companion object {
        /** Hash a device ID for PII-free storage. */
        fun hashDeviceId(deviceId: DeviceId): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(deviceId.value.toByteArray(Charsets.UTF_8))
            return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
        }
    }
}

/**
 * A single control event record.
 *
 * All fields are PII-free. [deviceIdHash] is the
 * SHA-256 prefix of the device ID, not the ID itself.
 */
data class ControlEvent(
    /** Wall-clock nanos when the event occurred. */
    val timestampNs: Long,
    /** Hashed device ID (first 16 hex chars of SHA-256). */
    val deviceIdHash: String,
    /** The canonical action type name (e.g. "PowerOn", "VolumeUp"). */
    val actionType: String,
    /** Correlation ID for end-to-end tracing. */
    val correlationId: String,
    /** The protocol used (or attempted). */
    val protocol: Protocol,
    /** The result of the action. */
    val result: EventResult,
    /** Round-trip latency in milliseconds, if measured. */
    val latencyMs: Long? = null,
    /** Error message if result is not Success. No PII. */
    val errorMessage: String? = null
) {
    init {
        require(timestampNs >= 0L) { "timestampNs must be non-negative." }
        require(deviceIdHash.isNotBlank()) { "deviceIdHash must be non-blank." }
        require(actionType.isNotBlank()) { "actionType must be non-blank." }
        require(correlationId.isNotBlank()) { "correlationId must be non-blank." }
    }
}

/**
 * The result of a control action attempt.
 */
enum class EventResult {
    /** Action was delivered successfully. */
    Success,
    /** Action failed at the adapter level. */
    AdapterError,
    /** Action failed due to missing permission. */
    PermissionDenied,
    /** No route was available for the action. */
    NoRoute,
    /** Action timed out. */
    Timeout,
    /** Action was retried on a fallback route. */
    Fallback,
    /** Action was neutralized (disconnect cleanup). */
    Neutralized
}
