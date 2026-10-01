package com.elysium.nexus.fabric

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
import com.elysium.nexus.fabric.dispatch.ActionDispatcher
import com.elysium.nexus.fabric.dispatch.CommandResolution
import com.elysium.nexus.fabric.dispatch.DispatchResult
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

/**
 * End-to-end integration test verifying the full platform flow:
 * UniversalAction → RouteNegotiator → PermissionGate → ControlSession →
 * Adapter Write → Evidence Store → Disconnect Neutralizer (§38 compliance).
 */
class IrPlatformIntegrationTest {

    private val targetDeviceId = DeviceId("integration-tv-001")

    // §4.7 JVM-safe fake for DeviceCommandResolver (needs an Android Context on device).
    private fun fakeIrResolver(): IrCommandResolver = IrCommandResolver { _, action ->
        val irAction = when (action) {
            is UniversalAction.VolumeUp -> IrAction.VOLUME_UP
            is UniversalAction.MediaPlay -> IrAction.PLAY
            is UniversalAction.MediaStop -> IrAction.STOP
            else -> null
        } ?: return@IrCommandResolver CommandResolution.ActionNotInProfile(targetDeviceId.value, IrAction.POWER_TOGGLE)
        CommandResolution.Resolved(
            profileId = targetDeviceId.value,
            codeSetId = "integration-code-set",
            action = irAction,
            signalId = "integration-signal-$irAction",
            physicalSha256 = "integration-fingerprint",
            signal = IrSignal.Encoded(
                carrierHz = 38000,
                protocol = IrProtocol.Nec,
                address = 0x07,
                command = irAction.ordinal
            )
        )
    }

    private fun mockAdapter(protocol: Protocol): DeviceAdapter = object : DeviceAdapter {
        override val protocol: Protocol = protocol
        override val label: String = protocol.name
        override val supportedCapabilities: Set<Capability> = setOf(Capability.OnOff, Capability.Volume, Capability.MediaTransport)
        private val _state = MutableStateFlow(AdapterState.Active)
        override val state: StateFlow<AdapterState> = _state
        private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
        override val devices: StateFlow<List<DeviceTwin>> = _devices
        override suspend fun start(): AdapterResult = AdapterResult.Ok
        override suspend fun scan(timeoutMs: Long): ScanResult = ScanResult.Ok(0)
        override suspend fun read(deviceId: DeviceId): ReadResult = ReadResult.Error(ErrorCode.UnsupportedOperation, "mock")
        override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult = WriteResult.Ok(reportedState = state)
        override suspend fun subscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
        override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
        override suspend fun stop(): AdapterResult = AdapterResult.Ok
    }

    @Test
    fun `full platform integration lifecycle executes successfully`() = runBlocking {
        val irAdapter = mockAdapter(Protocol.DirectIr)
        val routeNegotiator = RouteNegotiator(listOf(irAdapter))
        val sessionManager = SessionManager()
        val neutralizer = DisconnectNeutralizer()
        val evidenceStore = ControlEvidenceStore()

        val twin = DeviceTwin(
            deviceId = targetDeviceId,
            deviceType = DeviceType.Television,
            capabilities = setOf(Capability.OnOff, Capability.Volume, Capability.MediaTransport),
            connectivity = ConnectivityState.Online,
            label = "Integration Target TV",
            protocolBindings = setOf(
                ProtocolBinding(Protocol.DirectIr, "ir-endpoint-001", setOf(Capability.OnOff, Capability.Volume, Capability.MediaTransport))
            )
        )

        val dispatcher = ActionDispatcher(
            routeNegotiator = routeNegotiator,
            sessionManager = sessionManager,
            neutralizer = neutralizer,
            evidenceStore = evidenceStore,
            twinResolver = { id -> if (id == targetDeviceId) twin else null },
            permissionResolver = { setOf(PermissionGate.TRANSMIT_IR) },
            injectedIrResolver = fakeIrResolver()
        )

        // 1. Dispatch VolumeUp action
        val volumeAction = UniversalAction.VolumeUp(targetDeviceId = targetDeviceId)
        val volResult = dispatcher.dispatch(volumeAction)
        assertTrue(volResult is DispatchResult.Success)

        // 2. Dispatch MediaPlay action (holdable input)
        val playAction = UniversalAction.MediaPlay(targetDeviceId = targetDeviceId)
        val playResult = dispatcher.dispatch(playAction)
        assertTrue(playResult is DispatchResult.Success)

        // Verify session is active and evidence recorded
        val session = sessionManager.sessionFor(targetDeviceId)
        assertNotNull(session)
        assertTrue(session!!.isActive)
        assertEquals(2, evidenceStore.size)
        assertTrue(neutralizer.hasInflight())

        // 3. Abrupt Session Termination (Disconnect) -> Input Neutralization
        val neutralActions = dispatcher.terminateSession(targetDeviceId)

        // Verify Test #38 compliance: MediaStop issued, no inflight stuck inputs
        assertEquals(1, neutralActions.size)
        assertTrue(neutralActions[0] is UniversalAction.MediaStop)
        assertFalse(neutralizer.hasInflight())
        assertTrue(sessionManager.sessionFor(targetDeviceId)!!.isTerminated)

        // Evidence store records the neutralization event
        val neutralizedEvents = evidenceStore.query(result = EventResult.Neutralized)
        assertEquals(1, neutralizedEvents.size)
    }
}
