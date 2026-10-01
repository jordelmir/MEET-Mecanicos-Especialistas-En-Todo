package com.elysium.nexus.fabric.evidence

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.Protocol
import com.elysium.nexus.fabric.canonical.UniversalAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FlightRecorderTest {

    private lateinit var recorder: FlightRecorder

    @Before
    fun setup() {
        recorder = FlightRecorder(maxEntries = 100)
    }

    @Test
    fun `beginTrace returns a builder`() {
        val action = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-1"))
        val builder = recorder.beginTrace(action, DeviceId("device-1"))
        assertNotNull(builder)
    }

    @Test
    fun `completed entry is recorded`() {
        val action = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-1"))
        val entry = recorder.beginTrace(action, DeviceId("device-1"))
            .result(TransportResult.Success)
            .complete()

        assertEquals(1, recorder.size)
        assertEquals("PowerOn", entry.actionType)
        assertEquals(TransportResult.Success, entry.result)
        assertTrue(entry.isSuccessful)
    }

    @Test
    fun `multiple entries are recorded`() {
        repeat(5) { i ->
            val action = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-$i"))
            recorder.beginTrace(action, DeviceId("device-$i"))
                .result(TransportResult.Success)
                .complete()
        }

        assertEquals(5, recorder.size)
    }

    @Test
    fun `ring buffer evicts oldest when full`() {
        val smallRecorder = FlightRecorder(maxEntries = 3)

        repeat(5) { i ->
            val action = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-$i"))
            smallRecorder.beginTrace(action, DeviceId("device-$i"))
                .result(TransportResult.Success)
                .complete()
        }

        assertEquals(3, smallRecorder.size)
        // First two should be evicted
        val entries = smallRecorder.all()
        assertEquals("device-2", entries[0].targetDeviceId.value)
        assertEquals("device-4", entries[2].targetDeviceId.value)
    }

    @Test
    fun `query filters by deviceId`() {
        val action1 = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-1"))
        val action2 = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-2"))

        recorder.beginTrace(action1, DeviceId("device-1"))
            .result(TransportResult.Success).complete()
        recorder.beginTrace(action2, DeviceId("device-2"))
            .result(TransportResult.Success).complete()

        val filtered = recorder.query(deviceId = DeviceId("device-1"))
        assertEquals(1, filtered.size)
        assertEquals("device-1", filtered[0].targetDeviceId.value)
    }

    @Test
    fun `query filters by result`() {
        val action1 = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-1"))
        val action2 = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-2"))

        recorder.beginTrace(action1, DeviceId("device-1"))
            .result(TransportResult.Success).complete()
        recorder.beginTrace(action2, DeviceId("device-2"))
            .result(TransportResult.Timeout).complete()

        val successes = recorder.query(result = TransportResult.Success)
        assertEquals(1, successes.size)
    }

    @Test
    fun `averageLatencyNs returns null when empty`() {
        assertNull(recorder.averageLatencyNs())
    }

    @Test
    fun `averageLatencyNs computes correctly`() {
        val action = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-1"))
        recorder.beginTrace(action, DeviceId("device-1"))
            .result(TransportResult.Success).complete()

        val avg = recorder.averageLatencyNs()
        assertNotNull(avg)
        assertTrue(avg!! > 0L)
    }

    @Test
    fun `clear removes all entries`() {
        val action = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-1"))
        recorder.beginTrace(action, DeviceId("device-1"))
            .result(TransportResult.Success).complete()

        recorder.clear()
        assertEquals(0, recorder.size)
    }

    @Test
    fun `flight builder records routes evaluated`() {
        val action = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-1"))
        val routes = listOf(
            CandidateRoute(Protocol.DirectIr, 0.9, isSelected = true),
            CandidateRoute(Protocol.WiFi, 0.7, isSelected = false, rejectionReason = "lower score")
        )

        val entry = recorder.beginTrace(action, DeviceId("device-1"))
            .routesEvaluated(routes)
            .result(TransportResult.Success)
            .complete()

        assertEquals(2, entry.routesEvaluated.size)
        assertTrue(entry.routesEvaluated[0].isSelected)
        assertFalse(entry.routesEvaluated[1].isSelected)
    }

    @Test
    fun `flight builder records error message`() {
        val action = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-1"))
        val entry = recorder.beginTrace(action, DeviceId("device-1"))
            .result(TransportResult.AdapterError)
            .error("Device offline")
            .complete()

        assertEquals("Device offline", entry.errorMessage)
        assertFalse(entry.isSuccessful)
    }

    @Test
    fun `totalLatencyMs computes correctly`() {
        val action = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-1"))
        val entry = recorder.beginTrace(action, DeviceId("device-1"))
            .result(TransportResult.Success)
            .complete()

        assertTrue(entry.totalLatencyMs >= 0L)
    }

    @Test
    fun `flight entry traceId is unique`() {
        val action = UniversalAction.PowerOn(targetDeviceId = DeviceId("device-1"))
        val entry1 = recorder.beginTrace(action, DeviceId("device-1"))
            .result(TransportResult.Success).complete()
        val entry2 = recorder.beginTrace(action, DeviceId("device-1"))
            .result(TransportResult.Success).complete()

        assertTrue(entry1.traceId != entry2.traceId)
    }

}
