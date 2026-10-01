package com.elysium.nexus.fabric.session

import com.elysium.nexus.fabric.canonical.DeviceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ControlSessionTest {

    private val deviceId = DeviceId("test-device-001")

    @Test
    fun `new session starts in Created state`() {
        val session = ControlSession(sessionId = "s1", deviceId = deviceId)
        assertEquals(SessionState.Created, session.state)
        assertFalse(session.isActive)
        assertFalse(session.isTerminated)
        assertEquals(0L, session.actionCount)
        assertNull(session.activeRoute)
    }

    @Test
    fun `valid transition Created to PermissionCheck`() {
        val session = ControlSession(sessionId = "s1", deviceId = deviceId)
        val next = session.transitionTo(SessionState.PermissionCheck)
        assertEquals(SessionState.PermissionCheck, next.state)
    }

    @Test
    fun `invalid transition Created to Active throws`() {
        val session = ControlSession(sessionId = "s1", deviceId = deviceId)
        try {
            session.transitionTo(SessionState.Active)
            fail("Expected IllegalArgumentException for invalid transition")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid session transition"))
        }
    }

    @Test
    fun `full lifecycle Created to Terminated`() {
        var session = ControlSession(sessionId = "s1", deviceId = deviceId)
        session = session.transitionTo(SessionState.PermissionCheck)
        session = session.transitionTo(SessionState.RouteNegotiated)
        session = session.transitionTo(SessionState.Active)
        assertTrue(session.isActive)

        session = session.recordActivity()
        assertEquals(1L, session.actionCount)

        session = session.transitionTo(SessionState.Disconnecting)
        session = session.transitionTo(SessionState.Terminated)
        assertTrue(session.isTerminated)
        assertFalse(session.isActive)
    }

    @Test
    fun `PermissionDenied is terminal`() {
        var session = ControlSession(sessionId = "s1", deviceId = deviceId)
        session = session.transitionTo(SessionState.PermissionCheck)
        session = session.transitionTo(SessionState.PermissionDenied)
        assertTrue(session.isTerminated)
    }

    @Test
    fun `terminated session cannot transition further`() {
        var session = ControlSession(sessionId = "s1", deviceId = deviceId)
        session = session.transitionTo(SessionState.Terminated)
        try {
            session.transitionTo(SessionState.Active)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid session transition"))
        }
    }

    @Test
    fun `recordActivity increments action count and updates timestamp`() {
        val session = ControlSession(sessionId = "s1", deviceId = deviceId)
        val updated = session.recordActivity(nowNs = 999L)
        assertEquals(1L, updated.actionCount)
        assertEquals(999L, updated.lastActivityNs)

        val again = updated.recordActivity(nowNs = 1000L)
        assertEquals(2L, again.actionCount)
        assertEquals(1000L, again.lastActivityNs)
    }

    @Test
    fun `sessionId must be non-blank`() {
        try {
            ControlSession(sessionId = "", deviceId = deviceId)
            fail("Expected IllegalArgumentException for blank sessionId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("sessionId"))
        }
    }

    // ── SessionManager tests ──────────────────────────

    @Test
    fun `SessionManager creates and tracks sessions`() {
        val manager = SessionManager()
        val session = manager.createSession("s1", deviceId)
        assertNotNull(session)
        assertEquals(1, manager.totalSessions)
        assertEquals(session, manager.sessionFor(deviceId))
    }

    @Test
    fun `SessionManager enforces single session per device`() {
        var terminated: ControlSession? = null
        val manager = SessionManager(onSessionTerminated = { terminated = it })

        manager.createSession("s1", deviceId)
        val s2 = manager.createSession("s2", deviceId)

        // Previous session should have been terminated
        assertNotNull("Previous session should be terminated", terminated)
        assertTrue(terminated!!.isTerminated)
        assertEquals("s2", s2.sessionId)
    }

    @Test
    fun `SessionManager terminateSession terminates and fires callback`() {
        var terminated: ControlSession? = null
        val manager = SessionManager(onSessionTerminated = { terminated = it })

        manager.createSession("s1", deviceId)
        val result = manager.terminateSession(deviceId)

        assertNotNull(result)
        assertTrue(result!!.isTerminated)
        assertNotNull(terminated)
    }

    @Test
    fun `SessionManager terminateAll clears all active sessions`() {
        val manager = SessionManager()
        val d1 = DeviceId("d1")
        val d2 = DeviceId("d2")
        manager.createSession("s1", d1)
        manager.createSession("s2", d2)

        assertEquals(2, manager.activeSessions().size)

        val terminated = manager.terminateAll()
        assertEquals(2, terminated.size)
        assertEquals(0, manager.activeSessions().size)
    }

    @Test
    fun `SessionManager returns null for unknown device`() {
        val manager = SessionManager()
        assertNull(manager.sessionFor(DeviceId("unknown")))
    }

    // ── PermissionGate tests ──────────────────────────

    @Test
    fun `PermissionGate grants when all permissions present`() {
        val result = PermissionGate.check(
            protocol = com.elysium.nexus.fabric.canonical.Protocol.DirectIr,
            grantedPermissions = setOf(PermissionGate.TRANSMIT_IR)
        )
        assertTrue(result is PermissionResult.Granted)
    }

    @Test
    fun `PermissionGate denies when permission missing`() {
        val result = PermissionGate.check(
            protocol = com.elysium.nexus.fabric.canonical.Protocol.DirectIr,
            grantedPermissions = emptySet()
        )
        assertTrue(result is PermissionResult.Denied || result is PermissionResult.RationaleRequired)
    }

    @Test
    fun `PermissionGate grants for protocol with no required permissions`() {
        val result = PermissionGate.check(
            protocol = com.elysium.nexus.fabric.canonical.Protocol.HidOverUsb,
            grantedPermissions = emptySet()
        )
        assertTrue(result is PermissionResult.Granted)
    }

    @Test
    fun `PermissionGate requires rationale for BLE permissions`() {
        val result = PermissionGate.check(
            protocol = com.elysium.nexus.fabric.canonical.Protocol.HidOverBle,
            grantedPermissions = emptySet()
        )
        assertTrue("BLE should request rationale",
            result is PermissionResult.RationaleRequired)
    }

    @Test
    fun `PermissionGate requiredPermissions covers all Protocol values`() {
        for (protocol in com.elysium.nexus.fabric.canonical.Protocol.values()) {
            val perms = PermissionGate.requiredPermissions(protocol)
            assertNotNull("requiredPermissions must not be null for $protocol", perms)
        }
    }
}
