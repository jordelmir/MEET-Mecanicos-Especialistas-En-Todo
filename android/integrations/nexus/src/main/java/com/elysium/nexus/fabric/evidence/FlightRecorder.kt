package com.elysium.nexus.fabric.evidence

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.NexusErrorCode
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.routing.TransportRoute
import java.util.UUID

/**
 * §57 Protocol Flight Recorder.
 *
 * Every control action is logged end-to-end:
 *   intent → device → routes evaluated → winning
 *   route → command bytes → transport result →
 *   state observation → latency.
 *
 * The flight recorder is the diagnostic backbone:
 * when something fails, the flight log shows
 * exactly what happened, what was tried, and
 * what the device responded.
 *
 * ## Data model
 *
 * A [FlightEntry] is the complete trace of one
 * action attempt. The entry includes:
 * - The [UniversalAction] that was attempted
 * - The target [DeviceId]
 * - All [CandidateRoute]s that were evaluated
 * - The winning route (if any)
 * - The translated command (protocol-specific)
 * - The [TransportResult]
 * - The observed state after the command
 * - Latency breakdown (resolve, translate, send, confirm)
 *
 * ## Storage
 *
 * The recorder keeps the last [maxEntries] entries
 * in a ring buffer. The buffer is drained to
 * persistent storage periodically (future work).
 * The buffer is also available for the diagnostics
 * screen.
 */
class FlightRecorder(
    private val maxEntries: Int = 500
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive (got $maxEntries)." }
    }

    private val entries = ArrayDeque<FlightEntry>(maxEntries)

    /** Total entries stored. */
    val size: Int get() = entries.size

    /** All entries (newest last). */
    fun all(): List<FlightEntry> = entries.toList()

    /** Clear all entries. */
    fun clear() = entries.clear()

    /**
     * Start a new flight trace. Call this before
     * dispatching the action. Returns a [FlightBuilder]
     * that accumulates trace data.
     */
    fun beginTrace(
        action: UniversalAction,
        targetDeviceId: DeviceId
    ): FlightBuilder = FlightBuilder(this, action, targetDeviceId)

    /**
     * Query entries with optional filters.
     */
    fun query(
        fromNs: Long? = null,
        toNs: Long? = null,
        deviceId: DeviceId? = null,
        actionType: String? = null,
        result: TransportResult? = null,
        protocol: Protocol? = null
    ): List<FlightEntry> = entries.filter { entry ->
        (fromNs == null || entry.startedAtNs >= fromNs) &&
        (toNs == null || entry.completedAtNs <= toNs) &&
        (deviceId == null || entry.targetDeviceId == deviceId) &&
        (actionType == null || entry.actionType == actionType) &&
        (result == null || entry.result == result) &&
        (protocol == null || entry.winningRoute?.protocol == protocol)
    }

    /**
     * Average latency for successful entries.
     */
    fun averageLatencyNs(): Long? {
        val successes = entries.filter { it.result == TransportResult.Success }
        if (successes.isEmpty()) return null
        return (successes.map { it.totalLatencyNs }.average()).toLong()
    }

    /**
     * Failure rate for entries in the last [windowNs] nanoseconds.
     */
    fun recentFailureRate(windowNs: Long = 60_000_000_000L, nowNs: Long = System.nanoTime()): Double {
        val recent = entries.filter { it.startedAtNs >= nowNs - windowNs }
        if (recent.isEmpty()) return 0.0
        val failures = recent.count { it.result != TransportResult.Success }
        return failures.toDouble() / recent.size
    }

    internal fun record(entry: FlightEntry) {
        if (entries.size >= maxEntries) {
            entries.removeFirst()
        }
        entries.addLast(entry)
    }
}

/**
 * Builder for constructing a [FlightEntry] across
 * the dispatch pipeline. The builder is used by
 * the [ActionDispatcher] to record each stage.
 */
class FlightBuilder internal constructor(
    private val recorder: FlightRecorder,
    private val action: UniversalAction,
    private val targetDeviceId: DeviceId
) {
    private val traceId: String = UUID.randomUUID().toString()
    private val startedAtNs: Long = System.nanoTime()
    private var routesEvaluated: List<CandidateRoute> = emptyList()
    private var winningRoute: TransportRoute? = null
    private var commandPayload: String? = null
    private var result: TransportResult = TransportResult.Pending
    private var observedState: DeviceState? = null
    private var resolveLatencyNs: Long = 0L
    private var translateLatencyNs: Long = 0L
    private var sendLatencyNs: Long = 0L
    private var confirmLatencyNs: Long = 0L
    private var errorMessage: String? = null
    private var errorCode: NexusErrorCode? = null
    private var circuitBreakerTripped: Boolean = false

    fun routesEvaluated(routes: List<CandidateRoute>): FlightBuilder {
        this.routesEvaluated = routes
        return this
    }

    fun winningRoute(route: TransportRoute?): FlightBuilder {
        this.winningRoute = route
        return this
    }

    fun commandPayload(payload: String?): FlightBuilder {
        this.commandPayload = payload
        return this
    }

    fun resolveLatencyNs(ns: Long): FlightBuilder {
        this.resolveLatencyNs = ns
        return this
    }

    fun translateLatencyNs(ns: Long): FlightBuilder {
        this.translateLatencyNs = ns
        return this
    }

    fun sendLatencyNs(ns: Long): FlightBuilder {
        this.sendLatencyNs = ns
        return this
    }

    fun confirmLatencyNs(ns: Long): FlightBuilder {
        this.confirmLatencyNs = ns
        return this
    }

    fun result(result: TransportResult): FlightBuilder {
        this.result = result
        return this
    }

    fun observedState(state: DeviceState?): FlightBuilder {
        this.observedState = state
        return this
    }

    fun error(message: String?): FlightBuilder {
        this.errorMessage = message
        return this
    }

    fun circuitBreakerTripped(tripped: Boolean): FlightBuilder {
        this.circuitBreakerTripped = tripped
        return this
    }

    /**
     * V06-P24: typed taxonomy code for telemetry (\u00a780 rule 4).
     */
    fun errorCode(code: NexusErrorCode?): FlightBuilder {
        this.errorCode = code
        return this
    }

    /**
     * Finalize and record the flight entry.
     */
    fun complete(): FlightEntry {
        val entry = FlightEntry(
            traceId = traceId,
            actionType = action::class.simpleName ?: "Unknown",
            correlationId = action.correlationId,
            targetDeviceId = targetDeviceId,
            routesEvaluated = routesEvaluated,
            winningRoute = winningRoute,
            commandPayload = commandPayload,
            result = result,
            observedState = observedState,
            startedAtNs = startedAtNs,
            completedAtNs = System.nanoTime(),
            resolveLatencyNs = resolveLatencyNs,
            translateLatencyNs = translateLatencyNs,
            sendLatencyNs = sendLatencyNs,
            confirmLatencyNs = confirmLatencyNs,
            errorMessage = errorMessage,
            errorCode = errorCode,
            circuitBreakerTripped = circuitBreakerTripped
        )
        recorder.record(entry)
        return entry
    }
}

/**
 * A complete trace of one action attempt.
 */
data class FlightEntry(
    val traceId: String,
    val actionType: String,
    val correlationId: String,
    val targetDeviceId: DeviceId,
    val routesEvaluated: List<CandidateRoute>,
    val winningRoute: TransportRoute?,
    val commandPayload: String?,
    val result: TransportResult,
    val observedState: DeviceState?,
    val startedAtNs: Long,
    val completedAtNs: Long,
    val resolveLatencyNs: Long,
    val translateLatencyNs: Long,
    val sendLatencyNs: Long,
    val confirmLatencyNs: Long,
    val errorMessage: String?,
    val errorCode: NexusErrorCode? = null,
    val circuitBreakerTripped: Boolean
) {
    val totalLatencyNs: Long get() = completedAtNs - startedAtNs
    val totalLatencyMs: Long get() = totalLatencyNs / 1_000_000L
    val isSuccessful: Boolean get() = result == TransportResult.Success
}

/**
 * A route that was evaluated during dispatch.
 */
data class CandidateRoute(
    val protocol: Protocol,
    val score: Double,
    val isSelected: Boolean,
    val rejectionReason: String? = null
)

/**
 * Transport result for the flight recorder.
 */
enum class TransportResult {
    Success,
    AdapterError,
    PermissionDenied,
    NoRoute,
    Timeout,
    Fallback,
    Neutralized,
    CircuitBreakerOpen,
    Pending
}
