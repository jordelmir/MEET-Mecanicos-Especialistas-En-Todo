package com.elysium.nexus.core.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * JVM tests for [InMemoryAppSettingsStore].
 *
 * The store is the test-friendly half of the
 * §15 settings story. The Android-backed
 * [AndroidAppSettingsStore] is the production
 * counterpart; the test for it is an on-device
 * smoke test (Phase 1.18's manual verification).
 */
class InMemoryAppSettingsStoreTest {

    @Test
    fun `current returns the initial value`() {
        val initial = AppSettings(
            leftStickSensitivity = 1.4f,
            hapticsEnabled = false
        )
        val store = InMemoryAppSettingsStore(initial)
        assertEquals(initial, store.current)
    }

    @Test
    fun `current returns the default when no initial is provided`() {
        val store = InMemoryAppSettingsStore()
        assertEquals(AppSettings(), store.current)
    }

    @Test
    fun `update replaces the current value`() {
        val store = InMemoryAppSettingsStore()
        val updated = AppSettings(
            leftStickSensitivity = 0.8f,
            invertLeftX = true
        )
        store.update(updated)
        assertEquals(updated, store.current)
    }

    @Test
    fun `updates flow emits the new value after update`() = runBlocking {
        val store = InMemoryAppSettingsStore()
        val updated = AppSettings(rightStickSensitivity = 1.6f)
        store.update(updated)
        // The StateFlow always carries the latest
        // value; `first()` returns it.
        val emitted = store.updates.first()
        assertEquals(updated, emitted)
    }

    @Test
    fun `multiple updates are reflected in current`() {
        val store = InMemoryAppSettingsStore()
        store.update(AppSettings(leftStickSensitivity = 1.1f))
        store.update(AppSettings(leftStickSensitivity = 1.2f))
        store.update(AppSettings(leftStickSensitivity = 1.3f))
        assertEquals(1.3f, store.current.leftStickSensitivity, 0.0001f)
    }

    @Test
    fun `current returns the same reference on every call (no copy)`() {
        val initial = AppSettings()
        val store = InMemoryAppSettingsStore(initial)
        // The store is just `state.value`; the
        // reference is stable as long as no
        // `update` happens.
        assertSame(initial, store.current)
    }
}
