package com.elysium.nexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ButtonSet] — the 64-bit-bitset wrapper for the 21
 * canonical buttons. This is the wire-cheap representation; every
 * other surface that deals with multiple buttons at once goes
 * through it.
 */
class ButtonSetTest {

    @Test
    fun emptySetHasNoPressedButtons() {
        assertTrue(ButtonSet.EMPTY.isEmpty())
        assertEquals(0, ButtonSet.EMPTY.size())
        for (b in CanonicalButton.values()) {
            assertFalse(ButtonSet.EMPTY.isPressed(b))
        }
    }

    @Test
    fun allSetHasEveryButtonPressed() {
        val all = ButtonSet.ALL
        assertEquals(CanonicalButton.COUNT, all.size())
        for (b in CanonicalButton.values()) {
            assertTrue("expected ${b.name} to be pressed in ALL", all.isPressed(b))
        }
    }

    @Test
    fun withPressesAndReleasesIndividually() {
        val initial = ButtonSet.EMPTY
        val southDown = initial.with(CanonicalButton.South, true)
        assertTrue(southDown.isPressed(CanonicalButton.South))
        assertEquals(1, southDown.size())

        val southUp = southDown.with(CanonicalButton.South, false)
        assertEquals(ButtonSet.EMPTY, southUp)
    }

    @Test
    fun withIsImmutable() {
        val initial = ButtonSet.EMPTY
        initial.with(CanonicalButton.North, true)
        // The original instance must not have mutated. The §36
        // property "a released button does not remain active" is
        // built on this guarantee.
        assertTrue(initial.isEmpty())
    }

    @Test
    fun multipleButtonsCoexist() {
        val set = ButtonSet.EMPTY
            .with(CanonicalButton.South, true)
            .with(CanonicalButton.North, true)
            .with(CanonicalButton.LeftBumper, true)
        assertEquals(3, set.size())
        assertTrue(set.isPressed(CanonicalButton.South))
        assertTrue(set.isPressed(CanonicalButton.North))
        assertTrue(set.isPressed(CanonicalButton.LeftBumper))
        assertFalse(set.isPressed(CanonicalButton.East))
    }

    @Test
    fun forEachPressedVisitsEachHeldButtonExactlyOnce() {
        val set = ButtonSet.EMPTY
            .with(CanonicalButton.South, true)
            .with(CanonicalButton.North, true)
            .with(CanonicalButton.RightBumper, true)
        val visited = mutableListOf<CanonicalButton>()
        set.forEachPressed { visited.add(it) }
        assertEquals(
            setOf(
                CanonicalButton.South,
                CanonicalButton.North,
                CanonicalButton.RightBumper
            ),
            visited.toSet()
        )
        assertEquals(3, visited.size)
    }
}
