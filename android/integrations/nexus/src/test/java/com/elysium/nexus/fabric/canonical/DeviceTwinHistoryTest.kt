package com.elysium.nexus.fabric.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceTwinHistoryTest {

    private val deviceId = DeviceId("tv-1")

    @Before
    fun setup() {
    }

    @Test
    fun `empty history has no latest`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
        assertNull(history.latest)
        assertEquals(DeviceState.Unknown, history.reportedState)
    }

    @Test
    fun `append adds snapshot`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
            .append(DeviceState.OnOff(isOn = true), StateSource.DeviceReport)

        assertEquals(1, history.snapshots.size)
        assertEquals(DeviceState.OnOff(isOn = true), history.reportedState)
    }

    @Test
    fun `append evicts oldest when full`() {
        var history = DeviceTwinHistory(deviceId = deviceId)
        repeat(DeviceTwinHistory.MAX_SNAPSHOTS + 5) { i ->
            history = history.append(
                DeviceState.OnOff(isOn = i % 2 == 0),
                StateSource.DeviceReport
            )
        }

        assertEquals(DeviceTwinHistory.MAX_SNAPSHOTS, history.snapshots.size)
    }

    @Test
    fun `confirmLatest marks last snapshot as confirmed`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
            .append(DeviceState.OnOff(isOn = true), StateSource.DeviceReport)
            .confirmLatest()

        assertTrue(history.latest?.isConfirmed == true)
        assertEquals(DeviceState.OnOff(isOn = true), history.lastConfirmedState)
    }

    @Test
    fun `confirmLatest on empty history is no-op`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
        val result = history.confirmLatest()
        assertEquals(history, result)
    }

    @Test
    fun `recordFailure increments consecutive failures`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
            .append(DeviceState.OnOff(isOn = true), StateSource.DeviceReport)
            .recordFailure()

        assertEquals(1, history.latest?.consecutiveFailures)
    }

    @Test
    fun `isStale is true when desired differs from reported`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
            .append(DeviceState.OnOff(isOn = false), StateSource.DeviceReport)
            .withDesired(DeviceState.OnOff(isOn = true))

        assertTrue(history.isStale)
    }

    @Test
    fun `isStale is false when desired matches reported`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
            .append(DeviceState.OnOff(isOn = true), StateSource.DeviceReport)
            .withDesired(DeviceState.OnOff(isOn = true))

        assertFalse(history.isStale)
    }

    @Test
    fun `isStale is false when desired is Unknown`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
            .append(DeviceState.OnOff(isOn = true), StateSource.DeviceReport)

        assertFalse(history.isStale)
    }

    @Test
    fun `confidence is 0 for empty history`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
        assertEquals(0.0, history.confidence, 0.001)
    }

    @Test
    fun `confidence is high for recent confirmed snapshot`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
            .append(DeviceState.OnOff(isOn = true), StateSource.DeviceReport, isConfirmed = true)

        assertTrue(history.confidence > 0.8)
    }

    @Test
    fun `confidence decreases with consecutive failures`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
            .append(DeviceState.OnOff(isOn = true), StateSource.DeviceReport)
            .recordFailure()
            .recordFailure()
            .recordFailure()

        // recencyScore=1.0, confirmedBonus=0.0, failurePenalty=0.3 → total=0.7
        assertTrue(history.confidence < 0.8)
        assertTrue(history.confidence > 0.5)
    }

    @Test
    fun `withDesired updates desired state`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
            .withDesired(DeviceState.OnOff(isOn = true))

        assertEquals(DeviceState.OnOff(isOn = true), history.desiredState)
    }

    @Test
    fun `trimmed limits snapshot count`() {
        var history = DeviceTwinHistory(deviceId = deviceId)
        repeat(10) { i ->
            history = history.append(
                DeviceState.OnOff(isOn = i % 2 == 0),
                StateSource.DeviceReport
            )
        }

        val trimmed = history.trimmed(maxSnapshots = 3)
        assertEquals(3, trimmed.snapshots.size)
    }

    @Test
    fun `state source is recorded in snapshot`() {
        val history = DeviceTwinHistory(deviceId = deviceId)
            .append(DeviceState.OnOff(isOn = true), StateSource.Automation)

        assertEquals(StateSource.Automation, history.latest?.source)
    }

    @Test
    fun `metadata is recorded in snapshot`() {
        val metadata = mapOf("protocol" to "WiFi", "latencyMs" to "42")
        val history = DeviceTwinHistory(deviceId = deviceId)
            .append(DeviceState.OnOff(isOn = true), StateSource.DeviceReport, metadata = metadata)

        assertEquals("WiFi", history.latest?.metadata?.get("protocol"))
    }
}
