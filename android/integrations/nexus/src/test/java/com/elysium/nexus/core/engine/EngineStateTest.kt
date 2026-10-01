package com.elysium.nexus.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [EngineState] — the 10-state state machine.
 */
class EngineStateTest {

    @Test
    fun activeIsTheOnlyActiveState() {
        assertTrue(EngineState.Active.isActive())
        for (s in EngineState.values()) {
            if (s == EngineState.Active) continue
            assertFalse("expected $s not active", s.isActive())
        }
    }

    @Test
    fun hasSessionIsTrueForConnectedActiveSuspendedReconnecting() {
        assertTrue(EngineState.Connected.hasSession())
        assertTrue(EngineState.Active.hasSession())
        assertTrue(EngineState.Suspended.hasSession())
        assertTrue(EngineState.Reconnecting.hasSession())
    }

    @Test
    fun hasSessionIsFalseForIdleAndPreConnection() {
        for (s in EngineState.values()) {
            if (s in setOf(
                    EngineState.Connected,
                    EngineState.Active,
                    EngineState.Suspended,
                    EngineState.Reconnecting
                )
            ) continue
            assertFalse("expected $s not to have a session", s.hasSession())
        }
    }

    @Test
    fun allTenStatesAreDistinct() {
        assertEquals(10, EngineState.values().size)
    }

    @Test
    fun idleIsTheDefault() {
        // The first value of the enum is the default. Idle is
        // declared first, so this pins both the value and the
        // order.
        assertEquals(EngineState.Idle, EngineState.values()[0])
    }
}
