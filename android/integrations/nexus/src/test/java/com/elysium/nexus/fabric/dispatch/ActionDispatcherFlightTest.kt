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
import com.elysium.nexus.fabric.evidence.FlightRecorder
import com.elysium.nexus.fabric.evidence.TransportResult
import com.elysium.nexus.fabric.resilience.DisconnectNeutralizer
import com.elysium.nexus.fabric.routing.CircuitBreaker
import com.elysium.nexus.fabric.routing.RouteNegotiator
import com.elysium.nexus.fabric.session.PermissionGate
import com.elysium.nexus.fabric.session.SessionManager
import com.elysium.nexus.fabric.evidence.ControlEvidenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V06-P22: FlightRecorder wired into the dispatcher — every dispatch
 * produces a complete end-to-end flight entry.
 */
class ActionDispatcherFlightTest {

    private val deviceId = DeviceId("tv-flight-001")

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

    private val twin = DeviceTwin(
        deviceId = deviceId,
        deviceType = DeviceType.Television,
        capabilities = setOf(Capability.OnOff, Capability.Volume),
        connectivity = ConnectivityState.Online,
        label = "Flight Test TV",
        protocolBindings = setOf(
            ProtocolBinding(Protocol.DirectIr, "ir-1", setOf(Capability.OnOff, Capability.Volume)),
            ProtocolBinding(Protocol.HidOverBle, "ble-1", setOf(Capability.OnOff, Capability.Volume))
        )
    )

    private fun dispatcher(
        recorder: FlightRecorder,
        adapters: List<DeviceAdapter>,
        breaker: CircuitBreaker? = null,
        permissionResolver: () -> Set<String> = {
            setOf(PermissionGate.TRANSMIT_IR, PermissionGate.BLUETOOTH_CONNECT, PermissionGate.BLUETOOTH_SCAN)
        }
    ): ActionDispatcher = ActionDispatcher(
        routeNegotiator = RouteNegotiator(adapters),
        sessionManager = SessionManager(),
        neutralizer = DisconnectNeutralizer(),
        evidenceStore = ControlEvidenceStore(),
        twinResolver = { twin },
        permissionResolver = permissionResolver,
        maxRetries = 2,
        injectedIrResolver = fakeIrResolver(),
        circuitBreaker = breaker,
        flightRecorder = recorder
    )

    private fun fakeIrResolver(): IrCommandResolver = IrCommandResolver { _, action ->
        val irAction = when (action) {
            is UniversalAction.PowerOn -> com.elysium.nexus.core.device.IrAction.POWER_ON
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

    @Test
    fun `successful dispatch records a complete flight entry`() = runBlocking {
        val recorder = FlightRecorder()
        val dispatcher = dispatcher(recorder, listOf(adapter(Protocol.DirectIr)))

        val result = dispatcher.dispatch(UniversalAction.PowerOn(deviceId))

        assertTrue(result is DispatchResult.Success)
        assertEquals(1, recorder.size)
        val entry = recorder.all().first()
        assertEquals(TransportResult.Success, entry.result)
        assertNotNull(entry.winningRoute)
        assertEquals(Protocol.DirectIr, entry.winningRoute!!.protocol)
        assertTrue("send latency must be measured", entry.sendLatencyNs > 0)
        assertTrue("routes must be evaluated", entry.routesEvaluated.isNotEmpty())
        assertFalse(entry.circuitBreakerTripped)
    }

    @Test
    fun `no target records NoRoute flight entry`() = runBlocking {
        val recorder = FlightRecorder()
        val dispatcher = ActionDispatcher(
            routeNegotiator = RouteNegotiator(emptyList()),
            sessionManager = SessionManager(),
            neutralizer = DisconnectNeutralizer(),
            evidenceStore = ControlEvidenceStore(),
            twinResolver = { null },
            permissionResolver = { emptySet() },
            flightRecorder = recorder
        )

        val result = dispatcher.dispatch(UniversalAction.PowerOn(deviceId))

        assertTrue(result is DispatchResult.NoTarget)
        val entry = recorder.all().first()
        assertEquals(TransportResult.NoRoute, entry.result)
        assertTrue(entry.routesEvaluated.isEmpty())
    }

    @Test
    fun `permission denied records PermissionDenied flight entry`() = runBlocking {
        val recorder = FlightRecorder()
        val dispatcher = dispatcher(
            recorder,
            listOf(adapter(Protocol.DirectIr)),
            permissionResolver = { emptySet() }
        )

        val result = dispatcher.dispatch(UniversalAction.PowerOn(deviceId))

        assertTrue(result is DispatchResult.PermissionDenied)
        assertEquals(TransportResult.PermissionDenied, recorder.all().first().result)
    }

    @Test
    fun `breaker-blocked dispatch records CircuitBreakerOpen entry`() = runBlocking {
        val recorder = FlightRecorder()
        val breaker = CircuitBreaker(failureThreshold = 2, cooldownMs = 600_000)
        breaker.recordFailure(Protocol.DirectIr)
        breaker.recordFailure(Protocol.DirectIr)
        val dispatcher = dispatcher(
            recorder,
            listOf(adapter(Protocol.DirectIr, shouldSucceed = true)),
            breaker = breaker
        )

        val result = dispatcher.dispatch(UniversalAction.PowerOn(deviceId))

        assertTrue(result is DispatchResult.AllRoutesFailed)
        val entry = recorder.all().first()
        assertEquals(TransportResult.CircuitBreakerOpen, entry.result)
        assertTrue("breaker trip must be flagged", entry.circuitBreakerTripped)
        assertTrue("winning route is null when blocked", entry.winningRoute == null)
    }

    @Test
    fun `no-flight-recorder dispatcher produces no entries and no overhead`() = runBlocking {
        val recorder = FlightRecorder()
        val dispatcher = ActionDispatcher(
            routeNegotiator = RouteNegotiator(listOf(adapter(Protocol.DirectIr))),
            sessionManager = SessionManager(),
            neutralizer = DisconnectNeutralizer(),
            evidenceStore = ControlEvidenceStore(),
            twinResolver = { twin },
            permissionResolver = { setOf(PermissionGate.TRANSMIT_IR) },
            injectedIrResolver = fakeIrResolver()
        )

        dispatcher.dispatch(UniversalAction.PowerOn(deviceId))

        assertEquals(0, recorder.size)
    }
}