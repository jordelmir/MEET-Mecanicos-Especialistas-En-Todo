package com.elysium.nexus.fabric.routing

import com.elysium.nexus.fabric.adapter.AdapterResult
import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ReadResult
import com.elysium.nexus.fabric.adapter.ErrorCode
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteNegotiatorTest {

    private val deviceId = DeviceId("test-tv-001")

    private fun fakeAdapter(
        protocol: Protocol,
        state: AdapterState = AdapterState.Active,
        capabilities: Set<Capability> = setOf(Capability.OnOff, Capability.Volume)
    ): DeviceAdapter = object : DeviceAdapter {
        override val protocol: Protocol = protocol
        override val label: String = protocol.name
        override val supportedCapabilities: Set<Capability> = capabilities
        private val _state = MutableStateFlow(state)
        override val state: StateFlow<AdapterState> = _state
        private val _devices = MutableStateFlow<List<DeviceTwin>>(emptyList())
        override val devices: StateFlow<List<DeviceTwin>> = _devices
        override suspend fun start(): AdapterResult = AdapterResult.Ok
        override suspend fun scan(timeoutMs: Long): ScanResult = ScanResult.Ok(0)
        override suspend fun read(deviceId: DeviceId): ReadResult =
            ReadResult.Error(ErrorCode.UnsupportedOperation, "fake")
        override suspend fun write(deviceId: DeviceId, state: DeviceState): WriteResult =
            WriteResult.Ok(reportedState = null)
        override suspend fun subscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
        override suspend fun unsubscribe(deviceId: DeviceId): AdapterResult = AdapterResult.Ok
        override suspend fun stop(): AdapterResult = AdapterResult.Ok
    }

    private fun tvTwin(vararg bindings: ProtocolBinding): DeviceTwin = DeviceTwin(
        deviceId = deviceId,
        deviceType = DeviceType.Television,
        capabilities = setOf(Capability.OnOff, Capability.Volume, Capability.Channel),
        connectivity = ConnectivityState.Online,
        label = "Test TV",
        protocolBindings = bindings.toSet()
    )

    @Test
    fun `negotiate returns routes sorted by priority`() {
        val irAdapter = fakeAdapter(Protocol.DirectIr)
        val bleAdapter = fakeAdapter(Protocol.HidOverBle)

        val twin = tvTwin(
            ProtocolBinding(Protocol.HidOverBle, "ble-001", setOf(Capability.OnOff, Capability.Volume)),
            ProtocolBinding(Protocol.DirectIr, "ir-001", setOf(Capability.OnOff, Capability.Volume))
        )

        val negotiator = RouteNegotiator(listOf(irAdapter, bleAdapter))
        val action = UniversalAction.VolumeUp(targetDeviceId = deviceId)
        val routes = negotiator.negotiate(action, twin)

        assertEquals(2, routes.size)
        // IR should be first (priority 10 < BLE priority 25)
        assertEquals(Protocol.DirectIr, routes[0].protocol)
        assertEquals(Protocol.HidOverBle, routes[1].protocol)
    }

    @Test
    fun `negotiate filters by required capability`() {
        val irAdapter = fakeAdapter(Protocol.DirectIr)

        val twin = tvTwin(
            ProtocolBinding(Protocol.DirectIr, "ir-001", setOf(Capability.OnOff))
            // No Volume capability in the binding
        )

        val negotiator = RouteNegotiator(listOf(irAdapter))
        val action = UniversalAction.VolumeUp(targetDeviceId = deviceId)
        val routes = negotiator.negotiate(action, twin)

        // Volume capability not in binding → no routes
        assertTrue(routes.isEmpty())
    }

    @Test
    fun `negotiate returns empty when no adapter matches protocol`() {
        // Only a BLE adapter, but device only has IR binding
        val bleAdapter = fakeAdapter(Protocol.Ble)

        val twin = tvTwin(
            ProtocolBinding(Protocol.DirectIr, "ir-001", setOf(Capability.OnOff))
        )

        val negotiator = RouteNegotiator(listOf(bleAdapter))
        val action = UniversalAction.PowerOn(targetDeviceId = deviceId)
        val routes = negotiator.negotiate(action, twin)

        assertTrue(routes.isEmpty())
    }

    @Test
    fun `negotiate marks inactive adapters as unavailable`() {
        val irAdapter = fakeAdapter(Protocol.DirectIr, state = AdapterState.Idle)

        val twin = tvTwin(
            ProtocolBinding(Protocol.DirectIr, "ir-001", setOf(Capability.OnOff))
        )

        val negotiator = RouteNegotiator(listOf(irAdapter))
        val action = UniversalAction.PowerOn(targetDeviceId = deviceId)
        val routes = negotiator.negotiate(action, twin)

        assertEquals(1, routes.size)
        assertEquals(false, routes[0].isAvailable)
    }

    @Test
    fun `available routes sort before unavailable`() {
        val irAdapter = fakeAdapter(Protocol.DirectIr, state = AdapterState.Idle)
        val bleAdapter = fakeAdapter(Protocol.HidOverBle, state = AdapterState.Active)

        val twin = tvTwin(
            ProtocolBinding(Protocol.DirectIr, "ir-001", setOf(Capability.OnOff)),
            ProtocolBinding(Protocol.HidOverBle, "ble-001", setOf(Capability.OnOff))
        )

        val negotiator = RouteNegotiator(listOf(irAdapter, bleAdapter))
        val action = UniversalAction.PowerOn(targetDeviceId = deviceId)
        val routes = negotiator.negotiate(action, twin)

        assertEquals(2, routes.size)
        // BLE (available) should sort before IR (unavailable)
        assertTrue(routes[0].isAvailable)
        assertEquals(Protocol.HidOverBle, routes[0].protocol)
    }

    @Test
    fun `protocolPriority covers all Protocol values`() {
        for (protocol in Protocol.values()) {
            val priority = RouteNegotiator.protocolPriority(protocol)
            assertTrue("Priority for $protocol must be non-negative", priority >= 0)
        }
    }

    @Test
    fun `protocolLatencyEstimate covers all Protocol values`() {
        for (protocol in Protocol.values()) {
            val latency = RouteNegotiator.protocolLatencyEstimate(protocol)
            assertTrue("Latency for $protocol must be non-negative", latency >= 0)
        }
    }

    @Test
    fun `TransportRoute rejects negative priority`() {
        try {
            TransportRoute(
                protocol = Protocol.DirectIr,
                adapter = fakeAdapter(Protocol.DirectIr),
                binding = ProtocolBinding(Protocol.DirectIr, "ir-001", setOf(Capability.OnOff)),
                priority = -1,
                latencyEstimateMs = 5L,
                isAvailable = true
            )
            org.junit.Assert.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("priority"))
        }
    }
}
