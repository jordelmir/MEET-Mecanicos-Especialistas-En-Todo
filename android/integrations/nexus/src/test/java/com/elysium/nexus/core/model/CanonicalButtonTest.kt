package com.elysium.nexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smoke tests for [CanonicalButton] ordering.
 *
 * The ordinals are part of the public contract — [ButtonSet] relies on
 * them to map a button to a bit. We pin them with assertions so a
 * careless reorder is caught at test time, not at HID wire time.
 */
class CanonicalButtonTest {

    @Test
    fun faceButtonsAreInSouthEastWestNorthOrder() {
        assertEquals(CanonicalButton.South, CanonicalButton.values()[0])
        assertEquals(CanonicalButton.East, CanonicalButton.values()[1])
        assertEquals(CanonicalButton.West, CanonicalButton.values()[2])
        assertEquals(CanonicalButton.North, CanonicalButton.values()[3])
    }

    @Test
    fun totalCountIs23() {
        // 4 face + 4 shoulder/trigger + 2 stick-click + 5 system +
        // 4 paddle + 4 auxiliary. The compile-time guard inside
        // CanonicalButton.Companion already enforces this, but the
        // explicit test makes the number grep-able from the test
        // source — useful when reading the wire format.
        assertEquals(23, CanonicalButton.values().size)
    }

    @Test
    fun allButtonsAreDistinct() {
        // Belt-and-braces: every value should have a unique name.
        val names = CanonicalButton.values().map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}
