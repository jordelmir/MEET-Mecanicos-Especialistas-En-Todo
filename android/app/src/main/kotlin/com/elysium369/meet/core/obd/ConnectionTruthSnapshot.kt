package com.elysium369.meet.core.obd

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Human Intent Authority.
 * Physical scanner connections may only transition to CONNECT_REQUESTED
 * via explicit, direct human action. Background watchdogs, services,
 * screen transitions, and vehicle selection are forbidden from doing so.
 */
enum class ConnectionIntent {
    DISCONNECTED,
    CONNECT_REQUESTED,
}

/**
 * Exception thrown when any component attempts physical connection,
 * reconnection, or hardware activation while ConnectionIntent is DISCONNECTED.
 */
class ConnectionIntentDeniedException(
    message: String = "Physical OBD connection rejected: user intent is DISCONNECTED"
) : IllegalStateException(message)

/**
 * Authoritative lifecycle state of the physical transport link (BT Classic, BLE, WiFi).
 */
sealed interface TransportLinkState {
    val isPhysicalConnected: Boolean get() = this is Connected

    data object Disconnected : TransportLinkState
    data object Connecting : TransportLinkState
    data object Connected : TransportLinkState
    data object Closing : TransportLinkState
    data class RemoteClosed(
        val reason: String? = null,
        val timestampMonotonicMs: Long = System.nanoTime() / 1_000_000L
    ) : TransportLinkState
    data class IoFailure(
        val cause: Throwable,
        val timestampMonotonicMs: Long = System.nanoTime() / 1_000_000L
    ) : TransportLinkState
}

/**
 * Asynchronous physical transport events emitted immediately upon hardware transitions.
 */
sealed interface TransportLinkEvent {
    data class StateChanged(val state: TransportLinkState) : TransportLinkEvent
    data class RemoteClosed(val reason: String?, val timestampMonotonicMs: Long) : TransportLinkEvent
    data class IoFailure(val cause: Throwable, val timestampMonotonicMs: Long) : TransportLinkEvent
    data class BytesReceived(val count: Int, val timestampMonotonicMs: Long) : TransportLinkEvent
    data class BytesSent(val count: Int, val timestampMonotonicMs: Long) : TransportLinkEvent
}

/**
 * Layer 1: ELM327 / STN IC readiness state.
 */
enum class ElmLinkState {
    UNKNOWN,
    SYNCING,
    READY,
    NOT_APPLICABLE,
    UNRESPONSIVE,
}

/**
 * Layer 2: Vehicle bus protocol negotiation state.
 */
enum class ProtocolLinkState {
    UNKNOWN,
    NEGOTIATING,
    ACTIVE,
    LOST,
}

/**
 * Layer 0/2: Vehicle ECU semantic responsiveness.
 */
enum class EcuLinkState {
    UNKNOWN,
    RESPONSIVE,
    NO_RESPONSE,
    SESSION_LOST,
}

/**
 * Telemetry sample freshness and pacing state.
 */
enum class TelemetryLinkState {
    INACTIVE,
    ACTIVE,
    STALE,
    UNSUPPORTED,
}

/**
 * High-level holistic truth state combining all orthogonal layer proofs.
 */
enum class ObdSessionTruthState {
    DISCONNECTED,
    CONNECTING,
    SESSION_READY,
    DEGRADED,
    LINK_LOST,
    ERROR,
}

/**
 * Root cause category for connection termination.
 */
enum class DisconnectReason {
    USER_REQUESTED,
    USER_CANCELLED,
    CLASSIC_STREAM_EOF,
    CLASSIC_IO_EXCEPTION,
    BLE_GATT_DISCONNECTED,
    BLE_GATT_ERROR,
    WIFI_EOF,
    WIFI_IO_FAILURE,
    ANDROID_BLUETOOTH_OFF,
    PERMISSION_REVOKED,
    PROCESS_TERMINATED,
    REMOTE_CLOSED,
    IO_FAILURE,
    HANDSHAKE_TIMEOUT,
    SECURITY_DENIED,
    ADAPTER_NOT_FOUND,
    PROTOCOL_EXHAUSTED,
    UNKNOWN,
}

object DisconnectSemantics {
    fun isExpected(reason: DisconnectReason): Boolean =
        reason == DisconnectReason.USER_REQUESTED || reason == DisconnectReason.USER_CANCELLED

    fun countsAsPhysicalLinkLoss(reason: DisconnectReason): Boolean = reason in setOf(
        DisconnectReason.CLASSIC_STREAM_EOF,
        DisconnectReason.CLASSIC_IO_EXCEPTION,
        DisconnectReason.BLE_GATT_DISCONNECTED,
        DisconnectReason.BLE_GATT_ERROR,
        DisconnectReason.WIFI_EOF,
        DisconnectReason.WIFI_IO_FAILURE,
        DisconnectReason.REMOTE_CLOSED,
        DisconnectReason.IO_FAILURE,
        DisconnectReason.ANDROID_BLUETOOTH_OFF,
    )
}

/**
 * Generation-bound connection attempt representation.
 */
data class ConnectionAttempt(
    val attemptId: String,
    val requestedBy: String = "USER",
    val adapterAddress: String,
    val startedAtMonotonicMs: Long = System.nanoTime() / 1_000_000L,
    val generation: Long = 0L,
)

/**
 * Comprehensive, truthful snapshot of the entire OBD connection hierarchy.
 * Replaces the monolithic ObdState.CONNECTED with orthogonal layer proofs.
 */
data class ConnectionTruthSnapshot(
    val attemptId: String? = null,
    val attemptGeneration: Long = 0L,
    val intent: ConnectionIntent = ConnectionIntent.DISCONNECTED,
    val adapterAddress: String? = null,
    val adapterName: String? = null,
    val transportState: TransportLinkState = TransportLinkState.Disconnected,
    val transportLastRxMonotonicMs: Long? = null,
    val transportLastTxMonotonicMs: Long? = null,
    val elmState: ElmLinkState = ElmLinkState.UNKNOWN,
    val elmIdentity: String? = null,
    val elmLastProofMonotonicMs: Long? = null,
    val protocolState: ProtocolLinkState = ProtocolLinkState.UNKNOWN,
    val protocol: ObdProtocol? = null,
    val ecuState: EcuLinkState = EcuLinkState.UNKNOWN,
    val ecuLastProofMonotonicMs: Long? = null,
    val telemetryState: TelemetryLinkState = TelemetryLinkState.INACTIVE,
    val telemetryLastSampleMonotonicMs: Long? = null,
    val sessionState: ObdSessionTruthState = ObdSessionTruthState.DISCONNECTED,
    val disconnectReason: DisconnectReason? = null,
    val lastErrorMessage: String? = null,
    val softRecoveryCount: Int = 0,
    val protocolRecoveryCount: Int = 0,
    val physicalLinkLossCount: Int = 0,
    val lastFailureLayer: FailureLayer? = null,
) {
    val isDemoSession: Boolean
        get() = adapterAddress == "SIMULATOR"

    /**
     * Physical transport is up, ELM is responsive, and ECU has proven semantic response.
     */
    val isSessionReady: Boolean
        get() = transportState is TransportLinkState.Connected &&
                (elmState == ElmLinkState.READY ||
                    (elmState == ElmLinkState.NOT_APPLICABLE && protocol == ObdProtocol.DOIP_ISO13400)) &&
                protocolState == ProtocolLinkState.ACTIVE &&
                ecuState == EcuLinkState.RESPONSIVE

    /**
     * Physical link is alive but ECU is temporarily unresponsive or telemetry paused.
     */
    val isDegraded: Boolean
        get() = transportState is TransportLinkState.Connected &&
                (ecuState == EcuLinkState.NO_RESPONSE || telemetryState == TelemetryLinkState.STALE)

    val isPhysicallyConnected: Boolean
        get() = transportState is TransportLinkState.Connected && !isDemoSession

    val isEcuResponsive: Boolean
        get() = ecuState == EcuLinkState.RESPONSIVE

    val isEcuConnected: Boolean
        get() = isEcuResponsive

    companion object {
        fun initial(): ConnectionTruthSnapshot = ConnectionTruthSnapshot()
    }
}

/**
 * Bounded forensic connection lifecycle event trace.
 * Logs exact monotonic timing of connection phases and failures with privacy redaction.
 */
data class ConnectionTraceEvent(
    val monotonicMs: Long,
    val event: String,
    val details: String? = null,
)

class ConnectionTrace(private val maxCapacity: Int = 200) {
    private val queue = ConcurrentLinkedQueue<ConnectionTraceEvent>()
    private val eventCounter = AtomicLong(0)

    fun log(event: String, details: String? = null) {
        val now = System.nanoTime() / 1_000_000L
        val redactedDetails = details?.let(::redactPii)
        queue.offer(ConnectionTraceEvent(now, event, redactedDetails))
        while (queue.size > maxCapacity) {
            queue.poll()
        }
        eventCounter.incrementAndGet()
    }

    fun getEvents(): List<ConnectionTraceEvent> = queue.toList()

    fun exportRedacted(): List<String> {
        val list = queue.toList()
        if (list.isEmpty()) return emptyList()
        val baseMs = list.first().monotonicMs
        val metrics = listOf(
            "transportReadyMs" to durationBetween(list, "USER_CONNECT_REQUESTED", "TRANSPORT_READY"),
            "elmReadyMs" to durationBetween(list, "USER_CONNECT_REQUESTED", "ELM_READY"),
            "ecuReadyMs" to durationBetween(list, "USER_CONNECT_REQUESTED", "ECU_READY"),
            "sessionReadyMs" to durationBetween(list, "USER_CONNECT_REQUESTED", "SESSION_READY"),
            "cancelToIdleMs" to durationBetween(list, "USER_CANCELLED", "TRANSPORT_DISCONNECTED"),
        ).mapNotNull { (name, value) -> value?.let { "$name=$it" } }
        val events = list.map { e ->
            val deltaMs = e.monotonicMs - baseMs
            if (e.details.isNullOrBlank()) {
                "+${deltaMs}ms ${e.event}"
            } else {
                "+${deltaMs}ms ${e.event} [${e.details}]"
            }
        }
        return if (metrics.isEmpty()) events else listOf("METRICS ${metrics.joinToString(" ")}") + events
    }

    fun clear() {
        queue.clear()
    }

    private fun durationBetween(events: List<ConnectionTraceEvent>, start: String, end: String): Long? {
        val startAt = events.lastOrNull { it.event == start }?.monotonicMs ?: return null
        val endAt = events.firstOrNull { it.event == end && it.monotonicMs >= startAt }?.monotonicMs ?: return null
        return endAt - startAt
    }

    companion object {
        fun redactPii(input: String): String {
            // Redact MAC addresses: AA:BB:CC:DD:EE:FF -> AA:**:**:**:**:FF
            var sanitized = input.replace(
                Regex("([0-9A-Fa-f]{2}):([0-9A-Fa-f]{2}):([0-9A-Fa-f]{2}):([0-9A-Fa-f]{2}):([0-9A-Fa-f]{2}):([0-9A-Fa-f]{2})")
            ) { m ->
                "${m.groupValues[1]}:**:**:**:**:${m.groupValues[6]}"
            }
            // Redact VIN: 17 chars -> 3 chars + * + last 4 chars
            sanitized = sanitized.replace(
                Regex("\\b([A-HJ-NPR-Z0-9]{3})[A-HJ-NPR-Z0-9]{10}([A-HJ-NPR-Z0-9]{4})\\b")
            ) { m ->
                "${m.groupValues[1]}**********${m.groupValues[2]}"
            }
            return sanitized
        }
    }
}
