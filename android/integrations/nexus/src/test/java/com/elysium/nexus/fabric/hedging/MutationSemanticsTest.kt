package com.elysium.nexus.fabric.hedging


import com.elysium.nexus.fabric.canonical.ClimateMode
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutationSemanticsTest {

    private val target = DeviceId("dev-1")

    private fun custom(key: String) = UniversalAction.Custom(targetDeviceId = target, key = key)

    // ── IDEMPOTENT_SAFE ─────────────────────────────────────────────────

    @Test
    fun `absorbing actions are IDEMPOTENT_SAFE and hedgeable`() {
        val absorbing = listOf(
            UniversalAction.PowerOn(target),
            UniversalAction.PowerOff(target),
            UniversalAction.PowerToggle(target),
            UniversalAction.Mute(target),
            UniversalAction.MediaStop(target),
            UniversalAction.Home(target),
            UniversalAction.Back(target),
            UniversalAction.Menu(target),
            UniversalAction.SetVolume(target, 0.5f),
            UniversalAction.SetTemperature(target, 22f),
            UniversalAction.SetFanSpeed(target, 0.5f),
            UniversalAction.SetMode(target, ClimateMode.Auto),
            UniversalAction.InputSelect(target, "hdmi1")
        )
        for (a in absorbing) {
            assertEquals("$a must be IDEMPOTENT_SAFE", MutationSafety.IDEMPOTENT_SAFE, MutationSemantics.classify(a))
            assertTrue("$a must be hedgeable", MutationSemantics.canHedge(a))
            assertTrue("$a must be repeatable", MutationSemantics.canRepeatWithoutConfirmation(a))
        }
    }

    // ── NON_IDEMPOTENT ──────────────────────────────────────────────────

    @Test
    fun `incremental and transport actions are NON_IDEMPOTENT and never hedged`() {
        val incremental = listOf(
            UniversalAction.VolumeUp(target),
            UniversalAction.VolumeDown(target),
            UniversalAction.ChannelUp(target),
            UniversalAction.ChannelDown(target),
            UniversalAction.MediaPlay(target),
            UniversalAction.MediaPause(target),
            UniversalAction.MediaNext(target),
            UniversalAction.MediaPrevious(target),
            UniversalAction.Navigate(target, com.elysium.nexus.fabric.canonical.Direction.Up),
            UniversalAction.Ok(target)
        )
        for (a in incremental) {
            assertEquals("$a must be NON_IDEMPOTENT", MutationSafety.NON_IDEMPOTENT, MutationSemantics.classify(a))
            assertFalse("$a must not hedge", MutationSemantics.canHedge(a))
            assertFalse("$a must not blind-repeat", MutationSemantics.canRepeatWithoutConfirmation(a))
        }
    }

    // ── DESTRUCTIVE customs ─────────────────────────────────────────────

    @Test
    fun `destructive custom keys classify DESTRUCTIVE`() {
        for (key in listOf("factory_reset", "reset", "wipe", "erase", "delete", "clear_all")) {
            val action = custom(key)
            assertEquals("$key must be DESTRUCTIVE", MutationSafety.DESTRUCTIVE, MutationSemantics.classify(action))
            assertFalse("$key must never hedge", MutationSemantics.canHedge(action))
            assertFalse("$key must never blind-repeat", MutationSemantics.canRepeatWithoutConfirmation(action))
        }
    }

    @Test
    fun `destructive keyword suffix on custom key is detected`() {
        val action = custom("storage_factory_reset")
        assertEquals(MutationSafety.DESTRUCTIVE, MutationSemantics.classify(action))
    }

    @Test
    fun `ordinary custom keys are NON_IDEMPOTENT, not destructive`() {
        val action = custom("volume_boost")
        assertEquals(MutationSafety.NON_IDEMPOTENT, MutationSemantics.classify(action))
        assertFalse(MutationSemantics.canHedge(action))
    }

    // ── requiresConfirmation (recording axis, legacy-compatible) ────────

    @Test
    fun `requiresConfirmation keeps legacy recording policy`() {
        assertTrue(MutationSemantics.requiresConfirmation(UniversalAction.PowerOff(target)))
        assertTrue(MutationSemantics.requiresConfirmation(UniversalAction.SetMode(target, ClimateMode.Cool)))
        assertTrue(MutationSemantics.requiresConfirmation(custom("anything")))
        assertTrue(MutationSemantics.requiresConfirmation(custom("factory_reset")))

        assertFalse(MutationSemantics.requiresConfirmation(UniversalAction.PowerOn(target)))
        assertFalse(MutationSemantics.requiresConfirmation(UniversalAction.Mute(target)))
        assertFalse(MutationSemantics.requiresConfirmation(UniversalAction.VolumeUp(target)))
        assertFalse(MutationSemantics.requiresConfirmation(UniversalAction.SetVolume(target, 0.5f)))
    }

    @Test
    fun `every action has exactly one class`() {
        val all = listOf(
            UniversalAction.PowerOn(target), UniversalAction.PowerOff(target), UniversalAction.PowerToggle(target),
            UniversalAction.VolumeUp(target), UniversalAction.VolumeDown(target), UniversalAction.Mute(target),
            UniversalAction.SetVolume(target, 0.3f),
            UniversalAction.ChannelUp(target), UniversalAction.ChannelDown(target),
            UniversalAction.InputSelect(target, "hdmi2"),
            UniversalAction.MediaPlay(target), UniversalAction.MediaPause(target), UniversalAction.MediaStop(target),
            UniversalAction.MediaNext(target), UniversalAction.MediaPrevious(target),
            UniversalAction.Navigate(target, com.elysium.nexus.fabric.canonical.Direction.Left), UniversalAction.Ok(target),
            UniversalAction.Back(target), UniversalAction.Home(target), UniversalAction.Menu(target),
            UniversalAction.SetTemperature(target, 21f), UniversalAction.SetFanSpeed(target, 0.7f),
            UniversalAction.SetMode(target, ClimateMode.Heat),
            custom("anything"), custom("factory_reset")
        )
        for (a in all) {
            MutationSemantics.classify(a) // must not throw; exhaustive
        }
        assertEquals(25, all.size)
    }
}