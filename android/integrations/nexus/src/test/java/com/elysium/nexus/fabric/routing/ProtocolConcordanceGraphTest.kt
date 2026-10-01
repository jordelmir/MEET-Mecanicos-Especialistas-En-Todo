package com.elysium.nexus.fabric.routing

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProtocolConcordanceGraphTest {

    private lateinit var graph: ProtocolConcordanceGraph
    private val deviceId = DeviceId("tv-1")

    @Before
    fun setup() {
        graph = ProtocolConcordanceGraph()
            .withProtocol(deviceId, Protocol.DirectIr)
            .withProtocol(deviceId, Protocol.WiFi)
            .withProtocol(deviceId, Protocol.HdmiCec)
            .withMapping(deviceId, "PowerOn", ProtocolMapping(Protocol.DirectIr, "NEC frame"))
            .withMapping(deviceId, "PowerOn", ProtocolMapping(Protocol.WiFi, "webOS command"))
            .withMapping(deviceId, "PowerOn", ProtocolMapping(Protocol.HdmiCec, "CEC opcode 0x04"))
            .withMapping(deviceId, "VolumeUp", ProtocolMapping(Protocol.DirectIr, "NEC extended"))
            .withMapping(deviceId, "VolumeUp", ProtocolMapping(Protocol.WiFi, "webOS volume"))
    }

    @Test
    fun `protocolsForAction returns all protocols for known action`() {
        val protocols = graph.protocolsForAction(deviceId, UniversalAction.PowerOn(targetDeviceId = deviceId))
        assertEquals(3, protocols.size)
        assertTrue(protocols.contains(Protocol.DirectIr))
        assertTrue(protocols.contains(Protocol.WiFi))
        assertTrue(protocols.contains(Protocol.HdmiCec))
    }

    @Test
    fun `protocolsForAction returns empty for unknown action`() {
        val protocols = graph.protocolsForAction(deviceId, UniversalAction.MediaPlay(targetDeviceId = deviceId))
        assertTrue(protocols.isEmpty())
    }

    @Test
    fun `protocolsForAction returns empty for unknown device`() {
        val protocols = graph.protocolsForAction(DeviceId("unknown"), UniversalAction.PowerOn(targetDeviceId = deviceId))
        assertTrue(protocols.isEmpty())
    }

    @Test
    fun `canDeliver returns true for known mapping`() {
        assertTrue(graph.canDeliver(deviceId, UniversalAction.PowerOn(targetDeviceId = deviceId), Protocol.DirectIr))
        assertTrue(graph.canDeliver(deviceId, UniversalAction.PowerOn(targetDeviceId = deviceId), Protocol.WiFi))
    }

    @Test
    fun `canDeliver returns false for unknown mapping`() {
        assertFalse(graph.canDeliver(deviceId, UniversalAction.PowerOn(targetDeviceId = deviceId), Protocol.Ble))
    }

    @Test
    fun `fallbackChain returns protocols excluding preferred`() {
        val chain = graph.fallbackChain(deviceId, Protocol.DirectIr)
        assertEquals(2, chain.size)
        assertFalse(chain.contains(Protocol.DirectIr))
        assertTrue(chain.contains(Protocol.WiFi))
        assertTrue(chain.contains(Protocol.HdmiCec))
    }

    @Test
    fun `fallbackChain returns empty for unknown device`() {
        val chain = graph.fallbackChain(DeviceId("unknown"), Protocol.DirectIr)
        assertTrue(chain.isEmpty())
    }

    @Test
    fun `fallbackChain orders by priority`() {
        val chain = graph.fallbackChain(deviceId, Protocol.DirectIr)
        // WiFi (65) should come before HDMI CEC (35) — lower priority number = better
        val wifiIndex = chain.indexOf(Protocol.WiFi)
        val cecIndex = chain.indexOf(Protocol.HdmiCec)
        assertTrue("CEC should come before WiFi in fallback chain", cecIndex < wifiIndex)
    }

    @Test
    fun `withProtocol adds new protocol`() {
        val newGraph = graph.withProtocol(deviceId, Protocol.Ble)
        assertTrue(newGraph.canDeliver(deviceId, UniversalAction.PowerOn(targetDeviceId = deviceId), Protocol.DirectIr))
        assertEquals(4, newGraph.deviceProtocols[deviceId]?.size)
    }

    @Test
    fun `withMapping adds new mapping`() {
        val newGraph = graph.withMapping(deviceId, "PowerOn", ProtocolMapping(Protocol.Ble, "BLE HID"))
        val protocols = newGraph.protocolsForAction(deviceId, UniversalAction.PowerOn(targetDeviceId = deviceId))
        assertEquals(4, protocols.size)
    }
}
