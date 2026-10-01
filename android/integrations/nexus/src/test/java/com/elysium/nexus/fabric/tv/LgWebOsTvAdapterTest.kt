package com.elysium.nexus.fabric.tv

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.Direction
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LgWebOsTvAdapterTest {

    private fun createAdapter(
        ipAddress: String = "10.0.0.10",
        port: Int = 3000,
        savedClientKey: String? = "key-123"
    ) = LgWebOsTvAdapter(ipAddress, port, savedClientKey)

    @Test
    fun `brand and supported protocols are declared honestly`() {
        val adapter = createAdapter()

        assertEquals(TvBrand.LG, adapter.brand)
        assertEquals(setOf(Protocol.WiFi, Protocol.HdmiCec, Protocol.DirectIr), adapter.supportedProtocols)
    }

    @Test
    fun `supported capabilities include core TV control`() {
        val adapter = createAdapter()

        assertTrue(adapter.supportedCapabilities.contains(Capability.OnOff))
        assertTrue(adapter.supportedCapabilities.contains(Capability.Volume))
        assertTrue(adapter.supportedCapabilities.contains(Capability.Channel))
        assertTrue(adapter.supportedCapabilities.contains(Capability.InputSource))
        assertTrue(adapter.supportedCapabilities.contains(Capability.MediaTransport))
    }

    @Test
    fun `queryCapabilities returns declarative capability metadata`() = runBlocking {
        val adapter = createAdapter()

        val caps = adapter.queryCapabilities()

        val volume = caps.first { it.capability == Capability.Volume }
        assertEquals(0f, volume.min)
        assertEquals(100f, volume.max)
        assertTrue(volume.readable)
        assertTrue(volume.subscribable)

        val mode = caps.first { it.capability == Capability.Mode }
        assertEquals(false, mode.readable)
    }

    @Test
    fun `power on requires wake not ssap`() = runBlocking {
        // PowerOn maps to null command → Unsupported on the execute path (WoL is used instead)
        val adapter = createAdapter()

        val result = adapter.execute(UniversalAction.PowerOn(DeviceId("lg")))

        assertTrue(result is ActionExecutionResult.Unsupported)
    }

    @Test
    fun `unknown custom action is unsupported`() = runBlocking {
        val adapter = createAdapter()

        val result = adapter.execute(
            UniversalAction.Custom(DeviceId("lg"), key = "totally_unknown")
        )

        assertTrue(result is ActionExecutionResult.Unsupported)
    }

    @Test
    fun `wake fails gracefully when mac unknown`() = runBlocking {
        // No saved MAC and no server-info reachable → failed, not crash
        val adapter = createAdapter(ipAddress = "192.0.2.1")

        val result = adapter.wake()

        assertTrue(result is WakeResult.Failed)
    }

    @Test
    fun `readState for unsupported capability returns null without network`() = runBlocking {
        val adapter = createAdapter()

        val result = adapter.readState(Capability.Mode)

        assertTrue(result == null)
    }

    @Test
    fun `discover in offline environment returns empty not crash`() = runBlocking {
        // 192.0.2.x is TEST-NET — guaranteed unreachable, forces the catch path
        val adapter = createAdapter(ipAddress = "192.0.2.2")

        val records = adapter.discover(timeoutMs = 50)

        assertTrue(records.isEmpty())
    }

    @Test
    fun `identify returns low confidence evidence offline`() = runBlocking {
        val adapter = createAdapter(ipAddress = "192.0.2.3")

        val evidence = adapter.identify("http://192.0.2.3:3000")

        assertEquals(TvBrand.LG, evidence.brand)
        assertTrue(evidence.confidence < 0.5)
    }
}