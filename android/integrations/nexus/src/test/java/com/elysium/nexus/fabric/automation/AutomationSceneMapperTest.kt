package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.Capability
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §28→§34 mapper: persisted Automation (CommandValue) → executable
 * MacroTransaction (UniversalAction). Verifies the mapping is total,
 * honest (no fabricated canonical commands), and policy-aware.
 */
class AutomationSceneMapperTest {

    private val tv = DeviceId("tv-1")
    private val lights = DeviceId("lights-1")

    private fun automation(name: String, vararg actions: Action): Automation =
        Automation(
            id = AutomationId("automation-$name"),
            name = name,
            author = "test",
            createdAtNs = System.nanoTime(),
            triggers = listOf(Trigger(TriggerEvent.Button)),
            conditions = emptyList(),
            actions = actions.toList(),
            verification = VerificationPolicy(5_000L, requireStateConfirmation = false)
        )

    private fun action(capability: Capability, command: CommandValue, device: DeviceId = tv) =
        Action(deviceId = device, capability = capability, command = command)

    // ── Total mapping: every CommandValue variant maps ─────

    @Test
    fun `onOff true maps to PowerOn`() {
        val mapped = AutomationSceneMapper.toUniversalAction(action(Capability.OnOff, CommandValue.OnOff(true)))
        assertTrue(mapped is UniversalAction.PowerOn)
    }

    @Test
    fun `onOff false maps to PowerOff`() {
        val mapped = AutomationSceneMapper.toUniversalAction(action(Capability.OnOff, CommandValue.OnOff(false)))
        assertTrue(mapped is UniversalAction.PowerOff)
    }

    @Test
    fun `media play maps to MediaPlay`() {
        val mapped = AutomationSceneMapper.toUniversalAction(action(Capability.MediaTransport, CommandValue.Media(true)))
        assertEquals(tv, (mapped as UniversalAction.MediaPlay).targetDeviceId)
    }

    @Test
    fun `media pause maps to MediaPause`() {
        val mapped = AutomationSceneMapper.toUniversalAction(action(Capability.MediaTransport, CommandValue.Media(false)))
        assertEquals(tv, (mapped as UniversalAction.MediaPause).targetDeviceId)
    }

    @Test
    fun `climate maps to SetTemperature`() {
        val mapped = AutomationSceneMapper.toUniversalAction(action(Capability.TargetTemperature, CommandValue.Climate(22.5f)))
        val set = mapped as UniversalAction.SetTemperature
        assertEquals(tv, set.targetDeviceId)
        assertEquals(22.5f, set.targetCelsius, 0.0001f)
    }

    @Test
    fun `level maps to Custom level key`() {
        val mapped = AutomationSceneMapper.toUniversalAction(action(Capability.Level, CommandValue.Level(0.42f)))
        assertTrue(mapped is UniversalAction.Custom)
        assertEquals("level", (mapped as UniversalAction.Custom).key)
        assertEquals("0.42", mapped.payload["value"])
    }

    @Test
    fun `noop maps to Custom noop`() {
        val mapped = AutomationSceneMapper.toUniversalAction(action(Capability.Custom, CommandValue.Noop))
        assertTrue(mapped is UniversalAction.Custom)
        assertEquals("noop", (mapped as UniversalAction.Custom).key)
    }

    // ── Macro building: policy-aware, honest audit ──────────

    @Test
    fun `macro carries policy timeout on every step`() {
        val automation = automation(
            "Movie Night",
            action(Capability.OnOff, CommandValue.OnOff(true), tv),
            action(Capability.Level, CommandValue.Level(0.2f), lights)
        ).copy(verification = VerificationPolicy(12_000L, requireStateConfirmation = false))

        val mapped = AutomationSceneMapper.toMacroTransaction(automation)
        assertEquals(2, mapped.stepCount)
        assertTrue(mapped.transaction.steps.all { it.timeoutMs == 12_000L })
        assertEquals(1, mapped.customKeyCount)
        assertEquals(1, mapped.classifiedCanonical())
        assertEquals(2, mapped.originalCount)
    }

    @Test
    fun `state confirmation produces success predicate`() {
        val automation = automation(
            "Confirmed Power",
            action(Capability.OnOff, CommandValue.OnOff(true), tv)
        ).copy(verification = VerificationPolicy(5_000L, requireStateConfirmation = true))

        val mapped = AutomationSceneMapper.toMacroTransaction(automation)
        val predicate = mapped.transaction.steps.single().successCondition
        assertNotNull(predicate)
        assertTrue(predicate is StatePredicate.CapabilityAvailable)
    }

    @Test
    fun `best effort policy has no success predicate`() {
        val mapped = AutomationSceneMapper.toMacroTransaction(
            automation("BestEffort", action(Capability.OnOff, CommandValue.OnOff(true), tv))
        )
        assertNull(mapped.transaction.steps.single().successCondition)
    }

    @Test
    fun `compensation enables rollbackOnFailure`() {
        val withComp = automation("WithComp", action(Capability.OnOff, CommandValue.OnOff(true), tv))
            .copy(compensation = listOf(action(Capability.OnOff, CommandValue.OnOff(false), tv)))
        assertTrue(AutomationSceneMapper.toMacroTransaction(withComp).transaction.rollbackOnFailure)

        val withoutComp = automation("NoComp", action(Capability.OnOff, CommandValue.OnOff(true), tv))
        assertFalse(AutomationSceneMapper.toMacroTransaction(withoutComp).transaction.rollbackOnFailure)
    }

    @Test
    fun `total mapping - every CommandValue variant has a canonical action`() {
        var mappedCount = 0
        for ((capability, command) in allCommandVariants()) {
            val mapped = AutomationSceneMapper.toUniversalAction(action(capability, command))
            assertNotNull("no mapping for ${command::class.simpleName}", mapped)
            mappedCount++
        }
        assertEquals(11, mappedCount)
    }

    // ── Execution summary (UI) ────────────────────────────

    @Test
    fun `success summary reports steps and duration`() {
        val summary = summarizeExecution(SceneExecutionResult.Success(2, 3, 120L))
        assertTrue(summary.contains("2/3"))
        assertTrue(summary.contains("120"))
    }

    @Test
    fun `partial failure summary contains the error`() {
        val step = ActionStep(targetDeviceId = tv, action = UniversalAction.PowerOn(tv))
        val summary = summarizeExecution(
            SceneExecutionResult.PartialFailure(0, 1, step, "boom", rolledBack = true, durationMs = 5L)
        )
        assertTrue(summary.contains("boom"))
    }

    @Test
    fun `precondition and timeout summaries are distinct`() {
        val step = ActionStep(targetDeviceId = tv, action = UniversalAction.PowerOn(tv))
        val pre = summarizeExecution(SceneExecutionResult.PreconditionFailed(step, "no device"))
        val to = summarizeExecution(SceneExecutionResult.Timeout(1, 2, step, 9L))
        assertTrue(pre.contains("Precondición"))
        assertTrue(to.contains("Tiempo agotado"))
    }

    // ── helpers ────────────────────────────────────────────

    private fun allCommandVariants(): List<Pair<Capability, CommandValue>> = listOf(
        Capability.OnOff to CommandValue.OnOff(true),
        Capability.OnOff to CommandValue.OnOff(false),
        Capability.MediaTransport to CommandValue.Media(true),
        Capability.MediaTransport to CommandValue.Media(false),
        Capability.TargetTemperature to CommandValue.Climate(20f),
        Capability.Level to CommandValue.Level(0.5f),
        Capability.Color to CommandValue.Color(120f, 0.5f),
        Capability.ColorTemperature to CommandValue.ColorTemperature(2700),
        Capability.LockUnlock to CommandValue.Lock(true),
        Capability.Position to CommandValue.Position(0.8f),
        Capability.Custom to CommandValue.Noop
    )
}