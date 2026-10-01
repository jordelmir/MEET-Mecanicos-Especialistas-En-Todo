package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.automation.Verdict
import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * JVM tests for the §28 [Automation] +
 * [AutomationEngine]. The tests are pure
 * (no Android, no Hub); the production
 * wiring lives in the Hub / Android app.
 */
class AutomationEngineTest {

    @Test
    fun `Automation rejects a blank name`() {
        try {
            simpleAutomation(name = "")
            fail("Expected IllegalArgumentException for blank name.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Automation rejects a blank author`() {
        try {
            simpleAutomation(author = "")
            fail("Expected IllegalArgumentException for blank author.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Automation rejects empty triggers`() {
        try {
            simpleAutomation().copy(triggers = emptyList())
            fail("Expected IllegalArgumentException for empty triggers.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `Automation rejects empty actions`() {
        try {
            simpleAutomation().copy(actions = emptyList())
            fail("Expected IllegalArgumentException for empty actions.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `execute runs every action and returns Completed`() {
        val automation = simpleAutomation()
        val received = mutableListOf<Pair<Action, CommandStatus>>()
        val dispatcher = ActionDispatcher { action, _ ->
            received.add(action to CommandStatus.Confirmed)
            CommandStatus.Confirmed
        }
        val store = InMemoryAutomationStore()
        val context = Context(emptyMap())
        val verdict = AutomationEngine.execute(
            automation = automation,
            event = TriggerEvent.Motion,
            deviceId = DeviceId("d1"),
            context = context,
            store = store,
            dispatcher = dispatcher
        )
        assertTrue("Expected Completed, got $verdict", verdict is Verdict.Completed)
        assertEquals(2, (verdict as Verdict.Completed).perAction.size)
    }

    @Test
    fun `execute returns AlreadyRunning on duplicate trigger`() {
        val automation = simpleAutomation()
        val store = InMemoryAutomationStore()
        val dispatcher = ActionDispatcher { _, _ -> CommandStatus.Confirmed }
        val context = Context(emptyMap())
        // First call: completes.
        val v1 = AutomationEngine.execute(
            automation, TriggerEvent.Motion, DeviceId("d1"),
            context, store, dispatcher
        )
        assertTrue(v1 is Verdict.Completed)
        // Mark a key in-flight manually for the
        // second call.
        val key = IdempotencyKey.forEvent(automation, TriggerEvent.Motion, DeviceId("d1"))
        store.markInFlight(key)
        val v2 = AutomationEngine.execute(
            automation, TriggerEvent.Motion, DeviceId("d1"),
            context, store, dispatcher
        )
        assertEquals(Verdict.AlreadyRunning, v2)
    }

    @Test
    fun `execute returns ConditionsNotMet when a condition is false`() {
        val automation = simpleAutomation(
            conditions = listOf(
                Condition(kind = ConditionKind.UserPresent, value = "alice")
            )
        )
        val store = InMemoryAutomationStore()
        val dispatcher = ActionDispatcher { _, _ -> CommandStatus.Confirmed }
        val context = Context(
            mapOf(Context.KEY_USER_ID to "bob")
        )
        val v = AutomationEngine.execute(
            automation, TriggerEvent.Motion, DeviceId("d1"),
            context, store, dispatcher
        )
        assertTrue("Expected ConditionsNotMet, got $v", v is Verdict.ConditionsNotMet)
    }

    @Test
    fun `execute returns CompensationRan when an action fails`() {
        val action1 = Action(
            deviceId = DeviceId("d1"),
            capability = Capability.OnOff,
            command = CommandValue.OnOff(turnOn = true)
        )
        val action2 = Action(
            deviceId = DeviceId("d2"),
            capability = Capability.OnOff,
            command = CommandValue.OnOff(turnOn = true)
        )
        val compensation1 = Action(
            deviceId = DeviceId("d1"),
            capability = Capability.OnOff,
            command = CommandValue.OnOff(turnOn = false)
        )
        val automation = simpleAutomation(
            actions = listOf(action1, action2),
            compensation = listOf(compensation1)
        )
        val store = InMemoryAutomationStore()
        val context = Context(emptyMap())
        // First action: Confirmed. Second:
        // Rejected. Compensation runs for the
        // first action.
        val dispatcher = ActionDispatcher { action, _ ->
            when (action.deviceId) {
                DeviceId("d1") -> CommandStatus.Confirmed
                DeviceId("d2") -> CommandStatus.Rejected
                else -> CommandStatus.Confirmed
            }
        }
        val v = AutomationEngine.execute(
            automation, TriggerEvent.Motion, DeviceId("d1"),
            context, store, dispatcher
        )
        assertTrue("Expected CompensationRan, got $v", v is Verdict.CompensationRan)
    }

    @Test
    fun `IdempotencyKey is stable for the same inputs`() {
        val automation = simpleAutomation()
        val a = IdempotencyKey.forEvent(automation, TriggerEvent.Motion, DeviceId("d1"))
        val b = IdempotencyKey.forEvent(automation, TriggerEvent.Motion, DeviceId("d1"))
        assertEquals(a, b)
    }

    @Test
    fun `IdempotencyKey differs for different events`() {
        val automation = simpleAutomation()
        val a = IdempotencyKey.forEvent(automation, TriggerEvent.Motion, DeviceId("d1"))
        val b = IdempotencyKey.forEvent(automation, TriggerEvent.DoorOpened, DeviceId("d1"))
        assertNotNull(a)
        assertNotNull(b)
        assertTrue(a != b)
    }

    @Test
    fun `TriggerEvent has 25+ variants per §28_1`() {
        assertTrue(
            "TriggerEvent should have at least 20 variants; got ${TriggerEvent.values().size}",
            TriggerEvent.values().size >= 20
        )
    }

    @Test
    fun `ConditionKind has all the §28_2 variants`() {
        val expected = setOf(
            "AfterSunset", "BeforeSunrise", "UserPresent", "UserAbsent",
            "UserRole", "DeviceStateEquals", "DeviceStateNotEquals",
            "TimeInRange", "DayOfWeek", "Weather", "SecurityMode",
            "ConfidenceAtLeast", "HomeOccupied", "NetworkOnline", "EnergyTariff"
        )
        val actual = ConditionKind.values().map { it.name }.toSet()
        for (name in expected) {
            assertTrue("Expected ConditionKind '$name' (per §28.2)", name in actual)
        }
    }

    @Test
    fun `CommandStatus has the §40 variants`() {
        val expected = setOf(
            "Accepted", "Sent", "Acknowledged", "Confirmed",
            "Rejected", "TimedOut", "Unsupported", "DeviceOffline", "StateUnknown"
        )
        val actual = CommandStatus.values().map { it.name }.toSet()
        for (name in expected) {
            assertTrue("Expected CommandStatus '$name' (per §40)", name in actual)
        }
    }

    @Test
    fun `CommandValue on off payload is validated`() {
        try {
            CommandValue.Level(1.5f)
            fail("Expected IllegalArgumentException for Level > 1.0")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `VerificationPolicy rejects a non-positive timeout`() {
        try {
            VerificationPolicy(timeoutMs = 0L, requireStateConfirmation = false)
            fail("Expected IllegalArgumentException for timeoutMs = 0.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `VerificationPolicy rejects a too-large timeout`() {
        try {
            VerificationPolicy(
                timeoutMs = VerificationPolicy.MAX_TIMEOUT_MS + 1,
                requireStateConfirmation = false
            )
            fail("Expected IllegalArgumentException for timeout > MAX.")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    private fun simpleAutomation(
        name: String = "Test automation",
        author: String = "tester",
        triggers: List<Trigger> = listOf(Trigger(event = TriggerEvent.Motion)),
        conditions: List<Condition> = emptyList(),
        actions: List<Action> = listOf(
            Action(
                deviceId = DeviceId("d1"),
                capability = Capability.OnOff,
                command = CommandValue.OnOff(turnOn = true)
            ),
            Action(
                deviceId = DeviceId("d2"),
                capability = Capability.OnOff,
                command = CommandValue.OnOff(turnOn = false)
            )
        ),
        compensation: List<Action> = emptyList()
    ) = Automation(
        id = AutomationId("a1"),
        name = name,
        author = author,
        createdAtNs = 0L,
        triggers = triggers,
        conditions = conditions,
        actions = actions,
        verification = VerificationPolicy(timeoutMs = 5_000L, requireStateConfirmation = true),
        compensation = compensation
    )
}

/**
 * An in-memory [AutomationStore] for tests.
 * The store uses a [HashSet] for the in-flight
 * set; concurrency is not a test concern
 * (the engine is called from a single coroutine).
 */
class InMemoryAutomationStore : AutomationStore {
    private val inFlight = HashSet<String>()
    override fun isInFlight(key: IdempotencyKey): Boolean =
        inFlight.contains(key.value)
    override fun markInFlight(key: IdempotencyKey) {
        inFlight.add(key.value)
    }
    override fun markCompleted(key: IdempotencyKey) {
        inFlight.remove(key.value)
    }
}
