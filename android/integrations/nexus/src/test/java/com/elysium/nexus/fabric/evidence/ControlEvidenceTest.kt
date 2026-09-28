package com.elysium.nexus.fabric.evidence

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlEvidenceTest {

    private val deviceId = DeviceId("dev-evidence-001")

    @Test
    fun `hashDeviceId returns 16-char hex hash without PII`() {
        val hash1 = ControlEvidenceStore.hashDeviceId(deviceId)
        val hash2 = ControlEvidenceStore.hashDeviceId(deviceId)

        assertEquals(16, hash1.length)
        assertEquals(hash1, hash2)
        assertFalse(hash1.contains("dev-evidence-001"))
    }

    @Test
    fun `evidence store respects ring buffer capacity`() {
        val store = ControlEvidenceStore(maxEvents = 3)
        val hash = ControlEvidenceStore.hashDeviceId(deviceId)

        for (i in 1..5) {
            store.record(
                ControlEvent(
                    timestampNs = i * 1000L,
                    deviceIdHash = hash,
                    actionType = "Action$i",
                    correlationId = "corr-$i",
                    protocol = Protocol.DirectIr,
                    result = EventResult.Success
                )
            )
        }

        assertEquals(3, store.size)
        val events = store.all()
        assertEquals("Action3", events[0].actionType)
        assertEquals("Action4", events[1].actionType)
        assertEquals("Action5", events[2].actionType)
    }

    @Test
    fun `query filters events correctly`() {
        val store = ControlEvidenceStore(maxEvents = 10)
        val hash = ControlEvidenceStore.hashDeviceId(deviceId)

        store.record(
            ControlEvent(
                timestampNs = 1000L,
                deviceIdHash = hash,
                actionType = "PowerOn",
                correlationId = "c1",
                protocol = Protocol.DirectIr,
                result = EventResult.Success,
                latencyMs = 10L
            )
        )
        store.record(
            ControlEvent(
                timestampNs = 2000L,
                deviceIdHash = hash,
                actionType = "VolumeUp",
                correlationId = "c2",
                protocol = Protocol.HidOverBle,
                result = EventResult.AdapterError,
                errorMessage = "GATT disconnected"
            )
        )

        val successEvents = store.query(result = EventResult.Success)
        assertEquals(1, successEvents.size)
        assertEquals("PowerOn", successEvents[0].actionType)

        val irEvents = store.query(protocol = Protocol.DirectIr)
        assertEquals(1, irEvents.size)
        assertEquals("PowerOn", irEvents[0].actionType)

        val countMap = store.countByResult()
        assertEquals(1, countMap[EventResult.Success])
        assertEquals(1, countMap[EventResult.AdapterError])

        val avgLatency = store.averageSuccessLatencyMs()
        assertNotNull(avgLatency)
        assertEquals(10.0, avgLatency!!, 0.001)
    }
}
