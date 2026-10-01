package com.elysium.nexus.fabric.hedging

import com.elysium.nexus.fabric.adapter.AdapterResult
import com.elysium.nexus.fabric.adapter.AdapterState
import com.elysium.nexus.fabric.adapter.DeviceAdapter
import com.elysium.nexus.fabric.adapter.ErrorCode
import com.elysium.nexus.fabric.adapter.ReadResult
import com.elysium.nexus.fabric.adapter.ScanResult
import com.elysium.nexus.fabric.adapter.WriteResult
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.DeviceTwin
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.ProtocolBinding
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.routing.RouteNegotiator
import com.elysium.nexus.fabric.routing.TransportRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HedgedExecutorTest {

    private val target = DeviceId("dev-1")

    private fun route(protocol: Protocol): TransportRoute = TransportRoute(
        protocol = protocol,
        adapter = FakeAdapter(protocol),
        binding = ProtocolBinding(
            protocol = protocol,
            endpoint = "test://$protocol",
            capabilities = setOf(Capability.OnOff, Capability.Volume)
        ),
        priority = RouteNegotiator.protocolPriority(protocol),
        latencyEstimateMs = 50L,
        isAvailable = true
    )

    private val primary = route(Protocol.WiFi)
    private val backup = route(Protocol.DirectIr)

    @Test
    fun `non-idempotent action never touches backup even when primary fails`() = runTest {
        val executor = HedgedExecutor(hedgeDelayMs = 10)
        var backupCalls = 0

        val result = executor.executeWithHedge<Boolean>(
            action = UniversalAction.VolumeUp(target),
            primary = primary,
            backup = backup,
            executor = { route ->
                if (route == backup) {
                    backupCalls++
                    true
                } else {
                    null
                }
            }
        )

        assertEquals(0, backupCalls)
        assertTrue(result is HedgedResult.PrimaryFailed)
    }

    @Test
    fun `destructive custom action never hedges`() = runTest {
        val executor = HedgedExecutor(hedgeDelayMs = 5)
        var backupCalls = 0

        val result = executor.executeWithHedge<Boolean>(
            action = UniversalAction.Custom(targetDeviceId = target, key = "factory_reset"),
            primary = primary,
            backup = backup,
            executor = { route ->
                if (route == backup) {
                    backupCalls++
                    true
                } else {
                    null
                }
            }
        )

        assertEquals(0, backupCalls)
        assertTrue(result is HedgedResult.PrimaryFailed)
    }

    @Test
    fun `idempotent action falls back to backup when primary acks late`() = runTest {
        val executor = HedgedExecutor(hedgeDelayMs = 20)
        var backupCalls = 0

        val result = executor.executeWithHedge<Boolean>(
            action = UniversalAction.Mute(target),
            primary = primary,
            backup = backup,
            executor = { route ->
                if (route == backup) {
                    backupCalls++
                    true
                } else {
                    delay(500) // primary is slow: exceeds hedge delay
                    true
                }
            }
        )

        assertEquals(1, backupCalls)
        assertTrue(result is HedgedResult.BackupSuccess || result is HedgedResult.PrimarySuccess)
    }

    @Test
    fun `idempotent action with no backup still succeeds on primary`() = runTest {
        val executor = HedgedExecutor(hedgeDelayMs = 5)

        val result = executor.executeWithHedge<Boolean>(
            action = UniversalAction.PowerOn(target),
            primary = primary,
            backup = null,
            executor = { true }
        )

        assertTrue(result is HedgedResult.PrimarySuccess)
    }

    @Test
    fun `idempotent action primary failure returns BothFailed only when backup tried`() = runTest {
        val executor = HedgedExecutor(hedgeDelayMs = 5)

        val result = executor.executeWithHedge<Boolean>(
            action = UniversalAction.Mute(target),
            primary = primary,
            backup = backup,
            executor = { null }
        )

        // primary failed, backup tried and failed too
        assertFalse(result is HedgedResult.BackupSuccess)
        assertTrue(result is HedgedResult.BothFailed || result is HedgedResult.PrimaryFailed)
    }
}

private class FakeAdapter(
    override val protocol: Protocol
) : DeviceAdapter {
    override val label: String = "Fake"
    override val supportedCapabilities: Set<Capability> = emptySet()
    override val state: MutableStateFlow<AdapterState> = MutableStateFlow(AdapterState.Active)
    override val devices: MutableStateFlow<List<DeviceTwin>> = MutableStateFlow(emptyList())
    override suspend fun start() = AdapterResult.Ok
    override suspend fun scan(timeoutMs: Long) = ScanResult.Ok(0)
    override suspend fun read(deviceId: DeviceId) = ReadResult.Error(ErrorCode.DeviceNotFound, "fake")
    override suspend fun write(deviceId: DeviceId, state: DeviceState) = WriteResult.Ok(state)
    override suspend fun subscribe(deviceId: DeviceId) = AdapterResult.Ok
    override suspend fun unsubscribe(deviceId: DeviceId) = AdapterResult.Ok
    override suspend fun stop() = AdapterResult.Ok
}