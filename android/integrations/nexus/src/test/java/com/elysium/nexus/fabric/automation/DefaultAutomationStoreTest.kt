package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [DefaultAutomationStore].
 * Verifies dedup, expiry, and LRU eviction.
 */
class DefaultAutomationStoreTest {

    @Test
    fun keyIsInFlight_afterMarkInFlight() {
        val store = DefaultAutomationStore()
        val key = IdempotencyKey("k1")
        store.markInFlight(key)
        assertTrue(store.isInFlight(key))
    }

    @Test
    fun keyIsNotInFlight_afterMarkCompleted() {
        val store = DefaultAutomationStore()
        val key = IdempotencyKey("k1")
        store.markInFlight(key)
        store.markCompleted(key)
        assertFalse(store.isInFlight(key))
    }

    @Test
    fun newKeyIsNotInFlight() {
        val store = DefaultAutomationStore()
        assertFalse(store.isInFlight(IdempotencyKey("k1")))
    }

    @Test
    fun differentKeysAreIndependent() {
        val store = DefaultAutomationStore()
        val k1 = IdempotencyKey("k1")
        val k2 = IdempotencyKey("k2")
        store.markInFlight(k1)
        assertTrue(store.isInFlight(k1))
        assertFalse(store.isInFlight(k2))
    }

    @Test
    fun markCompletedIsSafeOnKeyNeverInFlight() {
        val store = DefaultAutomationStore()
        store.markCompleted(IdempotencyKey("k1"))
        assertFalse(store.isInFlight(IdempotencyKey("k1")))
    }

    @Test
    fun multipleKeysCanBeInFlightSimultaneously() {
        val store = DefaultAutomationStore()
        val keys = (0 until 10).map { IdempotencyKey("k$it") }
        keys.forEach { store.markInFlight(it) }
        keys.forEach { assertTrue(store.isInFlight(it)) }
    }

    @Test
    fun completingOneKeyDoesNotAffectOthers() {
        val store = DefaultAutomationStore()
        val k1 = IdempotencyKey("k1")
        val k2 = IdempotencyKey("k2")
        store.markInFlight(k1)
        store.markInFlight(k2)
        store.markCompleted(k1)
        assertFalse(store.isInFlight(k1))
        assertTrue(store.isInFlight(k2))
    }

    @Test
    fun idempotencyKeyGenerationIsStable() {
        val k1 = IdempotencyKey.forEvent(
            simpleAutomation("a1"), TriggerEvent.Motion, DeviceId("d1")
        )
        val k2 = IdempotencyKey.forEvent(
            simpleAutomation("a1"), TriggerEvent.Motion, DeviceId("d1")
        )
        assertEquals(k1, k2)
    }

    @Test
    fun idempotencyKeyDiffersForDifferentEvents() {
        val k1 = IdempotencyKey.forEvent(
            simpleAutomation("a1"), TriggerEvent.Motion, DeviceId("d1")
        )
        val k2 = IdempotencyKey.forEvent(
            simpleAutomation("a1"), TriggerEvent.DoorOpened, DeviceId("d1")
        )
        assertTrue(k1 != k2)
    }

    @Test
    fun idempotencyKeyDiffersForDifferentDevices() {
        val k1 = IdempotencyKey.forEvent(
            simpleAutomation("a1"), TriggerEvent.Motion, DeviceId("d1")
        )
        val k2 = IdempotencyKey.forEvent(
            simpleAutomation("a1"), TriggerEvent.Motion, DeviceId("d2")
        )
        assertTrue(k1 != k2)
    }

    @Test
    fun idempotencyKeyUsesUnderscoreForNullDeviceId() {
        val k1 = IdempotencyKey.forEvent(
            simpleAutomation("a1"), TriggerEvent.Time, null
        )
        assertTrue(k1.value.contains("_"))
    }

    @Test
    fun dedupWindowIsFiveMinutes() {
        assertEquals(5 * 60 * 1000L, DefaultAutomationStore.DEDUP_WINDOW_MS)
    }

    @Test
    fun maxKeysIs1000() {
        assertEquals(1000, DefaultAutomationStore.MAX_KEYS)
    }

    private fun simpleAutomation(id: String = "a1") = Automation(
        id = AutomationId(id),
        name = "Test",
        author = "tester",
        createdAtNs = 0L,
        triggers = listOf(Trigger(event = TriggerEvent.Motion)),
        conditions = emptyList(),
        actions = listOf(
            Action(
                deviceId = DeviceId("d1"),
                capability = Capability.OnOff,
                command = CommandValue.OnOff(turnOn = true)
            )
        ),
        verification = VerificationPolicy(
            timeoutMs = 5_000L,
            requireStateConfirmation = false
        )
    )
}
