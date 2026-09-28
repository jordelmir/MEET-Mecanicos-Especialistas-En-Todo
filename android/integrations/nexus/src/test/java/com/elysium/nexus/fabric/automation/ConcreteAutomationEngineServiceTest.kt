package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.DeviceState
import com.elysium.nexus.fabric.canonical.UniversalAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcreteAutomationEngineServiceTest {

    private val tvId = DeviceId("tv-living")

    private class RecordingDispatcher(
        private val succeed: (UniversalAction) -> Boolean = { true }
    ) : UniversalActionDispatcher {
        val dispatched = mutableListOf<Pair<DeviceId, UniversalAction>>()

        override suspend fun dispatch(deviceId: DeviceId, action: UniversalAction): Boolean {
            dispatched.add(deviceId to action)
            return succeed(action)
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

    @Test
    fun `scene executes all steps on success`() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val engine = ConcreteAutomationEngineService(dispatcher)

        val scene = Scene(
            name = "movie",
            steps = listOf(
                ActionStep(targetDeviceId = tvId, action = UniversalAction.PowerOn(tvId)),
                ActionStep(targetDeviceId = tvId, action = UniversalAction.VolumeUp(tvId))
            )
        )

        val result = engine.executeScene(scene)

        assertTrue(result is SceneExecutionResult.Success)
        assertEquals(2, (result as SceneExecutionResult.Success).completedSteps)
        assertEquals(2, dispatcher.dispatched.size)
    }

    @Test
    fun `scene precondition failure rolls back completed steps`() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val engine = ConcreteAutomationEngineService(dispatcher)

        val scene = Scene(
            name = "guarded",
            steps = listOf(
                ActionStep(
                    targetDeviceId = tvId,
                    action = UniversalAction.PowerOn(tvId),
                    rollbackAction = UniversalAction.PowerOff(tvId)
                ),
                ActionStep(
                    targetDeviceId = tvId,
                    action = UniversalAction.Mute(tvId),
                    precondition = StatePredicate.DeviceState(
                        deviceId = tvId,
                        expectedState = DeviceState.OnOff(true)
                    )
                )
            )
        )

        val result = engine.executeScene(scene)

        assertTrue(result is SceneExecutionResult.PreconditionFailed)
        // First step executed, then rolled back
        assertTrue(dispatcher.dispatched[0].second is UniversalAction.PowerOn)
        assertTrue(dispatcher.dispatched[1].second is UniversalAction.PowerOff)
    }

    @Test
    fun `scene dispatch failure triggers rollback`() = runBlocking {
        val dispatcher = RecordingDispatcher { action ->
            action !is UniversalAction.VolumeUp
        }
        val engine = ConcreteAutomationEngineService(dispatcher)

        val scene = Scene(
            name = "fail",
            steps = listOf(
                ActionStep(
                    targetDeviceId = tvId,
                    action = UniversalAction.PowerOn(tvId),
                    rollbackAction = UniversalAction.PowerOff(tvId)
                ),
                ActionStep(targetDeviceId = tvId, action = UniversalAction.VolumeUp(tvId))
            )
        )

        val result = engine.executeScene(scene)

        assertTrue(result is SceneExecutionResult.PartialFailure)
        val partial = result as SceneExecutionResult.PartialFailure
        assertEquals(1, partial.completedSteps)
        assertEquals(true, partial.rolledBack)
        // Rollback executed after failure
        assertTrue(dispatcher.dispatched.last().second is UniversalAction.PowerOff)
    }

    @Test
    fun `scene success condition timeout returns Timeout and rolls back`() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val engine = ConcreteAutomationEngineService(
            dispatcher,
            stateProvider = FakeStateProvider(emptyMap())
        )

        val scene = Scene(
            name = "timeout",
            steps = listOf(
                ActionStep(
                    targetDeviceId = tvId,
                    action = UniversalAction.PowerOn(tvId),
                    rollbackAction = UniversalAction.PowerOff(tvId),
                    successCondition = StatePredicate.DeviceState(
                        deviceId = tvId,
                        expectedState = DeviceState.OnOff(true)
                    ),
                    timeoutMs = 250L
                )
            )
        )

        val result = engine.executeScene(scene)

        assertTrue(result is SceneExecutionResult.Timeout)
        // Rollback dispatched after timeout
        assertTrue(dispatcher.dispatched.last().second is UniversalAction.PowerOff)
    }

    @Test
    fun `macro with rollbackOnFailure false does not rollback`() = runBlocking {
        val dispatcher = RecordingDispatcher { action -> action !is UniversalAction.VolumeUp }
        val engine = ConcreteAutomationEngineService(dispatcher)

        val macro = MacroTransaction(
            name = "no-rollback",
            rollbackOnFailure = false,
            steps = listOf(
                ActionStep(
                    targetDeviceId = tvId,
                    action = UniversalAction.PowerOn(tvId),
                    rollbackAction = UniversalAction.PowerOff(tvId)
                ),
                ActionStep(targetDeviceId = tvId, action = UniversalAction.VolumeUp(tvId))
            )
        )

        val result = engine.executeMacro(macro)

        assertTrue(result is SceneExecutionResult.PartialFailure)
        val partial = result as SceneExecutionResult.PartialFailure
        assertEquals(false, partial.rolledBack)
        // No rollback dispatched: PowerOn + VolumeUp only, no PowerOff
        assertEquals(2, dispatcher.dispatched.size)
        assertEquals(false, dispatcher.dispatched.any { it.second is UniversalAction.PowerOff })
    }

    @Test
    fun `manual triggers never auto-fire in evaluateRules`() = runBlocking {
        val engine = ConcreteAutomationEngineService(RecordingDispatcher())

        val rule = AutomationRule(
            name = "manual",
            trigger = AutomationTrigger.ManualTrigger,
            actions = listOf(AutomationAction.Notify("t", "b"))
        )
        engine.addRule(rule)

        val due = engine.evaluateRules()

        assertTrue(due.isEmpty())
    }

    @Test
    fun `connectivity trigger rule fires when device connected`() = runBlocking {
        val engine = ConcreteAutomationEngineService(
            RecordingDispatcher(),
            stateProvider = FakeStateProvider(connected = mapOf(tvId to true))
        )
        val rule = AutomationRule(
            id = "c1",
            name = "connectivity-rule",
            trigger = AutomationTrigger.ConnectivityTrigger(tvId, connected = true),
            actions = listOf(AutomationAction.ExecuteAction(tvId, UniversalAction.Mute(tvId)))
        )
        engine.addRule(rule)

        val due = engine.evaluateRules()

        assertEquals(listOf("c1"), due.map { it.id })
    }

    @Test
    fun `connectivity trigger does not fire when provider says connected false`() = runBlocking {
        val engine = ConcreteAutomationEngineService(
            RecordingDispatcher(),
            stateProvider = FakeStateProvider(connected = mapOf(tvId to false))
        )
        engine.addRule(
            AutomationRule(
                id = "c2",
                name = "conn-false",
                trigger = AutomationTrigger.ConnectivityTrigger(tvId, connected = true),
                actions = listOf(AutomationAction.Notify("t", "b"))
            )
        )

        val due = engine.evaluateRules()

        assertTrue(due.isEmpty())
    }

    @Test
    fun `disabled rule never fires`() = runBlocking {
        val engine = ConcreteAutomationEngineService(
            RecordingDispatcher(),
            stateProvider = FakeStateProvider(connected = mapOf(tvId to true))
        )
        engine.addRule(
            AutomationRule(
                id = "c3",
                name = "disabled-rule",
                trigger = AutomationTrigger.ConnectivityTrigger(tvId, connected = true),
                actions = listOf(AutomationAction.Notify("t", "b")),
                enabled = false
            )
        )

        val due = engine.evaluateRules()

        assertTrue(due.isEmpty())
    }

    @Test
    fun `history starts empty`() = runBlocking {
        val engine = ConcreteAutomationEngineService(RecordingDispatcher())

        val history = engine.observeHistory().first()

        assertTrue(history.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // V06-P19: per-step transaction semantics — policy-gated retries + audit
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `idempotent step retries when first attempt fails`() = runBlocking {
        var calls = 0
        val dispatcher = RecordingDispatcher { _ ->
            calls++
            calls >= 3 // succeed on the 3rd attempt
        }
        val engine = ConcreteAutomationEngineService(dispatcher)

        val scene = Scene(
            name = "retry-mute",
            steps = listOf(
                ActionStep(
                    targetDeviceId = tvId,
                    action = UniversalAction.Mute(tvId), // IDEMPOTENT_SAFE
                    retryCount = 3,
                    retryDelayMs = 1L
                )
            )
        )

        val result = engine.executeScene(scene)

        assertTrue(result is SceneExecutionResult.Success)
        assertEquals(3, calls)
    }

    @Test
    fun `non-idempotent step never blind-retries`() = runBlocking {
        var calls = 0
        val dispatcher = RecordingDispatcher { _ ->
            calls++
            false // always fails
        }
        val engine = ConcreteAutomationEngineService(dispatcher)

        val scene = Scene(
            name = "no-retry-vol-up",
            steps = listOf(
                ActionStep(
                    targetDeviceId = tvId,
                    action = UniversalAction.VolumeUp(tvId), // NON_IDEMPOTENT
                    retryCount = 5,
                    retryDelayMs = 1L
                )
            )
        )

        val result = engine.executeScene(scene)

        assertTrue(result is SceneExecutionResult.PartialFailure)
        assertEquals("blind retry on VolumeUp would double the volume", 1, calls)
    }

    @Test
    fun `destructive custom step never blind-retries`() = runBlocking {
        var calls = 0
        val dispatcher = RecordingDispatcher { _ ->
            calls++
            false
        }
        val engine = ConcreteAutomationEngineService(dispatcher)

        val scene = Scene(
            name = "no-retry-reset",
            steps = listOf(
                ActionStep(
                    targetDeviceId = tvId,
                    action = UniversalAction.Custom(tvId, key = "factory_reset"),
                    retryCount = 4,
                    retryDelayMs = 1L
                )
            )
        )

        val result = engine.executeScene(scene)

        assertTrue(result is SceneExecutionResult.PartialFailure)
        assertEquals(1, calls)
    }

    @Test
    fun `scene run is recorded in history`() = runBlocking {
        val engine = ConcreteAutomationEngineService(RecordingDispatcher())

        engine.executeScene(
            Scene(
                name = "movie",
                steps = listOf(
                    ActionStep(targetDeviceId = tvId, action = UniversalAction.PowerOn(tvId))
                )
            )
        )

        val history = engine.observeHistory().first()
        assertEquals(1, history.size)
        assertEquals("SCENE:movie", history[0].ruleId)
        assertTrue(history[0].success)
        assertEquals(1, history[0].actionsExecuted)
    }

    @Test
    fun `failed macro run is recorded with error`() = runBlocking {
        val dispatcher = RecordingDispatcher { action -> action !is UniversalAction.VolumeUp }
        val engine = ConcreteAutomationEngineService(dispatcher)

        engine.executeMacro(
            MacroTransaction(
                name = "no-rollback",
                rollbackOnFailure = false,
                steps = listOf(
                    ActionStep(targetDeviceId = tvId, action = UniversalAction.VolumeUp(tvId))
                )
            )
        )

        val history = engine.observeHistory().first()
        assertEquals(1, history.size)
        assertEquals("MACRO:no-rollback", history[0].ruleId)
        assertTrue(!history[0].success)
        assertTrue(history[0].error != null)
    }
}