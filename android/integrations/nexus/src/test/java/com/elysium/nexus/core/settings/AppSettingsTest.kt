package com.elysium.nexus.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [AppSettings].
 *
 * The settings are the §15 "I own a Magic V2"
 * user-tunable document. The test suite covers
 * the constructor's validation, the defaults,
 * and the `copy` ergonomics.
 */
class AppSettingsTest {

    @Test
    fun `defaults are the no-op configuration`() {
        val s = AppSettings()
        assertEquals(1.0f, s.leftStickSensitivity, 0.0001f)
        assertEquals(1.0f, s.rightStickSensitivity, 0.0001f)
        assertFalse(s.invertLeftX)
        assertFalse(s.invertLeftY)
        assertFalse(s.invertRightX)
        assertFalse(s.invertRightY)
        assertTrue(s.hapticsEnabled)
        assertTrue(s.darkTheme)
    }

    @Test
    fun `left stick sensitivity is validated`() {
        // Below the minimum.
        var threw = false
        try {
            AppSettings(leftStickSensitivity = 0.1f)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected IllegalArgumentException for low sensitivity", threw)
        // Above the maximum.
        threw = false
        try {
            AppSettings(leftStickSensitivity = 3.0f)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected IllegalArgumentException for high sensitivity", threw)
    }

    @Test
    fun `right stick sensitivity is validated`() {
        var threw = false
        try {
            AppSettings(rightStickSensitivity = 0.1f)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected IllegalArgumentException for low right sensitivity", threw)
    }

    @Test
    fun `sensitivity range is permissive at the boundaries`() {
        val min = AppSettings(leftStickSensitivity = AppSettings.MIN_SENSITIVITY)
        assertEquals(AppSettings.MIN_SENSITIVITY, min.leftStickSensitivity, 0.0001f)
        val max = AppSettings(leftStickSensitivity = AppSettings.MAX_SENSITIVITY)
        assertEquals(AppSettings.MAX_SENSITIVITY, max.leftStickSensitivity, 0.0001f)
    }

    @Test
    fun `copy preserves the unchanged fields`() {
        val s = AppSettings(
            leftStickSensitivity = 1.5f,
            invertLeftX = true
        )
        val t = s.copy(rightStickSensitivity = 0.7f)
        assertEquals(1.5f, t.leftStickSensitivity, 0.0001f)
        assertEquals(0.7f, t.rightStickSensitivity, 0.0001f)
        assertTrue(t.invertLeftX)
        // hapticsEnabled is preserved.
        assertTrue(t.hapticsEnabled)
    }

    @Test
    fun `copy produces an unequal instance when a field changes`() {
        val a = AppSettings()
        val b = a.copy(hapticsEnabled = false)
        assertNotEquals(a, b)
        assertFalse(b.hapticsEnabled)
    }

    @Test
    fun `copy produces an equal instance when no field changes`() {
        val a = AppSettings()
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `MIN_SENSITIVITY and MAX_SENSITIVITY bracket the range`() {
        assertTrue(AppSettings.MIN_SENSITIVITY < AppSettings.MAX_SENSITIVITY)
        assertEquals(0.5f, AppSettings.MIN_SENSITIVITY, 0.0001f)
        assertEquals(2.0f, AppSettings.MAX_SENSITIVITY, 0.0001f)
    }
}
