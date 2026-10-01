package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcreteLocalRuleEngineTest {

    private val tvId = DeviceId("tv-living")

    private class RecordingDispatcher : UniversalActionDispatcher {
        val dispatched = mutableListOf<Pair<DeviceId, UniversalAction>>()

        override suspend fun dispatch(deviceId: DeviceId, action: UniversalAction): Boolean {
            dispatched.add(deviceId to action)
            return true
        }
    }

    private class FakeStateProvider(
        private val states: Map<DeviceId, DeviceState> = emptyMap(),
        private val connected: Map<DeviceId, Boolean> = emptyMap()
    ) : StateProvider {
        override suspend fun getState(deviceId: DeviceId): DeviceState? = states[deviceId]

        override suspend fun getCapabilities(deviceId: DeviceId): Set<com.elysium.nexus.fabric.canonical.Capability>? = null

        override suspend fun getLastSeen(deviceId: DeviceId): Long = 0L

        override suspend fun isConnected(deviceId: DeviceId): Boolean = connected[deviceId] ?: false
    }

    private fun rule(
        id: String = "rule-1",
        trigger: AutomationTrigger = AutomationTrigger.ManualTrigger,
        actions: List<AutomationAction> = emptyList(),
        cooldownMs: Long = 0L,
        maxPerDay: Int = Int.MAX_VALUE,
        enabled: Boolean = true
    ) = AutomationRule(
        id = id,
        name = id,
        trigger = trigger,
        actions = actions,
        cooldownMs = cooldownMs,
        maxExecutionsPerDay = maxPerDay,
        enabled = enabled
    )

    @Test
    fun `manual trigger rule never fires`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(
            RecordingDispatcher(),
            FakeStateProvider()
        )
        engine.addRule(rule(trigger = AutomationTrigger.ManualTrigger))

        assertTrue(engine.evaluate().isEmpty())
    }

    @Test
    fun `connectivity trigger fires when connected matches`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(
            RecordingDispatcher(),
            FakeStateProvider(connected = mapOf(tvId to true))
        )
        engine.addRule(
            rule(trigger = AutomationTrigger.ConnectivityTrigger(tvId, connected = true))
        )

        assertEquals(listOf("rule-1"), engine.evaluate().map { it.id })
    }

    @Test
    fun `connectivity trigger does not fire when mismatch`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(
            RecordingDispatcher(),
            FakeStateProvider(connected = mapOf(tvId to true))
        )
        engine.addRule(
            rule(trigger = AutomationTrigger.ConnectivityTrigger(tvId, connected = false))
        )

        assertTrue(engine.evaluate().isEmpty())
    }

    @Test
    fun `state trigger fires when state matches`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(
            RecordingDispatcher(),
            FakeStateProvider(states = mapOf(tvId to DeviceState.OnOff(true)))
        )
        engine.addRule(
            rule(
                trigger = AutomationTrigger.StateTrigger(
                    deviceId = tvId,
                    capability = "onOff",
                    stateMatches = DeviceState.OnOff(true)
                )
            )
        )

        assertEquals(1, engine.evaluate().size)
    }

    @Test
    fun `state trigger does not fire on mismatched state`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(
            RecordingDispatcher(),
            FakeStateProvider(states = mapOf(tvId to DeviceState.OnOff(false)))
        )
        engine.addRule(
            rule(
                trigger = AutomationTrigger.StateTrigger(
                    deviceId = tvId,
                    capability = "onOff",
                    stateMatches = DeviceState.OnOff(true)
                )
            )
        )

        assertTrue(engine.evaluate().isEmpty())
    }

    @Test
    fun `disabled rule never fires`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(
            RecordingDispatcher(),
            FakeStateProvider(connected = mapOf(tvId to true))
        )
        engine.addRule(
            rule(
                trigger = AutomationTrigger.ConnectivityTrigger(tvId, connected = true),
                enabled = false
            )
        )

        assertTrue(engine.evaluate().isEmpty())
    }

    @Test
    fun `cooldown blocks re-execution`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(RecordingDispatcher(), FakeStateProvider())
        val r = rule(cooldownMs = 60_000L)
        engine.addRule(r)

        engine.recordExecution(r.id, true)
        assertTrue(engine.isInCooldown(r))
    }

    @Test
    fun `cooldown expired allows execution`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(RecordingDispatcher(), FakeStateProvider())
        val r = rule(cooldownMs = 1L)
        engine.addRule(r)

        engine.recordExecution(r.id, true)
        kotlinx.coroutines.delay(10)
        assertFalse(engine.isInCooldown(r))
    }

    @Test
    fun `zero cooldown never blocks`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(RecordingDispatcher(), FakeStateProvider())
        val r = rule(cooldownMs = 0L)
        engine.addRule(r)

        engine.recordExecution(r.id, true)
        assertFalse(engine.isInCooldown(r))
    }

    @Test
    fun `daily limit blocks further executions`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(RecordingDispatcher(), FakeStateProvider())
        val r = rule(maxPerDay = 2)
        engine.addRule(r)

        engine.recordExecution(r.id, true)
        engine.recordExecution(r.id, true)
        assertTrue(engine.hasExceededDailyLimit(r))
    }

    @Test
    fun `daily limit allows up to max`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(RecordingDispatcher(), FakeStateProvider())
        val r = rule(maxPerDay = 3)
        engine.addRule(r)

        engine.recordExecution(r.id, true)
        engine.recordExecution(r.id, true)
        assertFalse(engine.hasExceededDailyLimit(r))
    }

@Test
    fun `execute dispatches actions`() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val engine = ConcreteLocalRuleEngine(dispatcher, FakeStateProvider())
        val r = rule(
            trigger = AutomationTrigger.ManualTrigger,
            actions = listOf(AutomationAction.ExecuteAction(tvId, UniversalAction.Mute(tvId)))
        )

        engine.execute(r)

        assertEquals(1, dispatcher.dispatched.size)
        assertTrue(dispatcher.dispatched.first().second is UniversalAction.Mute)
    }

    @Test
    fun `execute delay action waits`() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val engine = ConcreteLocalRuleEngine(dispatcher, FakeStateProvider())
        val r = rule(
            trigger = AutomationTrigger.ManualTrigger,
            actions = listOf(
                AutomationAction.ExecuteAction(tvId, UniversalAction.Mute(tvId)),
                AutomationAction.Delay(20L),
                AutomationAction.ExecuteAction(tvId, UniversalAction.Mute(tvId))
            )
        )

        engine.execute(r)

        assertEquals(2, dispatcher.dispatched.size)
    }

    @Test
    fun `getDueRules returns eligible rules`() = runBlocking {
        val engine = ConcreteLocalRuleEngine(
            RecordingDispatcher(),
            FakeStateProvider(connected = mapOf(tvId to true))
        )
        engine.addRule(
            rule(trigger = AutomationTrigger.ConnectivityTrigger(tvId, connected = true))
        )

        assertEquals(1, engine.getDueRules().size)
    }
}