package com.elysium.nexus.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [DpadState] — the 8+1 D-pad states and their HID hat-switch
 * encoding per `MASTER_ORDER.md` §18.
 */
class DpadStateTest {

    @Test
    fun centerIsTheOnlyInactiveState() {
        assertFalse(DpadState.Center.isActive())
        for (s in DpadState.values()) {
            if (s == DpadState.Center) continue
            assertTrue("expected $s to be active", s.isActive())
        }
    }

    @Test
    fun unitVectorAtCenterIsOrigin() {
        val (x, y) = DpadState.Center.unitVector()
        assertEquals(0, x)
        assertEquals(0, y)
    }

    @Test
    fun unitVectorsForCardinals() {
        assertEquals(Pair(0, +1), DpadState.North.unitVector())
        assertEquals(Pair(+1, 0), DpadState.East.unitVector())
        assertEquals(Pair(0, -1), DpadState.South.unitVector())
        assertEquals(Pair(-1, 0), DpadState.West.unitVector())
    }

    @Test
    fun unitVectorsForDiagonals() {
        assertEquals(Pair(+1, +1), DpadState.NorthEast.unitVector())
        assertEquals(Pair(+1, -1), DpadState.SouthEast.unitVector())
        assertEquals(Pair(-1, -1), DpadState.SouthWest.unitVector())
        assertEquals(Pair(-1, +1), DpadState.NorthWest.unitVector())
    }

    @Test
    fun hatSwitchRoundTripForAllStates() {
        for (state in DpadState.values()) {
            val hat = DpadState.toHatSwitch(state)
            val parsed = DpadState.fromHatSwitch(hat)
            assertEquals(state, parsed)
        }
    }

    @Test
    fun hatSwitchEncodingMatchesUsbHidSpec() {
        // 0..7 are clockwise from North; 8 is neutral. This is the
        // USB HID Usage Tables convention. The values are part of
        // the public wire contract.
        assertEquals(0, DpadState.toHatSwitch(DpadState.North))
        assertEquals(1, DpadState.toHatSwitch(DpadState.NorthEast))
        assertEquals(2, DpadState.toHatSwitch(DpadState.East))
        assertEquals(3, DpadState.toHatSwitch(DpadState.SouthEast))
        assertEquals(4, DpadState.toHatSwitch(DpadState.South))
        assertEquals(5, DpadState.toHatSwitch(DpadState.SouthWest))
        assertEquals(6, DpadState.toHatSwitch(DpadState.West))
        assertEquals(7, DpadState.toHatSwitch(DpadState.NorthWest))
        assertEquals(8, DpadState.toHatSwitch(DpadState.Center))
    }

    @Test
    fun invalidHatValueReturnsNull() {
        // A malformed report (e.g. a 4-bit field carrying garbage)
        // must not silently map to Center. The engine rejects the
        // report when fromHatSwitch returns null.
        assertNull(DpadState.fromHatSwitch(9))
        assertNull(DpadState.fromHatSwitch(-1))
    }
}
