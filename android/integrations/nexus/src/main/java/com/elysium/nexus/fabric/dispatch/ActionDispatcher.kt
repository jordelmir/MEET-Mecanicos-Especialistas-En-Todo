package com.elysium.nexus.fabric.dispatch

import android.content.Context
import android.util.Log
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.evidence.ControlEvidenceStore
import com.elysium.nexus.fabric.evidence.ControlEvent
import com.elysium.nexus.fabric.evidence.EventResult
import com.elysium.nexus.fabric.error.UxErrorMapper
import com.elysium.nexus.fabric.evidence.FlightBuilder
import com.elysium.nexus.fabric.evidence.FlightRecorder
import com.elysium.nexus.fabric.evidence.TransportResult
import com.elysium.nexus.fabric.resilience.DisconnectNeutralizer
import com.elysium.nexus.fabric.routing.ActionRouteScorer
import com.elysium.nexus.fabric.routing.CircuitBreaker
import com.elysium.nexus.fabric.routing.RouteNegotiator
import com.elysium.nexus.fabric.routing.TransportRoute
import com.elysium.nexus.fabric.session.ControlSession
import com.elysium.nexus.fabric.session.PermissionGate
import com.elysium.nexus.fabric.session.PermissionResult
import com.elysium.nexus.fabric.session.SessionManager
import com.elysium.nexus.fabric.session.SessionState
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.database.IrCatalogRepository
import com.elysium.nexus.fabric.hedging.HedgedExecutor
import com.elysium.nexus.fabric.hedging.HedgedResult
import com.elysium.nexus.fabric.profile.InstalledIrProfileRepository
import java.util.UUID

private const val TAG = "ElysiumNexus.ActionDispatcher"

/**
 * §4.7/§25 Authoritative Action Dispatcher.
 *
 * For IR routes: resolves commands from installed profiles via DeviceCommandResolver.
 * Zero hardcoded NEC bytes. Zero manufactured addresses.
 *
 * Pipeline:
 * 1. Parse intent → UniversalAction
 * 2. Resolve target → DeviceTwin from DKG
 * 3. Negotiate route → RouteNegotiator picks best
 * 4. Check permissions → PermissionGate
 * 5. Open/reuse session → SessionManager
 * 6. Translate & dispatch → DeviceCommandResolver (IR) or adapter (others)
 * 7. Handle disconnect → DisconnectNeutralizer
 * 8. Record evidence → EvidenceStore
 * 9. Fallback on failure → next route
 * 10. Neutralize on terminate → release all inputs
 *
 * V06-P13/14 (wired):
 * - [routeScorer] (when injected) re-ranks negotiated routes dynamically;
 *   routes that score 0.0 (capability mismatch) are dropped.
 * - [circuitBreaker] (when injected) gates every per-protocol attempt;
 *   adaptive open → skip, half-open → probe, success/failure recorded.
 * Both are optional — with neither injected, the dispatcher behaves exactly
 * as before (static priority order, no breaker).
 *
 * V06-P22 (wired):
 * - [flightRecorder] (when injected) records a complete [FlightEntry] per
 *   dispatch: routes evaluated, winning route, command payload, latency
 *   breakdown (resolve/send), result, error and breaker trips — the
 *   diagnostic backbone of every action.
 */
class ActionDispatcher(
    private val routeNegotiator: RouteNegotiator,
    private val sessionManager: SessionManager,
    private val neutralizer: DisconnectNeutralizer,
    private val evidenceStore: ControlEvidenceStore,
    private val twinResolver: (DeviceId) -> DeviceTwin?,
    private val permissionResolver: () -> Set<String>,
    private val maxRetries: Int = 2,
    private val context: Context? = null,
    private val injectedIrResolver: IrCommandResolver? = null,
    private val routeScorer: ActionRouteScorer? = null,
    private val circuitBreaker: CircuitBreaker? = null,
    private val flightRecorder: FlightRecorder? = null,
    // V0.6.2 PR4 Phase 16: zero-trust gate (optional — null disables the check)
    private val trustResolver: ((DeviceId) -> com.elysium.nexus.fabric.identity.DeviceTrustRecord?)? = null,
    private val trustAuditor: ((com.elysium.nexus.fabric.identity.ZeroTrustPolicy.TrustAuditEntry) -> Unit)? = null,
    // V0.6.2 PR4 Phase 18: hedged execution for idempotent actions (§61)
    private val hedgedExecutor: HedgedExecutor? = null
) {
    private val deviceCommandResolver: IrCommandResolver? by lazy {
        injectedIrResolver ?: context?.let { DeviceCommandResolver(it) }
    }

    init {
        require(maxRetries in 0..5) { "maxRetries must be in [0, 5] (got $maxRetries)." }
    }

    suspend fun dispatch(action: UniversalAction): DispatchResult {
        val recorder = flightRecorder
        if (recorder == null) return dispatchCore(action, null)
        val builder = recorder.beginTrace(action, action.targetDeviceId)
        val result = dispatchCore(action, builder)
        finishFlight(builder, result)
        return result
    }

    private suspend fun dispatchCore(
        action: UniversalAction,
        flight: FlightBuilder?
    ): DispatchResult {
        val startNs = System.nanoTime()

        val twin = twinResolver(action.targetDeviceId)
            ?: return recordAndReturn(action, Protocol.Unknown, EventResult.NoRoute,
                DispatchResult.NoTarget(action.targetDeviceId), startNs)

        // V0.6.2 PR4 Phase 16: zero-trust authorization gate (§72)
        val trustRecord = trustResolver?.invoke(action.targetDeviceId)
        if (trustResolver != null) {
            val auditEntry = com.elysium.nexus.fabric.identity.ZeroTrustPolicy.TrustAuditEntry(
                deviceId = action.targetDeviceId,
                action = action::class.simpleName ?: "Unknown",
                requiredTrust = com.elysium.nexus.fabric.identity.ZeroTrustPolicy.requiredTrustState(action),
                actualTrust = trustRecord?.currentState ?: com.elysium.nexus.fabric.identity.TrustState.UNPAIRED,
                authorized = com.elysium.nexus.fabric.identity.ZeroTrustPolicy.isAuthorized(trustRecord, action)
            )
            trustAuditor?.invoke(auditEntry)
            if (!auditEntry.authorized) {
                Log.w(TAG, "ZeroTrust DENIED: ${action::class.simpleName} on ${action.targetDeviceId.value} " +
                    "requires ${auditEntry.requiredTrust}, has ${auditEntry.actualTrust}")
                return recordAndReturn(action, Protocol.Unknown, EventResult.PermissionDenied,
                    DispatchResult.PermissionDenied(action, listOf("Trust ${auditEntry.actualTrust} < required ${auditEntry.requiredTrust}")), startNs)
            }
        }

        val routes = routeNegotiator.negotiate(action, twin)
        if (routes.isEmpty()) {
            return recordAndReturn(action, Protocol.Unknown, EventResult.NoRoute,
                DispatchResult.NoRoute(action, twin), startNs)
        }

        val grantedPermissions = permissionResolver()

        val existingSession = sessionManager.sessionFor(action.targetDeviceId)
        var activeSession: ControlSession = if (existingSession == null || existingSession.isTerminated) {
            sessionManager.createSession(sessionId = UUID.randomUUID().toString(), deviceId = action.targetDeviceId)
        } else {
            existingSession
        }

        var lastError: String? = null
        var availableRoutes = routes.filter { it.isAvailable }
        if (routeScorer != null) {
            // V06-P13: dynamic rerank — zero-scored routes (no capability
            // match) are dropped, the rest keep descending-score order.
            availableRoutes = routeScorer.rank(action, twin, availableRoutes)
                .filter { it.score > 0.0 }
                .map { it.route }
        }
        val routesToTry = availableRoutes.take(maxRetries + 1)
        if (flight != null) {
            flight.routesEvaluated(routesToTry.map { r ->
                com.elysium.nexus.fabric.evidence.CandidateRoute(
                    protocol = r.protocol,
                    score = routeScorer?.score(action, twin, r)
                        ?: (1.0 - r.priority / 100.0).coerceIn(0.0, 1.0),
                    isSelected = r == routesToTry.firstOrNull(),
                    rejectionReason = null
                )
            })
        }

        for ((attemptIndex, route) in routesToTry.withIndex()) {
            // V06-P14: per-protocol circuit breaker gates every attempt.
            val breaker = circuitBreaker
            if (breaker != null && !breaker.allowAttempt(route.protocol)) {
                lastError = "Circuit open for ${route.protocol}"
                recordAttempt(action, route.protocol, EventResult.Fallback, lastError, startNs)
                continue
            }

            val permResult = PermissionGate.check(route.protocol, grantedPermissions)
            if (permResult !is PermissionResult.Granted) {
                val missing = when (permResult) {
                    is PermissionResult.Denied -> permResult.missing
                    is PermissionResult.RationaleRequired -> listOf(permResult.permission)
                    else -> emptyList()
                }
                if (attemptIndex == routesToTry.lastIndex) {
                    return recordAndReturn(action, route.protocol, EventResult.PermissionDenied,
                        DispatchResult.PermissionDenied(action, missing), startNs)
                }
                lastError = "Permission denied for ${route.protocol}: $missing"
                recordAttempt(action, route.protocol, EventResult.Fallback, lastError, startNs)
                continue
            }

            if (!activeSession.isActive) {
                activeSession = activeSession.transitionTo(SessionState.PermissionCheck)
                activeSession = activeSession.withRoute(route)
                activeSession = activeSession.transitionTo(SessionState.Active)
                sessionManager.updateSession(activeSession)
            }

            // §25 IR routes resolve from installed profiles, NOT from hardcoded NEC
            val resolveStartNs = System.nanoTime()
            val deviceState = if (route.protocol == Protocol.DirectIr || route.protocol == Protocol.HubIr) {
                when (val resolution = resolveIrCommand(action, route)) {
                    is CommandResolution.Resolved -> {
                        DeviceState.IrCommand(
                            protocolName = resolution.signalId,
                            address = 0,
                            command = 0,
                            extras = mapOf(
                                "signalId" to resolution.signalId,
                                "codeSetId" to resolution.codeSetId,
                                "profileId" to resolution.profileId,
                                "fingerprint" to resolution.physicalSha256.take(16)
                            ),
                            irSignal = resolution.signal
                        )
                    }
                    is CommandResolution.ProfileMissing -> {
                        lastError = "Profile ${resolution.profileId} missing"
                        null
                    }
                    is CommandResolution.ActionNotInProfile -> {
                        lastError = "Action ${resolution.action} not in profile ${resolution.profileId}"
                        null
                    }
                    is CommandResolution.SignalMissing -> {
                        lastError = "Signal ${resolution.signalId} missing from SQLite"
                        null
                    }
                    is CommandResolution.FingerprintMismatch -> {
                        lastError = "Fingerprint mismatch signalId=${resolution.signalId}"
                        null
                    }
                }
            } else {
                DefaultActionTranslator.translate(action, route)
            }
            flight?.resolveLatencyNs(System.nanoTime() - resolveStartNs)
            if (deviceState is DeviceState.IrCommand) {
                flight?.commandPayload("IR ${deviceState.protocolName} (${deviceState.irSignal}")
            }

            if (deviceState == null) {
                lastError = "Cannot resolve command for ${action::class.simpleName} via ${route.protocol}"
                if (attemptIndex == routesToTry.lastIndex) {
                    return recordAndReturn(action, route.protocol, EventResult.AdapterError,
                        DispatchResult.TranslationFailed(action, route), startNs)
                }
                recordAttempt(action, route.protocol, EventResult.Fallback, lastError, startNs)
                continue
            }

            val sendStartNs = System.nanoTime()

            // V0.6.2 PR4 Phase 18: §61 hedged execution for idempotent actions
            val hedge = hedgedExecutor
            val writeResult: WriteResult = if (hedge != null && routesToTry.size > 1) {
                val backupRoute = routesToTry.firstOrNull { it != route }
                val hedgedResult = hedge.executeWithHedge(
                    action = action,
                    primary = route,
                    backup = backupRoute,
                    executor = { r -> r.adapter.write(action.targetDeviceId, deviceState) }
                )
                when (hedgedResult) {
                    is HedgedResult.PrimarySuccess -> hedgedResult.value ?: WriteResult.Error(ErrorCode.Unknown, "Hedge primary returned null")
                    is HedgedResult.BackupSuccess -> {
                        Log.d(TAG, "Hedge: backup route succeeded for ${action::class.simpleName}")
                        hedgedResult.value ?: WriteResult.Error(ErrorCode.Unknown, "Hedge backup returned null")
                    }
                    is HedgedResult.PrimaryFailed -> {
                        WriteResult.Error(ErrorCode.NetworkError, hedgedResult.reason)
                    }
                    is HedgedResult.BothFailed -> {
                        WriteResult.Error(ErrorCode.NetworkError, hedgedResult.reason)
                    }
                }
            } else {
                route.adapter.write(action.targetDeviceId, deviceState)
            }

            when (writeResult) {
                is WriteResult.Ok -> {
                    flight?.sendLatencyNs(System.nanoTime() - sendStartNs)
                    flight?.winningRoute(route)
                    circuitBreaker?.recordSuccess(route.protocol)
                    neutralizer.trackAction(action)
                    activeSession = activeSession.recordActivity()
                    sessionManager.updateSession(activeSession)
                    val latencyMs = (System.nanoTime() - startNs) / 1_000_000L
                    recordAttempt(action, route.protocol, EventResult.Success, null, startNs)
                    return DispatchResult.Success(action = action, route = route, latencyMs = latencyMs, reportedState = writeResult.reportedState)
                }
                is WriteResult.Error -> {
                    circuitBreaker?.recordFailure(route.protocol)
                    lastError = "Adapter error: ${writeResult.code} - ${writeResult.message}"
                    if (attemptIndex == routesToTry.lastIndex) {
                        return recordAndReturn(action, route.protocol, EventResult.AdapterError,
                            DispatchResult.AdapterFailed(action, route, writeResult), startNs)
                    }
                    recordAttempt(action, route.protocol, EventResult.Fallback, lastError, startNs)
                }
            }
        }

        return recordAndReturn(action, Protocol.Unknown, EventResult.NoRoute,
            DispatchResult.AllRoutesFailed(action, lastError ?: "All routes exhausted"), startNs)
    }

    /**
     * §25 Resolve IR command from installed profile via DeviceCommandResolver.
     * Returns [CommandResolution] — sealed, never null. Fail-closed.
     */
    private suspend fun resolveIrCommand(action: UniversalAction, route: TransportRoute): CommandResolution {
        val resolver = deviceCommandResolver
            ?: return CommandResolution.ProfileMissing("no-resolver-context")
        return resolver.resolve(action.targetDeviceId, action)
    }

    /**
     * V06-P22: finalize the flight trace from a terminal [DispatchResult].
     * Never called when [flightRecorder] is null (the flight is opt-in).
     */
    private fun finishFlight(flight: FlightBuilder, result: DispatchResult) {
        val transportResult: TransportResult
        val error: String?
        when (result) {
            is DispatchResult.Success -> {
                transportResult = TransportResult.Success
                error = null
            }
            is DispatchResult.NoTarget -> {
                transportResult = TransportResult.NoRoute
                error = "No target ${result.deviceId}"
            }
            is DispatchResult.NoRoute -> {
                transportResult = TransportResult.NoRoute
                error = "No route for ${result.target.deviceId}"
            }
            is DispatchResult.PermissionDenied -> {
                transportResult = TransportResult.PermissionDenied
                error = "Missing ${result.missing}"
            }
            is DispatchResult.TranslationFailed -> {
                transportResult = TransportResult.AdapterError
                error = "Translation failed via ${result.route.protocol}"
            }
            is DispatchResult.AdapterFailed -> {
                transportResult = TransportResult.AdapterError
                error = "Adapter ${result.writeError.code}: ${result.writeError.message}"
            }
            is DispatchResult.AllRoutesFailed -> {
                val blocked = result.reason.contains("Circuit open")
                transportResult = if (blocked) TransportResult.CircuitBreakerOpen else TransportResult.Fallback
                error = result.reason
            }
        }
        flight.result(transportResult)
        flight.error(error)
        flight.errorCode(UxErrorMapper.codeFor(result))
        if (transportResult == TransportResult.CircuitBreakerOpen) {
            flight.circuitBreakerTripped(true)
        }
        flight.complete()
    }

    suspend fun terminateSession(deviceId: DeviceId): List<UniversalAction> {
        val neutralActions = neutralizer.neutralize(deviceId)
        for (action in neutralActions) {
            val twin = twinResolver(deviceId) ?: continue
            val routes = routeNegotiator.negotiate(action, twin)
            val route = routes.firstOrNull { it.isAvailable } ?: continue
            val state = if (route.protocol == Protocol.DirectIr || route.protocol == Protocol.HubIr) {
                when (val resolution = resolveIrCommand(action, route)) {
                    is CommandResolution.Resolved -> {
                        DeviceState.IrCommand(
                            protocolName = resolution.signalId,
                            address = 0,
                            command = 0,
                            extras = mapOf("signalId" to resolution.signalId),
                            irSignal = resolution.signal
                        )
                    }
                    else -> null
                }
            } else {
                DefaultActionTranslator.translate(action, route)
            } ?: continue
            route.adapter.write(deviceId, state)
            evidenceStore.record(ControlEvent(
                timestampNs = System.nanoTime(),
                deviceIdHash = ControlEvidenceStore.hashDeviceId(deviceId),
                actionType = action::class.simpleName ?: "Unknown",
                correlationId = action.correlationId,
                protocol = route.protocol,
                result = EventResult.Neutralized
            ))
        }
        sessionManager.terminateSession(deviceId)
        return neutralActions
    }

    suspend fun terminateAll(): Map<DeviceId, List<UniversalAction>> {
        val result = mutableMapOf<DeviceId, List<UniversalAction>>()
        for (session in sessionManager.activeSessions()) {
            val actions = terminateSession(session.deviceId)
            if (actions.isNotEmpty()) result[session.deviceId] = actions
        }
        return result
    }

    private fun recordAttempt(action: UniversalAction, protocol: Protocol, result: EventResult, error: String?, startNs: Long) {
        val latencyMs = (System.nanoTime() - startNs) / 1_000_000L
        evidenceStore.record(ControlEvent(
            timestampNs = System.nanoTime(),
            deviceIdHash = ControlEvidenceStore.hashDeviceId(action.targetDeviceId),
            actionType = action::class.simpleName ?: "Unknown",
            correlationId = action.correlationId,
            protocol = protocol,
            result = result,
            latencyMs = latencyMs,
            errorMessage = error
        ))
    }

    private fun recordAndReturn(action: UniversalAction, protocol: Protocol, result: EventResult, dispatchResult: DispatchResult, startNs: Long): DispatchResult {
        recordAttempt(action, protocol, result, (dispatchResult as? DispatchResult.AdapterFailed)?.writeError?.message, startNs)
        return dispatchResult
    }
}

sealed class DispatchResult {
    data class Success(val action: UniversalAction, val route: TransportRoute, val latencyMs: Long, val reportedState: DeviceState?) : DispatchResult()
    data class NoTarget(val deviceId: DeviceId) : DispatchResult()
    data class NoRoute(val action: UniversalAction, val target: DeviceTwin) : DispatchResult()
    data class PermissionDenied(val action: UniversalAction, val missing: List<String>) : DispatchResult()
    data class TranslationFailed(val action: UniversalAction, val route: TransportRoute) : DispatchResult()
    data class AdapterFailed(val action: UniversalAction, val route: TransportRoute, val writeError: WriteResult.Error) : DispatchResult()
    data class AllRoutesFailed(val action: UniversalAction, val reason: String) : DispatchResult()
}

fun interface ActionTranslator {
    fun translate(action: UniversalAction, route: TransportRoute): DeviceState?
}

/**
 * §25 Default translator for NON-IR routes only.
 * IR routes use DeviceCommandResolver — never this translator.
 */
object DefaultActionTranslator : ActionTranslator {
    override fun translate(action: UniversalAction, route: TransportRoute): DeviceState? {
        return when (action) {
            is UniversalAction.PowerOn -> DeviceState.OnOff(isOn = true)
            is UniversalAction.PowerOff -> DeviceState.OnOff(isOn = false)
            is UniversalAction.PowerToggle -> DeviceState.OnOff(isOn = true)
            is UniversalAction.VolumeUp -> DeviceState.Level(value = 0.6f)
            is UniversalAction.VolumeDown -> DeviceState.Level(value = 0.4f)
            is UniversalAction.SetVolume -> DeviceState.Level(value = action.level)
            is UniversalAction.Mute -> DeviceState.Level(value = 0f)
            is UniversalAction.MediaPlay -> DeviceState.Media(playing = true)
            is UniversalAction.MediaPause -> DeviceState.Media(playing = false)
            is UniversalAction.MediaStop -> DeviceState.Media(playing = false)
            is UniversalAction.MediaNext -> DeviceState.Media(playing = true, track = "next")
            is UniversalAction.MediaPrevious -> DeviceState.Media(playing = true, track = "previous")
            is UniversalAction.SetTemperature -> DeviceState.Climate(targetCelsius = action.targetCelsius, mode = action.mode)
            is UniversalAction.SetMode -> DeviceState.Climate(targetCelsius = 22f, mode = action.mode)
            is UniversalAction.SetFanSpeed -> DeviceState.Level(value = action.level)
            else -> null
        }
    }
}
