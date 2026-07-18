package com.elysium369.meet.core.monetization

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure-logic unit tests for AdGateManager safety rules.
 * Tests the isSafetyOrEmergencyContext logic without Android Context.
 */
class AdGateManagerTest {

    // ── Extracted pure function from AdGateManager ──
    private fun isSafetyOrEmergencyContext(screen: String?, obdConnected: Boolean): Boolean {
        if (screen == null) return false
        val restricted = listOf(
            "hud", "active_tests", "oscilloscope", "service_resets",
            "tow_truck_service", "mechanic_service", "live_link",
            "connect", "dvir", "dashcam", "premium"
        )
        val normalized = screen.substringBefore("/").substringBefore("?")
        return normalized in restricted || (obdConnected && normalized == "scanner")
    }

    // ═══════════════════════════════════════════════════════════
    // Safety-critical screens MUST block ads
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `ads blocked on HUD screen`() {
        assertTrue(isSafetyOrEmergencyContext("hud", false))
    }

    @Test
    fun `ads blocked on active tests`() {
        assertTrue(isSafetyOrEmergencyContext("active_tests", false))
    }

    @Test
    fun `ads blocked on oscilloscope`() {
        assertTrue(isSafetyOrEmergencyContext("oscilloscope", false))
    }

    @Test
    fun `ads blocked on service resets`() {
        assertTrue(isSafetyOrEmergencyContext("service_resets", false))
    }

    @Test
    fun `ads blocked on tow truck emergency`() {
        assertTrue(isSafetyOrEmergencyContext("tow_truck_service", false))
    }

    @Test
    fun `ads blocked on live link`() {
        assertTrue(isSafetyOrEmergencyContext("live_link", false))
    }

    @Test
    fun `ads blocked on DVIR`() {
        assertTrue(isSafetyOrEmergencyContext("dvir", false))
    }

    @Test
    fun `ads blocked on connect screen`() {
        assertTrue(isSafetyOrEmergencyContext("connect", false))
    }

    @Test
    fun `ads blocked on premium paywall`() {
        assertTrue(isSafetyOrEmergencyContext("premium", false))
    }

    // ═══════════════════════════════════════════════════════════
    // OBD-connected scanner blocks ads
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `ads blocked on scanner when OBD is connected`() {
        assertTrue(isSafetyOrEmergencyContext("scanner", obdConnected = true))
    }

    @Test
    fun `ads allowed on scanner when OBD is disconnected`() {
        assertFalse(isSafetyOrEmergencyContext("scanner", obdConnected = false))
    }

    // ═══════════════════════════════════════════════════════════
    // Non-critical screens allow ads
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `ads allowed on home screen`() {
        assertFalse(isSafetyOrEmergencyContext("home", false))
    }

    @Test
    fun `ads allowed on garage screen`() {
        assertFalse(isSafetyOrEmergencyContext("garage", false))
    }

    @Test
    fun `ads allowed on manuals screen`() {
        assertFalse(isSafetyOrEmergencyContext("manuals", false))
    }

    @Test
    fun `ads allowed on settings screen`() {
        assertFalse(isSafetyOrEmergencyContext("settings", false))
    }

    // ═══════════════════════════════════════════════════════════
    // Route parameter stripping
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `route parameters are stripped before matching`() {
        assertTrue(isSafetyOrEmergencyContext("hud/full", false))
        assertTrue(isSafetyOrEmergencyContext("active_tests?pid=01", false))
    }

    @Test
    fun `null screen does not block ads`() {
        assertFalse(isSafetyOrEmergencyContext(null, false))
    }
}
