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
import com.elysium.nexus.fabric.dispatch.CommandResolution
import com.elysium.nexus.fabric.dispatch.IrCommandResolver
import com.elysium.nexus.fabric.evidence.ControlEvidenceStore
import com.elysium.nexus.fabric.evidence.EventResult
import com.elysium.nexus.fabric.infrared.IrProtocol
import com.elysium.nexus.fabric.resilience.DisconnectNeutralizer
import com.elysium.nexus.fabric.routing.RouteNegotiator
import com.elysium.nexus.fabric.session.PermissionGate
import com.elysium.nexus.fabric.session.SessionManager
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDispatcherTest {

    private val deviceId = DeviceId("tv-dispatch-001")

    // §4.7 Fake resolver: JVM-safe stand-in for DeviceCommandResolver (which needs an Android Context).
    private fun fakeIrResolver(): IrCommandResolver = IrCommandResolver { _, action ->
        val irAction = when (action) {
            is UniversalAction.PowerOn -> IrAction.POWER_ON
            is UniversalAction.PowerToggle -> IrAction.POWER_TOGGLE
            is UniversalAction.VolumeUp -> IrAction.VOLUME_UP
            is UniversalAction.Mute -> IrAction.MUTE
            is UniversalAction.MediaPlay -> IrAction.PLAY
            is UniversalAction.MediaStop -> IrAction.STOP
            else -> null
        } ?: return@IrCommandResolver CommandResolution.ActionNotInProfile(deviceId.value, IrAction.POWER_TOGGLE)
        CommandResolution.Resolved(
            profileId = deviceId.value,
            codeSetId = "fake-code-set",
            action = irAction,
            signalId = "fake-signal-$irAction",
            physicalSha256 = "fake-fingerprint",
            signal = IrSignal.Encoded(
                carrierHz = 38000,
                protocol = IrProtocol.Nec,
                address = 0x07,
                command = irAction.ordinal
            )
        )
    }

    private fun fakeAdapter(
        protocol: Protocol,
        state: AdapterState = AdapterState.Active,
        shouldSucceed: Boolean = true
    ): DeviceAdapter = object : DeviceAdapter {
        override val protocol: Protocol = protocol
        override val label: String = protocol.name
        override val supportedCapabilities: Set<Capability> = setOf(Capability.OnOff, Capability.Volume)
        private val _state = MutableStateFlow(state)
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

    private fun tvTwin(vararg bindings: ProtocolBinding): DeviceTwin = DeviceTwin(
        deviceId = deviceId,
        deviceType = DeviceType.Television,
        capabilities = setOf(Capability.OnOff, Capability.Volume),
        connectivity = ConnectivityState.Online,
        label = "Dispatch Test TV",
        protocolBindings = bindings.toSet()
    )

    @Test
    fun `dispatch succeeds through full pipeline`() = runBlocking {
        val irAdapter = fakeAdapter(Protocol.DirectIr)
        val routeNegotiator = RouteNegotiator(listOf(irAdapter))
        val sessionManager = SessionManager()
        val neutralizer = DisconnectNeutralizer()
        val evidenceStore = ControlEvidenceStore()

        val twin = tvTwin(ProtocolBinding(Protocol.DirectIr, "ir-001", setOf(Capability.OnOff)))

        val dispatcher = ActionDispatcher(
            routeNegotiator = routeNegotiator,
            sessionManager = sessionManager,
            neutralizer = neutralizer,
            evidenceStore = evidenceStore,
            twinResolver = { id -> if (id == deviceId) twin else null },
            permissionResolver = { setOf(PermissionGate.TRANSMIT_IR) },
            injectedIrResolver = fakeIrResolver()
        )

        val action = UniversalAction.PowerOn(targetDeviceId = deviceId)
        val result = dispatcher.dispatch(action)

        assertTrue(result is DispatchResult.Success)
        val success = result as DispatchResult.Success
        assertEquals(Protocol.DirectIr, success.route.protocol)
        assertEquals(1, evidenceStore.size)
        assertEquals(EventResult.Success, evidenceStore.all()[0].result)
        assertNotNull(sessionManager.sessionFor(deviceId))
    }

    @Test
    fun `dispatch returns NoTarget when device not found`() = runBlocking {
        val routeNegotiator = RouteNegotiator(emptyList())
        val sessionManager = SessionManager()
        val neutralizer = DisconnectNeutralizer()
        val evidenceStore = ControlEvidenceStore()

        val dispatcher = ActionDispatcher(
            routeNegotiator = routeNegotiator,
            sessionManager = sessionManager,
            neutralizer = neutralizer,
            evidenceStore = evidenceStore,
            twinResolver = { null },
            permissionResolver = { emptySet() }
        )

        val action = UniversalAction.PowerOn(targetDeviceId = deviceId)
        val result = dispatcher.dispatch(action)

        assertTrue(result is DispatchResult.NoTarget)
    }

    @Test
    fun `dispatch falls back to secondary route when primary fails`() = runBlocking {
        val failingIrAdapter = fakeAdapter(Protocol.DirectIr, shouldSucceed = false)
        val workingBleAdapter = fakeAdapter(Protocol.HidOverBle, shouldSucceed = true)

        val routeNegotiator = RouteNegotiator(listOf(failingIrAdapter, workingBleAdapter))
        val sessionManager = SessionManager()
        val neutralizer = DisconnectNeutralizer()
        val evidenceStore = ControlEvidenceStore()

        val twin = tvTwin(
            ProtocolBinding(Protocol.DirectIr, "ir-001", setOf(Capability.OnOff)),
            ProtocolBinding(Protocol.HidOverBle, "ble-001", setOf(Capability.OnOff))
        )

        val dispatcher = ActionDispatcher(
            routeNegotiator = routeNegotiator,
            sessionManager = sessionManager,
            neutralizer = neutralizer,
            evidenceStore = evidenceStore,
            twinResolver = { twin },
            permissionResolver = { setOf(PermissionGate.TRANSMIT_IR, PermissionGate.BLUETOOTH_CONNECT, PermissionGate.BLUETOOTH_SCAN) },
            injectedIrResolver = fakeIrResolver()
        )

        val action = UniversalAction.PowerOn(targetDeviceId = deviceId)
        val result = dispatcher.dispatch(action)

        assertTrue(result is DispatchResult.Success)
        val success = result as DispatchResult.Success
        assertEquals(Protocol.HidOverBle, success.route.protocol)
        assertEquals(2, evidenceStore.size) // Fallback event + Success event
    }

    @Test
    fun `terminateSession neutralizes inflight inputs and ends session`() = runBlocking {
        val mediaAdapter = fakeAdapter(Protocol.DirectIr)
        val routeNegotiator = RouteNegotiator(listOf(mediaAdapter))
        val sessionManager = SessionManager()
        val neutralizer = DisconnectNeutralizer()
        val evidenceStore = ControlEvidenceStore()

        val twin = tvTwin(ProtocolBinding(Protocol.DirectIr, "ir-001", setOf(Capability.MediaTransport, Capability.OnOff)))

        val dispatcher = ActionDispatcher(
            routeNegotiator = routeNegotiator,
            sessionManager = sessionManager,
            neutralizer = neutralizer,
            evidenceStore = evidenceStore,
            twinResolver = { twin },
            permissionResolver = { setOf(PermissionGate.TRANSMIT_IR) },
            injectedIrResolver = fakeIrResolver()
        )

        // Dispatch MediaPlay
        dispatcher.dispatch(UniversalAction.MediaPlay(targetDeviceId = deviceId))
        assertTrue(neutralizer.hasInflight())

        // Terminate session
        val neutralizedActions = dispatcher.terminateSession(deviceId)

        assertEquals(1, neutralizedActions.size)
        assertTrue(neutralizedActions[0] is UniversalAction.MediaStop)
        assertFalse(neutralizer.hasInflight())
        assertTrue(sessionManager.sessionFor(deviceId)!!.isTerminated)
    }
}
