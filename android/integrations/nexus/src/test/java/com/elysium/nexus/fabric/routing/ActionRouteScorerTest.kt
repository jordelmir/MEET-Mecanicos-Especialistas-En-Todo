package com.elysium.nexus.fabric.routing

import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.DeviceType
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.ProtocolBinding
import com.elysium.nexus.fabric.canonical.TrustState
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.evidence.ControlEvidenceStore
import com.elysium.nexus.fabric.evidence.ControlEvent
import com.elysium.nexus.fabric.evidence.EventResult
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionRouteScorerTest {

    private lateinit var evidenceStore: ControlEvidenceStore
    private lateinit var scorer: ActionRouteScorer

    private val testDeviceId = DeviceId("test-device-1")
    private val allCapabilities = setOf(Capability.OnOff, Capability.Volume, Capability.Channel)

    @Before
    fun setup() {
        evidenceStore = ControlEvidenceStore(maxEvents = 100)
        scorer = ActionRouteScorer(evidenceStore)
    }

    private fun makeDevice(
        protocols: List<Protocol>,
        trust: TrustState = TrustState.SelfDeclared
    ) = DeviceTwin(
        deviceId = testDeviceId,
        manufacturer = "Test",
        model = "Test TV",
        deviceType = DeviceType.Television,
        capabilities = allCapabilities,
        protocolBindings = protocols.map { p ->
            ProtocolBinding(protocol = p, endpoint = "test://$p", capabilities = allCapabilities)
        }.toSet(),
        trust = trust,
        lastSeenNs = System.nanoTime()
    )

    private fun makeRoute(
        protocol: Protocol,
        available: Boolean = true,
        latencyMs: Long = 50L,
        capabilities: Set<Capability> = allCapabilities
    ) = TransportRoute(
        protocol = protocol,
        adapter = FakeTestAdapter(protocol, available),
        binding = ProtocolBinding(protocol = protocol, endpoint = "test://$protocol", capabilities = capabilities),
        priority = RouteNegotiator.protocolPriority(protocol),
        latencyEstimateMs = latencyMs,
        isAvailable = available
    )

    @Test
    fun `score returns zero when capability not supported`() {
        val device = makeDevice(listOf(Protocol.DirectIr))
        val route = makeRoute(Protocol.DirectIr, capabilities = setOf(Capability.OnOff))
        val action = UniversalAction.VolumeUp(targetDeviceId = testDeviceId)

        val score = scorer.score(action, device, route)
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun `score returns zero when adapter unavailable`() {
        val device = makeDevice(listOf(Protocol.DirectIr))
        val route = makeRoute(Protocol.DirectIr, available = false)
        val action = UniversalAction.PowerOn(targetDeviceId = testDeviceId)

        val score = scorer.score(action, device, route)
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun `score is higher for lower latency`() {
        val device = makeDevice(listOf(Protocol.DirectIr, Protocol.WiFi))
        val fastRoute = makeRoute(Protocol.DirectIr, latencyMs = 5L, capabilities = setOf(Capability.OnOff))
        val slowRoute = makeRoute(Protocol.WiFi, latencyMs = 200L, capabilities = setOf(Capability.OnOff))
        val action = UniversalAction.PowerOn(targetDeviceId = testDeviceId)

        val fastScore = scorer.score(action, device, fastRoute)
        val slowScore = scorer.score(action, device, slowRoute)
        assertTrue("Fast route ($fastScore) should score higher than slow ($slowScore)", fastScore > slowScore)
    }

    @Test
    fun `score is higher when history has successes`() {
        val device = makeDevice(listOf(Protocol.DirectIr))
        val route = makeRoute(Protocol.DirectIr, capabilities = setOf(Capability.OnOff))
        val action = UniversalAction.PowerOn(targetDeviceId = testDeviceId)

        repeat(10) {
            evidenceStore.record(ControlEvent(
                timestampNs = System.nanoTime(),
                deviceIdHash = "test",
                actionType = "PowerOn",
                correlationId = "test",
                protocol = Protocol.DirectIr,
                result = EventResult.Success,
                latencyMs = 5L
            ))
        }

        val scoreWithHistory = scorer.score(action, device, route)
        evidenceStore.clear()
        val scoreWithoutHistory = scorer.score(action, device, route)

        assertTrue("Score with success history ($scoreWithHistory) should be higher than without ($scoreWithoutHistory)", scoreWithHistory > scoreWithoutHistory)
    }

    @Test
    fun `score penalizes recent failures`() {
        val device = makeDevice(listOf(Protocol.WiFi))
        val route = makeRoute(Protocol.WiFi, capabilities = setOf(Capability.OnOff))
        val action = UniversalAction.PowerOn(targetDeviceId = testDeviceId)

        repeat(5) {
            evidenceStore.record(ControlEvent(
                timestampNs = System.nanoTime(),
                deviceIdHash = "test",
                actionType = "PowerOn",
                correlationId = "test",
                protocol = Protocol.WiFi,
                result = EventResult.AdapterError,
                latencyMs = null
            ))
        }

        val score = scorer.score(action, device, route)
        assertTrue("Score should be penalized for recent failures", score < 0.9)
    }

    @Test
    fun `score higher for manufacturer certified trust`() {
        val trustedDevice = makeDevice(listOf(Protocol.DirectIr), trust = TrustState.ManufacturerCertified)
        val untrustedDevice = makeDevice(listOf(Protocol.DirectIr), trust = TrustState.Untrusted)
        val route = makeRoute(Protocol.DirectIr, capabilities = setOf(Capability.OnOff))
        val action = UniversalAction.PowerOn(targetDeviceId = testDeviceId)

        val trustedScore = scorer.score(action, trustedDevice, route)
        val untrustedScore = scorer.score(action, untrustedDevice, route)
        assertTrue("Trusted ($trustedScore) should score higher than untrusted ($untrustedScore)", trustedScore > untrustedScore)
    }

    @Test
    fun `rank returns routes sorted by score descending`() {
        val device = makeDevice(listOf(Protocol.DirectIr, Protocol.WiFi, Protocol.Ble))
        val irRoute = makeRoute(Protocol.DirectIr, latencyMs = 5L, capabilities = setOf(Capability.OnOff))
        val wifiRoute = makeRoute(Protocol.WiFi, latencyMs = 20L, capabilities = setOf(Capability.OnOff))
        val bleRoute = makeRoute(Protocol.Ble, latencyMs = 25L, capabilities = setOf(Capability.OnOff))
        val action = UniversalAction.PowerOn(targetDeviceId = testDeviceId)

        val ranked = scorer.rank(action, device, listOf(wifiRoute, bleRoute, irRoute))
        assertEquals(3, ranked.size)
        // Verify scores are in descending order
        for (i in 0 until ranked.size - 1) {
            assertTrue(
                "Score at index $i (${ranked[i].score}) should be >= score at ${i + 1} (${ranked[i + 1].score})",
                ranked[i].score >= ranked[i + 1].score
            )
        }
        // All routes should have a positive score
        assertTrue("Best route score should be positive", ranked[0].score > 0.0)
    }

    @Test
    fun `confirmation capable protocols get bonus`() {
        val device = makeDevice(listOf(Protocol.Matter, Protocol.DirectIr))
        val matterRoute = makeRoute(Protocol.Matter, latencyMs = 30L, capabilities = setOf(Capability.OnOff))
        val irRoute = makeRoute(Protocol.DirectIr, latencyMs = 30L, capabilities = setOf(Capability.OnOff))
        val action = UniversalAction.PowerOn(targetDeviceId = testDeviceId)

        val matterScore = scorer.score(action, device, matterRoute)
        val irScore = scorer.score(action, device, irRoute)
        assertTrue("Matter ($matterScore) should score higher than IR ($irScore) due to confirmation", matterScore > irScore)
    }
}

private class FakeTestAdapter(
    override val protocol: Protocol,
    private val isActive: Boolean
) : com.elysium.nexus.fabric.adapter.DeviceAdapter {
    override val label: String = "Fake"
    override val supportedCapabilities: Set<Capability> = emptySet()
    override val state: MutableStateFlow<AdapterState> = MutableStateFlow(if (isActive) AdapterState.Active else AdapterState.Idle)
    override val devices: MutableStateFlow<List<com.elysium.nexus.fabric.canonical.DeviceTwin>> = MutableStateFlow(emptyList())
    override suspend fun start() = com.elysium.nexus.fabric.adapter.AdapterResult.Ok
    override suspend fun scan(timeoutMs: Long) = com.elysium.nexus.fabric.adapter.ScanResult.Ok(0)
    override suspend fun read(deviceId: DeviceId) = com.elysium.nexus.fabric.adapter.ReadResult.Error(com.elysium.nexus.fabric.adapter.ErrorCode.DeviceNotFound, "fake")
    override suspend fun write(deviceId: DeviceId, state: DeviceState) = com.elysium.nexus.fabric.adapter.WriteResult.Ok(state)
    override suspend fun subscribe(deviceId: DeviceId) = com.elysium.nexus.fabric.adapter.AdapterResult.Ok
    override suspend fun unsubscribe(deviceId: DeviceId) = com.elysium.nexus.fabric.adapter.AdapterResult.Ok
    override suspend fun stop() = com.elysium.nexus.fabric.adapter.AdapterResult.Ok
}
