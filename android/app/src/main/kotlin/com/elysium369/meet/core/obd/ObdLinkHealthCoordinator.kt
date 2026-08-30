package com.elysium369.meet.core.obd

import android.util.Log
import com.elysium369.meet.core.transport.TransportInterface
import com.elysium369.meet.observability.MeetTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Failure domain taxonomy for layered OBD diagnosis and recovery.
 */
enum class FailureLayer {
    L0_ECU_APPLICATION,
    L1_ADAPTER_ELM,
    L2_VEHICLE_BUS_PROTOCOL,
    L3_PHYSICAL_TRANSPORT,
}

/**
 * Single Authority for OBD link health, orthogonal truth snapshot, and layered recovery.
 *
 * Enforces the non-negotiable architectural law:
 * - NO USER INTENT -> NO PHYSICAL CONNECTION
 * - NO PHYSICAL PROOF -> NO "SCANNER CONNECTED"
 * - NO ECU RESPONSE -> NO "ECU CONNECTED"
 * - TELEMETRY SILENCE != PHYSICAL DISCONNECT
 * - ECU FAILURE != BLUETOOTH FAILURE
 * - Recovery only operates on the lowest failing layer; L0-L2 NEVER close physical sockets.
 */
class ObdLinkHealthCoordinator(
    private val trace: ConnectionTrace = ConnectionTrace()
) {
    private val TAG = "EV_HEALTH_COORD"

    private val _truth = MutableStateFlow(ConnectionTruthSnapshot.initial())
    val truth: StateFlow<ConnectionTruthSnapshot> = _truth.asStateFlow()

    private val isRecovering = AtomicBoolean(false)

    fun getTrace(): ConnectionTrace = trace

    /**
     * Called when the user initiates a connection transaction.
     */
    fun onUserConnectRequested(attempt: ConnectionAttempt) {
        _truth.update { current ->
            current.copy(
                attemptId = attempt.attemptId,
                attemptGeneration = attempt.generation,
                intent = ConnectionIntent.CONNECT_REQUESTED,
                adapterAddress = attempt.adapterAddress,
                sessionState = ObdSessionTruthState.CONNECTING,
                transportState = TransportLinkState.Connecting,
                transportLastRxMonotonicMs = null,
                transportLastTxMonotonicMs = null,
                elmState = ElmLinkState.UNKNOWN,
                elmIdentity = null,
                elmLastProofMonotonicMs = null,
                protocolState = ProtocolLinkState.UNKNOWN,
                protocol = null,
                ecuState = EcuLinkState.UNKNOWN,
                ecuLastProofMonotonicMs = null,
                telemetryState = TelemetryLinkState.INACTIVE,
                telemetryLastSampleMonotonicMs = null,
                disconnectReason = null,
                lastErrorMessage = null,
                lastFailureLayer = null,
            )
        }
        trace.log("USER_CONNECT_REQUESTED", "attempt=${attempt.attemptId} target=${attempt.adapterAddress}")
        MeetTelemetry.event(
            name = "obd.connection_attempt",
            attributes = mapOf("operation" to "USER_CONNECT_REQUESTED"),
            traceId = attempt.attemptId,
            correlationId = attempt.attemptId,
        )
    }

    fun isCurrentAttempt(attemptId: String, generation: Long): Boolean {
        val current = _truth.value
        return current.intent == ConnectionIntent.CONNECT_REQUESTED &&
            current.attemptId == attemptId &&
            current.attemptGeneration == generation
    }

    /**
     * Called when the user cancels or explicitly disconnects.
     */
    fun onUserCancelled(reason: String = "User cancelled") {
        _truth.update { current ->
            current.copy(
                intent = ConnectionIntent.DISCONNECTED,
                sessionState = ObdSessionTruthState.DISCONNECTED,
                transportState = TransportLinkState.Disconnected,
                elmState = ElmLinkState.UNKNOWN,
                protocolState = ProtocolLinkState.UNKNOWN,
                ecuState = EcuLinkState.UNKNOWN,
                telemetryState = TelemetryLinkState.INACTIVE,
                disconnectReason = DisconnectReason.USER_CANCELLED
            )
        }
        trace.log("USER_CANCELLED", reason)
        telemetryDisconnect(DisconnectReason.USER_CANCELLED)
    }

    fun onUserDisconnected() {
        _truth.update { current ->
            current.copy(
                intent = ConnectionIntent.DISCONNECTED,
                sessionState = ObdSessionTruthState.DISCONNECTED,
                transportState = TransportLinkState.Disconnected,
                elmState = ElmLinkState.UNKNOWN,
                protocolState = ProtocolLinkState.UNKNOWN,
                ecuState = EcuLinkState.UNKNOWN,
                telemetryState = TelemetryLinkState.INACTIVE,
                disconnectReason = DisconnectReason.USER_REQUESTED
            )
        }
        trace.log("USER_DISCONNECTED")
        trace.log("SESSION_ENDED")
        telemetryDisconnect(DisconnectReason.USER_REQUESTED)
    }

    fun onConnectionAttemptFailed(reason: String, disconnectReason: DisconnectReason = DisconnectReason.HANDSHAKE_TIMEOUT) {
        _truth.update {
            it.copy(
                intent = ConnectionIntent.DISCONNECTED,
                sessionState = ObdSessionTruthState.ERROR,
                transportState = TransportLinkState.Disconnected,
                elmState = ElmLinkState.UNKNOWN,
                protocolState = ProtocolLinkState.UNKNOWN,
                protocol = null,
                ecuState = EcuLinkState.UNKNOWN,
                telemetryState = TelemetryLinkState.INACTIVE,
                disconnectReason = disconnectReason,
                lastErrorMessage = reason,
            )
        }
        trace.log("CONNECTION_ATTEMPT_FAILED", reason)
        telemetryDisconnect(disconnectReason)
    }

    /**
     * Direct observation of physical transport state.
     */
    fun onTransportStateChanged(state: TransportLinkState) {
        _truth.update { current ->
            val updatedSession = when (state) {
                is TransportLinkState.Connected -> if (current.isSessionReady) ObdSessionTruthState.SESSION_READY else ObdSessionTruthState.CONNECTING
                is TransportLinkState.RemoteClosed -> ObdSessionTruthState.LINK_LOST
                is TransportLinkState.IoFailure -> ObdSessionTruthState.LINK_LOST
                is TransportLinkState.Disconnected -> ObdSessionTruthState.DISCONNECTED
                is TransportLinkState.Connecting -> ObdSessionTruthState.CONNECTING
                is TransportLinkState.Closing -> ObdSessionTruthState.DISCONNECTED
            }
            val linkLossInc = if (state is TransportLinkState.RemoteClosed || state is TransportLinkState.IoFailure) 1 else 0
            val linkLost = state is TransportLinkState.RemoteClosed || state is TransportLinkState.IoFailure
            current.copy(
                intent = if (linkLost) ConnectionIntent.DISCONNECTED else current.intent,
                transportState = state,
                sessionState = updatedSession,
                physicalLinkLossCount = current.physicalLinkLossCount + linkLossInc,
                elmState = if (linkLost || state is TransportLinkState.Disconnected) ElmLinkState.UNKNOWN else current.elmState,
                protocolState = if (linkLost || state is TransportLinkState.Disconnected) ProtocolLinkState.UNKNOWN else current.protocolState,
                ecuState = if (linkLost) EcuLinkState.SESSION_LOST else if (state is TransportLinkState.Disconnected) EcuLinkState.UNKNOWN else current.ecuState,
                telemetryState = if (linkLost || state is TransportLinkState.Disconnected) TelemetryLinkState.INACTIVE else current.telemetryState,
                disconnectReason = when (state) {
                    is TransportLinkState.RemoteClosed -> DisconnectReason.REMOTE_CLOSED
                    is TransportLinkState.IoFailure -> DisconnectReason.IO_FAILURE
                    else -> current.disconnectReason
                },
                lastFailureLayer = if (linkLost) FailureLayer.L3_PHYSICAL_TRANSPORT else current.lastFailureLayer,
                lastErrorMessage = when (state) {
                    is TransportLinkState.RemoteClosed -> state.reason
                    is TransportLinkState.IoFailure -> state.cause.message
                    else -> current.lastErrorMessage
                }
            )
        }
        when (state) {
            is TransportLinkState.Connected -> trace.log("TRANSPORT_READY")
            is TransportLinkState.RemoteClosed -> trace.log(
                "TRANSPORT_REMOTE_CLOSED",
                "${state.reason.orEmpty()} detectionMs=${(System.nanoTime() / 1_000_000L - state.timestampMonotonicMs).coerceAtLeast(0L)}"
            )
            is TransportLinkState.IoFailure -> trace.log("TRANSPORT_IO_FAILURE", state.cause.message)
            is TransportLinkState.Disconnected -> trace.log("TRANSPORT_DISCONNECTED")
            is TransportLinkState.Connecting -> trace.log("TRANSPORT_CONNECTING")
            is TransportLinkState.Closing -> trace.log("TRANSPORT_CLOSING")
        }
        when (state) {
            is TransportLinkState.RemoteClosed -> telemetryDisconnect(DisconnectReason.REMOTE_CLOSED)
            is TransportLinkState.IoFailure -> telemetryDisconnect(DisconnectReason.IO_FAILURE)
            else -> Unit
        }
    }

    private fun telemetryDisconnect(reason: DisconnectReason) {
        val attemptId = _truth.value.attemptId ?: return
        MeetTelemetry.event(
            name = "obd.disconnect",
            attributes = mapOf(
                "disconnectReason" to reason.name,
                "expected" to DisconnectSemantics.isExpected(reason),
            ),
            traceId = attemptId,
            correlationId = attemptId,
        )
    }

    fun onPhysicalRxProof(bytesCount: Int) {
        val now = System.nanoTime() / 1_000_000L
        _truth.update { it.copy(transportLastRxMonotonicMs = now) }
    }

    fun onPhysicalTxProof(bytesCount: Int) {
        val now = System.nanoTime() / 1_000_000L
        _truth.update { it.copy(transportLastTxMonotonicMs = now) }
    }

    fun onElmSyncStarted() {
        _truth.update { it.copy(elmState = ElmLinkState.SYNCING) }
        trace.log("ELM_SYNC_STARTED")
    }

    fun onElmReady(identity: String) {
        val now = System.nanoTime() / 1_000_000L
        _truth.update {
            it.copy(
                elmState = ElmLinkState.READY,
                elmIdentity = identity,
                elmLastProofMonotonicMs = now
            )
        }
        trace.log("ELM_READY", identity)
    }

    fun onElmNotApplicable(identity: String) {
        val now = System.nanoTime() / 1_000_000L
        _truth.update {
            it.copy(
                elmState = ElmLinkState.NOT_APPLICABLE,
                elmIdentity = identity,
                elmLastProofMonotonicMs = now,
            )
        }
        trace.log("ELM_NOT_APPLICABLE", identity)
    }

    fun onProtocolNegotiating() {
        _truth.update { it.copy(protocolState = ProtocolLinkState.NEGOTIATING) }
        trace.log("PROTOCOL_NEGOTIATING")
    }

    fun onNegotiationEvidence(evidence: ElmNegotiator.NegotiationEvidence) {
        trace.log(evidence.type.name, evidence.redactedDetail().ifBlank { null })
        when (evidence.type) {
            ElmNegotiator.EvidenceType.ELM_BANNER_RECEIVED -> {
                val now = System.nanoTime() / 1_000_000L
                _truth.update {
                    it.copy(
                        elmState = ElmLinkState.READY,
                        elmIdentity = "ELM327-compatible",
                        elmLastProofMonotonicMs = now,
                    )
                }
                trace.log("ELM_READY", "banner_verified")
            }
            ElmNegotiator.EvidenceType.FIRST_VALID_ECU_FRAME -> onEcuHandshake()
            else -> Unit
        }
    }

    fun onMotionObservedDuringNegotiation() {
        val current = _truth.value
        if (current.sessionState == ObdSessionTruthState.CONNECTING || current.protocolState == ProtocolLinkState.NEGOTIATING) {
            val alreadyRecorded = trace.getEvents().any { it.event == "MOTION_TEMPORALLY_CORRELATED" }
            if (!alreadyRecorded) trace.log("MOTION_TEMPORALLY_CORRELATED", "source=PHONE_FUSED_SPEED")
        }
    }

    fun onProtocolReady(protocol: ObdProtocol) {
        _truth.update {
            it.copy(
                protocolState = ProtocolLinkState.ACTIVE,
                protocol = protocol
            )
        }
        trace.log("PROTOCOL_READY", protocol.displayName)
    }

    fun onEcuHandshake() {
        trace.log("ECU_HANDSHAKE")
    }

    fun onEcuReady() {
        val now = System.nanoTime() / 1_000_000L
        _truth.update {
            it.copy(
                ecuState = EcuLinkState.RESPONSIVE,
                ecuLastProofMonotonicMs = now,
                sessionState = ObdSessionTruthState.SESSION_READY
            )
        }
        trace.log("ECU_READY")
        trace.log("SESSION_READY")
    }

    fun onEcuSilent(reason: String = "timeout") {
        _truth.update {
            it.copy(
                ecuState = EcuLinkState.NO_RESPONSE,
                sessionState = if (it.isPhysicallyConnected) ObdSessionTruthState.DEGRADED else it.sessionState
            )
        }
        trace.log("ECU_RESPONSE_STALE", reason)
    }

    fun onTelemetrySampleReceived() {
        val now = System.nanoTime() / 1_000_000L
        _truth.update {
            val refreshed = it.copy(
                telemetryState = TelemetryLinkState.ACTIVE,
                telemetryLastSampleMonotonicMs = now,
                ecuState = EcuLinkState.RESPONSIVE,
                ecuLastProofMonotonicMs = now
            )
            refreshed.copy(
                sessionState = if (refreshed.isSessionReady) {
                    ObdSessionTruthState.SESSION_READY
                } else {
                    refreshed.sessionState
                }
            )
        }
    }

    fun onTelemetryStale() {
        _truth.update {
            it.copy(
                telemetryState = TelemetryLinkState.STALE,
                ecuState = if (it.isPhysicallyConnected) EcuLinkState.NO_RESPONSE else it.ecuState,
                sessionState = if (it.isPhysicallyConnected) ObdSessionTruthState.DEGRADED else it.sessionState
            )
        }
    }

    /**
     * Executes layered recovery based on the reported failure.
     * Guaranteed never to open or close physical transports for L0-L2 failures.
     */
    suspend fun executeLayeredRecovery(
        layer: FailureLayer,
        reason: String,
        transport: TransportInterface,
        onElmResync: suspend () -> Boolean,
        onProtocolRestore: suspend () -> Boolean
    ): Boolean {
        if (_truth.value.intent != ConnectionIntent.CONNECT_REQUESTED) {
            Log.w(TAG, "Layered recovery skipped: ConnectionIntent is not CONNECT_REQUESTED")
            return false
        }
        if (!isRecovering.compareAndSet(false, true)) return false
        try {
            when (layer) {
                FailureLayer.L0_ECU_APPLICATION -> {
                    // L0: ECU negative response / unsupported PID / NO DATA
                    // DO NOT TOUCH TRANSPORT. Record observation and proceed.
                    Log.d(TAG, "L0 observation logged: $reason — no physical action taken")
                    return true
                }
                FailureLayer.L1_ADAPTER_ELM -> {
                    // L1: Buffer full / prompt lost / unexpected ELM output
                    // Perform soft prompt drain and CR sync over the live socket.
                    trace.log("SOFT_RECOVERY", reason)
                    _truth.update {
                        it.copy(
                            softRecoveryCount = it.softRecoveryCount + 1,
                            lastFailureLayer = FailureLayer.L1_ADAPTER_ELM,
                        )
                    }
                    Log.i(TAG, "Executing L1 soft ELM resync over live transport...")
                    val success = onElmResync()
                    if (success) {
                        trace.log("ELM_PROMPT_OK")
                    }
                    return success
                }
                FailureLayer.L2_VEHICLE_BUS_PROTOCOL -> {
                    // L2: CAN/BUS error / ECU silence while socket is alive
                    // Preserve socket. Re-establish protocol and header over the existing link.
                    trace.log("PROTOCOL_RECOVERY", reason)
                    _truth.update {
                        it.copy(
                            protocolRecoveryCount = it.protocolRecoveryCount + 1,
                            lastFailureLayer = FailureLayer.L2_VEHICLE_BUS_PROTOCOL,
                            ecuState = EcuLinkState.NO_RESPONSE,
                            sessionState = ObdSessionTruthState.DEGRADED
                        )
                    }
                    Log.i(TAG, "Executing L2 protocol recovery over live physical transport...")
                    val success = onProtocolRestore()
                    if (success) {
                        val now = System.nanoTime() / 1_000_000L
                        _truth.update {
                            it.copy(
                                ecuState = EcuLinkState.RESPONSIVE,
                                ecuLastProofMonotonicMs = now,
                                sessionState = ObdSessionTruthState.SESSION_READY
                            )
                        }
                        trace.log("ECU_RECOVERED")
                    }
                    return success
                }
                FailureLayer.L3_PHYSICAL_TRANSPORT -> {
                    // L3: True physical socket EOF, broken pipe, or GATT disconnect
                    // Mark LINK_LOST. DO NOT AUTO-RECONNECT physically.
                    trace.log("TRANSPORT_REMOTE_CLOSED", reason)
                    _truth.update {
                        it.copy(
                            intent = ConnectionIntent.DISCONNECTED,
                            transportState = TransportLinkState.RemoteClosed(reason),
                            sessionState = ObdSessionTruthState.LINK_LOST,
                            elmState = ElmLinkState.UNKNOWN,
                            protocolState = ProtocolLinkState.UNKNOWN,
                            protocol = null,
                            ecuState = EcuLinkState.SESSION_LOST,
                            telemetryState = TelemetryLinkState.INACTIVE,
                            disconnectReason = DisconnectReason.REMOTE_CLOSED,
                            lastFailureLayer = FailureLayer.L3_PHYSICAL_TRANSPORT
                        )
                    }
                    return false
                }
            }
        } finally {
            isRecovering.set(false)
        }
    }
}
