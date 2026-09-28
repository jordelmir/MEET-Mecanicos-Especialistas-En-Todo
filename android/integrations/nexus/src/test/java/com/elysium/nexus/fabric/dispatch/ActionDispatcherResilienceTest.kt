package com.elysium.nexus.fabric.dispatch

import com.elysium.nexus.fabric.adapter.AdapterResult
import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.ReadResult
import com.elysium.nexus.fabric.adapter.ScanResult
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.ConnectivityState
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.DeviceType
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.ProtocolBinding
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.evidence.ControlEvidenceStore
import com.elysium.nexus.fabric.evidence.EventResult
import com.elysium.nexus.fabric.resilience.DisconnectNeutralizer
import com.elysium.nexus.fabric.routing.ActionRouteScorer
import com.elysium.nexus.fabric.routing.CircuitBreaker
import com.elysium.nexus.fabric.routing.RouteNegotiator
import com.elysium.nexus.fabric.session.PermissionGate
import com.elysium.nexus.fabric.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-P13/P14: scorer + circuit breaker wired into the dispatcher.
 */
class ActionDispatcherResilienceTest {

    private val deviceId = DeviceId("tv-resilience-001")

    private fun adapter(protocol: Protocol, shouldSucceed: Boolean = true): DeviceAdapter =
        object : DeviceAdapter {
            override val protocol: Protocol = protocol
            override val label: String = protocol.name
            override val supportedCapabilities: Set<Capability> =
                setOf(Capability.OnOff, Capability.Volume)
            private val _state = MutableStateFlow(AdapterState.Active)
            override val state: StateFlow<AdapterState> = _state
            private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
            override val devices: StateFlow<List<DeviceTwin>> = _devices
            override suspend fun start(): AdapterResult = AdapterResult.Ok
            override suspend fun scan(timeoutMs: Long): ScanResult = ScanResult.Ok(0)
            override suspend fun read(deviceId: DeviceId): ReadResult =
                ReadResult.Error(ErrorCode.UnsupportedOperation, "fake")
            override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult =
                if (shouldSucceed) WriteResult.Ok(reportedState = state)
                else WriteResult.Error(ErrorCode.NetworkError, "Failed transmission")
            override suspend fun subscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
            override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
            override suspend fun stop(): AdapterResult = AdapterResult.Ok
        }

    /** IR routes resolve through the resolver — absent it, dispatch fails closed. */
    private fun fakeIrResolver(): IrCommandResolver = IrCommandResolver { _, action ->
        val irAction = when (action) {
            is UniversalAction.PowerOn -> com.elysium.nexus.core.device.IrAction.POWER_ON
            is UniversalAction.PowerToggle -> com.elysium.nexus.core.device.IrAction.POWER_TOGGLE
            is UniversalAction.Mute -> com.elysium.nexus.core.device.IrAction.MUTE
            else -> null
        } ?: return@IrCommandResolver CommandResolution.ActionNotInProfile(deviceId.value, com.elysium.nexus.core.device.IrAction.POWER_TOGGLE)
        CommandResolution.Resolved(
            profileId = deviceId.value,
            codeSetId = "fake-code-set",
            action = irAction,
            signalId = "fake-signal-$irAction",
            physicalSha256 = "fake-fingerprint",
            signal = com.elysium.nexus.core.device.IrSignal.Encoded(
                carrierHz = 38000,
                protocol = com.elysium.nexus.fabric.infrared.IrProtocol.Nec,
                address = 0x07,
                command = irAction.ordinal
            )
        )
    }

    private fun twin(vararg bindings: ProtocolBinding): DeviceTwin = DeviceTwin(
        deviceId = deviceId,
        deviceType = DeviceType.Television,
        capabilities = setOf(Capability.OnOff, Capability.Volume),
        connectivity = ConnectivityState.Online,
        label = "Resilience Test TV",
        protocolBindings = bindings.toSet()
    )

    /** A twin with OnOff+Volume bindings on WiFi and IR (both available). */
    private fun trueBinding(): DeviceTwin = twin(
        ProtocolBinding(Protocol.WiFi, "wifi-1", setOf(Capability.OnOff, Capability.Volume)),
        ProtocolBinding(Protocol.DirectIr, "ir-1", setOf(Capability.OnOff, Capability.Volume))
    )

    private fun dispatcher(
        adapters: List<DeviceAdapter>,
        scorer: ActionRouteScorer? = null,
        breaker: CircuitBreaker? = null,
        maxRetries: Int = 2
    ): ActionDispatcher = ActionDispatcher(
        routeNegotiator = RouteNegotiator(adapters),
        sessionManager = SessionManager(),
        neutralizer = DisconnectNeutralizer(),
        evidenceStore = ControlEvidenceStore(),
        twinResolver = { trueBinding() },
        permissionResolver = {
                setOf(
                    PermissionGate.TRANSMIT_IR,
                    PermissionGate.INTERNET,
                    PermissionGate.ACCESS_FINE_LOCATION,
                    PermissionGate.BLUETOOTH_CONNECT,
                    PermissionGate.BLUETOOTH_SCAN
                )
            },
        maxRetries = maxRetries,
        injectedIrResolver = fakeIrResolver(),
        routeScorer = scorer,
        circuitBreaker = breaker
    )

    // ═══ V06-P14: circuit breaker gating ═════════════════════════════════

    @Test
    fun `open circuit blocks route and dispatch falls through`() = runBlocking {
        val irAdapter = adapter(Protocol.DirectIr, shouldSucceed = false)
        val breaker = CircuitBreaker(failureThreshold = 2, cooldownMs = 600_000)

        // Two failures to open the circuit
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)
        assertFalse("circuit must be open", breaker.allowAttempt(Protocol.DirectIr))

        val dispatcher = dispatcher(listOf(irAdapter), breaker = breaker)
        val result = dispatcher.dispatch(UniversalAction.PowerOn(deviceId))

        assertTrue("blocked single route must not succeed", result is DispatchResult.AllRoutesFailed)
        val failed = result as DispatchResult.AllRoutesFailed
        assertTrue("reason must name the circuit", failed.reason.contains("Circuit open"))
    }

    @Test
    fun `breaker records success and resets the circuit`() = runBlocking {
        val irAdapter = adapter(Protocol.DirectIr, shouldSucceed = true)
        val breaker = CircuitBreaker(failureThreshold = 5, cooldownMs = 600_000)
        breaker.recordFailure(Protocol.DirectIr) // 1 failure

        val dispatcher = dispatcher(listOf(irAdapter), breaker = breaker)
        val result = dispatcher.dispatch(UniversalAction.Mute(deviceId))

        assertTrue("successful dispatch must succeed", result is DispatchResult.Success)
        val state = breaker.stateFor(Protocol.DirectIr)
        assertEquals("success must reset consecutive failures", 0, state.consecutiveFailures)
    }

    @Test
    fun `breaker opens through dispatcher failures and then blocks`() = runBlocking {
        val wifiFailing = adapter(Protocol.WiFi, shouldSucceed = false)
        val irFailing = adapter(Protocol.DirectIr, shouldSucceed = false)
        val breaker = CircuitBreaker(failureThreshold = 2, cooldownMs = 600_000)

        val dispatcher = dispatcher(listOf(wifiFailing, irFailing), breaker = breaker, maxRetries = 2)

        // One dispatch = one failing attempt per protocol → 1 failure each.
        dispatcher.dispatch(UniversalAction.PowerOn(deviceId))
        assertEquals("IR failures after 1st dispatch", 1, breaker.stateFor(Protocol.DirectIr).consecutiveFailures)
        assertEquals("WiFi failures after 1st dispatch", 1, breaker.stateFor(Protocol.WiFi).consecutiveFailures)

        // Second dispatch: IR fails again → threshold 2 → circuit opens.
        // WiFi is then skipped on the third dispatch; no route is attempted.
        dispatcher.dispatch(UniversalAction.PowerOn(deviceId))
        assertEquals("IR failures after 2nd dispatch", 2, breaker.stateFor(Protocol.DirectIr).consecutiveFailures)

        val third = dispatcher.dispatch(UniversalAction.PowerOn(deviceId))
        assertTrue("third was $third", third is DispatchResult.AllRoutesFailed)
        assertTrue((third as DispatchResult.AllRoutesFailed).reason.contains("Circuit open"))
    }

    // ═══ V06-P13: dynamic route scoring ══════════════════════════════════

    @Test
    fun `scorer reranking - higher-scored route is tried before static priority`() = runBlocking {
        val wifiAdapter = adapter(Protocol.WiFi, shouldSucceed = false)
        val irAdapter = adapter(Protocol.DirectIr, shouldSucceed = true)
        val negotiator = RouteNegotiator(listOf(wifiAdapter, irAdapter))
        val evidenceStore = ControlEvidenceStore()

        // Static priority would try DirectIr (10) before WiFi (65), and IR
        // succeeds immediately. With the scorer injected, WiFi scores higher
        // (state-confirmation bonus), so WiFi is tried first, fails, and the
        // dispatcher falls through to IR.
        val dispatcher = ActionDispatcher(
            routeNegotiator = negotiator,
            sessionManager = SessionManager(),
            neutralizer = DisconnectNeutralizer(),
            evidenceStore = evidenceStore,
            twinResolver = { trueBinding() },
            permissionResolver = {
                setOf(
                    PermissionGate.TRANSMIT_IR,
                    PermissionGate.INTERNET,
                    PermissionGate.ACCESS_FINE_LOCATION,
                    PermissionGate.BLUETOOTH_CONNECT,
                    PermissionGate.BLUETOOTH_SCAN
                )
            },
            maxRetries = 2,
            injectedIrResolver = fakeIrResolver(),
            routeScorer = ActionRouteScorer(evidenceStore),
            circuitBreaker = null
        )

        val result = dispatcher.dispatch(UniversalAction.PowerOn(deviceId))

        assertTrue("fallback must land on IR (got $result)", result is DispatchResult.Success)
        assertEquals(Protocol.DirectIr, (result as DispatchResult.Success).route.protocol)

        // Evidence proves WiFi was attempted first (Fallback) before the IR
        // success — the scorer order, not the static priority order.
        val wifiEvents = evidenceStore.query(protocol = Protocol.WiFi)
        assertTrue("WiFi failure must be recorded", wifiEvents.any { it.result == EventResult.Fallback })
    }

    @Test
    fun `capability-mismatched route is dropped before scoring - NoRoute not AdapterError`() = runBlocking {
        val irAdapter = adapter(Protocol.DirectIr, shouldSucceed = true)
        val negotiator = RouteNegotiator(listOf(irAdapter))
        val evidenceStore = ControlEvidenceStore()

        val twin = DeviceTwin(
            deviceId = deviceId,
            deviceType = DeviceType.Television,
            capabilities = setOf(Capability.OnOff),
            connectivity = ConnectivityState.Online,
            label = "Scorer TV",
            protocolBindings = setOf(
                ProtocolBinding(Protocol.DirectIr, "ir-1", setOf(Capability.Volume))
            )
        )

        val dispatcher = ActionDispatcher(
            routeNegotiator = negotiator,
            sessionManager = SessionManager(),
            neutralizer = DisconnectNeutralizer(),
            evidenceStore = evidenceStore,
            twinResolver = { twin },
            permissionResolver = {
                setOf(
                    PermissionGate.TRANSMIT_IR,
                    PermissionGate.INTERNET,
                    PermissionGate.ACCESS_FINE_LOCATION,
                    PermissionGate.BLUETOOTH_CONNECT,
                    PermissionGate.BLUETOOTH_SCAN
                )
            },
            maxRetries = 2,
            injectedIrResolver = fakeIrResolver(),
            routeScorer = ActionRouteScorer(evidenceStore),
            circuitBreaker = null
        )

        // PowerOn requires OnOff capability; the only binding has Volume.
        // The route never reaches the adapter or the IR resolver — the
        // negotiator drops it, and the dispatcher's score>0 filter is a
        // second, redundant guard (defense in depth).
        val result = dispatcher.dispatch(UniversalAction.PowerOn(deviceId))
        assertTrue("got $result", result is DispatchResult.NoRoute)
        assertEquals(0, evidenceStore.query(protocol = Protocol.DirectIr).size)
    }
}