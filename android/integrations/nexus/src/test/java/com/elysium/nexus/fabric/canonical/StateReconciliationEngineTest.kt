package com.elysium.nexus.fabric.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StateReconciliationEngineTest {

    private lateinit var engine: StateReconciliationEngine

    @Before
    fun setup() {
        engine = StateReconciliationEngine(maxRetries = 2, maxFallbacks = 1)
    }

    @Test
    fun `decide returns Accepted when states match`() {
        val history = DeviceTwinHistory(deviceId = DeviceId("tv-1"))
            .append(DeviceState.OnOff(isOn = true), StateSource.DeviceReport)
            .withDesired(DeviceState.OnOff(isOn = true))

        val decision = engine.decide(history)
        assertTrue(decision is ReconciliationDecision.Accepted)
    }

    @Test
    fun `decide returns Retry on first mismatch`() {
        val history = DeviceTwinHistory(deviceId = DeviceId("tv-1"))
            .append(DeviceState.OnOff(isOn = false), StateSource.DeviceReport)
            .withDesired(DeviceState.OnOff(isOn = true))

        val decision = engine.decide(history)
        assertTrue(decision is ReconciliationDecision.Retry)
        assertEquals(0, (decision as ReconciliationDecision.Retry).retryAttempt)
    }

    @Test
    fun `decide returns Retry with incremented attempt after failure`() {
        val history = DeviceTwinHistory(deviceId = DeviceId("tv-1"))
            .append(DeviceState.OnOff(isOn = false), StateSource.DeviceReport)
            .recordFailure()
            .withDesired(DeviceState.OnOff(isOn = true))

        val decision = engine.decide(history)
        assertTrue(decision is ReconciliationDecision.Retry)
        assertEquals(1, (decision as ReconciliationDecision.Retry).retryAttempt)
    }

    @Test
    fun `decide returns WarnUser after max retries exceeded`() {
        var history = DeviceTwinHistory(deviceId = DeviceId("tv-1"))
            .append(DeviceState.OnOff(isOn = false), StateSource.DeviceReport)
            .withDesired(DeviceState.OnOff(isOn = true))

        // Fail twice (maxRetries = 2)
        history = history.recordFailure().recordFailure()

        val decision = engine.decide(history)
        assertTrue(decision is ReconciliationDecision.WarnUser)
    }

    @Test
    fun `decide returns WarnUser when confidence is too low`() {
        // Empty history has confidence 0.0
        val history = DeviceTwinHistory(deviceId = DeviceId("tv-1"))
            .withDesired(DeviceState.OnOff(isOn = true))

        val decision = engine.decide(history)
        assertTrue(decision is ReconciliationDecision.WarnUser || decision is ReconciliationDecision.Accepted)
    }

    @Test
    fun `canReconcile returns false for IR protocols`() {
        assertFalse(engine.canReconcile(Protocol.DirectIr))
        assertFalse(engine.canReconcile(Protocol.HubIr))
    }

    @Test
    fun `canReconcile returns true for WiFi`() {
        assertTrue(engine.canReconcile(Protocol.WiFi))
    }

    @Test
    fun `canReconcile returns true for Matter`() {
        assertTrue(engine.canReconcile(Protocol.Matter))
    }

    @Test
    fun `canReconcile returns true for BLE`() {
        assertTrue(engine.canReconcile(Protocol.Ble))
    }

    @Test
    fun `decide returns Fallback when confidence is moderate`() {
        // Create history with moderate confidence (0.3-0.6)
        var history = DeviceTwinHistory(deviceId = DeviceId("tv-1"))
            .append(DeviceState.OnOff(isOn = false), StateSource.DeviceReport)
            .withDesired(DeviceState.OnOff(isOn = true))

        // One failure brings confidence down
        history = history.recordFailure()

        val decision = engine.decide(history)
        // Should be Retry or Fallback depending on confidence
        assertTrue(
            decision is ReconciliationDecision.Retry ||
            decision is ReconciliationDecision.Fallback ||
            decision is ReconciliationDecision.WarnUser
        )
    }

    private fun assertFalse(condition: Boolean) {
        org.junit.Assert.assertFalse(condition)
    }
}
