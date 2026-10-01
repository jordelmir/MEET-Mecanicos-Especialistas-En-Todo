package com.elysium.nexus.core.haptics

import com.elysium.nexus.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for [SettingsAwareHaptics].
 *
 * The wrapper is a thin decorator: it forwards
 * events to the inner [Haptics] when the
 * settings say "haptics on", and silently drops
 * them when the settings say "haptics off". The
 * test verifies both halves and the transition.
 */
class SettingsAwareHapticsTest {

    @Test
    fun `forwards events when hapticsEnabled is true`() {
        val inner = FakeHaptics()
        val flow = MutableStateFlow(AppSettings(hapticsEnabled = true))
        val wrapper = SettingsAwareHaptics(inner, flow)
        wrapper.fire(HapticEvent.ButtonTap)
        wrapper.fire(HapticEvent.Error)
        assertEquals(2, inner.events().size)
        assertEquals(HapticEvent.ButtonTap, inner.events()[0])
        assertEquals(HapticEvent.Error, inner.events()[1])
    }

    @Test
    fun `drops events when hapticsEnabled is false`() {
        val inner = FakeHaptics()
        val flow = MutableStateFlow(AppSettings(hapticsEnabled = false))
        val wrapper = SettingsAwareHaptics(inner, flow)
        wrapper.fire(HapticEvent.ButtonTap)
        wrapper.fire(HapticEvent.Error)
        assertEquals(0, inner.events().size)
    }

    @Test
    fun `respects a runtime change in hapticsEnabled`() {
        val inner = FakeHaptics()
        val flow = MutableStateFlow(AppSettings(hapticsEnabled = true))
        val wrapper = SettingsAwareHaptics(inner, flow)
        wrapper.fire(HapticEvent.ButtonTap)
        flow.value = AppSettings(hapticsEnabled = false)
        wrapper.fire(HapticEvent.Error)
        assertEquals(1, inner.events().size)
        assertEquals(HapticEvent.ButtonTap, inner.events()[0])
    }

    @Test
    fun `re-enabling haptics resumes forwarding`() {
        val inner = FakeHaptics()
        val flow = MutableStateFlow(AppSettings(hapticsEnabled = false))
        val wrapper = SettingsAwareHaptics(inner, flow)
        wrapper.fire(HapticEvent.ButtonTap)
        flow.value = AppSettings(hapticsEnabled = true)
        wrapper.fire(HapticEvent.Error)
        assertEquals(1, inner.events().size)
        assertEquals(HapticEvent.Error, inner.events()[0])
    }
}
