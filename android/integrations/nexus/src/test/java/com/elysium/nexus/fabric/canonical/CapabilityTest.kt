package com.elysium.nexus.fabric.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * JVM tests for the §4.3 [Capability] enum
 * + [ActionRisk] / [AuthenticationLevel].
 */
class CapabilityTest {

    @Test
    fun `every capability has a non-null default risk`() {
        Capability.values().forEach { c ->
            assertNotNull("Capability.${c.name} has a null defaultRisk", c.defaultRisk)
        }
    }

    @Test
    fun `capability count is at least 30 per §4_3`() {
        // §4.3 lists 30+ capabilities. The enum
        // currently has 40+ variants; the floor
        // is the §4.3 minimum.
        assertTrue(
            "Capability enum must have at least 30 variants; got ${Capability.values().size}",
            Capability.values().size >= 30
        )
    }

    @Test
    fun `security-sensitive capabilities match the §18_1 set`() {
        assertEquals(Capability.LockUnlock, Capability.SECURITY_SENSITIVE.first())
        assertTrue(Capability.SECURITY_SENSITIVE.contains(Capability.LockUnlock))
        assertTrue(Capability.SECURITY_SENSITIVE.contains(Capability.ArmDisarm))
    }

    @Test
    fun `life-safety capabilities match the §4_3 set`() {
        assertTrue(Capability.LIFE_SAFETY.contains(Capability.SmokeDetection))
        assertTrue(Capability.LIFE_SAFETY.contains(Capability.CarbonMonoxideDetection))
    }

    @Test
    fun `high-power capabilities use HighPower risk`() {
        assertEquals(ActionRisk.HighPower, Capability.EnergyControl.defaultRisk)
        assertEquals(ActionRisk.HighPower, Capability.Charging.defaultRisk)
    }

    @Test
    fun `security capabilities use SecuritySensitive risk`() {
        assertEquals(ActionRisk.SecuritySensitive, Capability.LockUnlock.defaultRisk)
        assertEquals(ActionRisk.SecuritySensitive, Capability.ArmDisarm.defaultRisk)
    }

    @Test
    fun `life-safety capabilities use LifeSafety risk`() {
        assertEquals(ActionRisk.LifeSafety, Capability.SmokeDetection.defaultRisk)
        assertEquals(ActionRisk.LifeSafety, Capability.CarbonMonoxideDetection.defaultRisk)
    }

    @Test
    fun `privacy-sensitive capabilities use PrivacySensitive risk`() {
        assertEquals(ActionRisk.PrivacySensitive, Capability.CameraStream.defaultRisk)
        assertEquals(ActionRisk.PrivacySensitive, Capability.CameraRecord.defaultRisk)
        assertEquals(ActionRisk.PrivacySensitive, Capability.Doorbell.defaultRisk)
    }

    @Test
    fun `physical-motion capabilities use PhysicalMotion risk`() {
        assertEquals(ActionRisk.PhysicalMotion, Capability.OpenClose.defaultRisk)
        assertEquals(ActionRisk.PhysicalMotion, Capability.Position.defaultRisk)
    }

    @Test
    fun `ActionRisk maps to the right AuthenticationLevel per §18_1 + threat model`() {
        assertEquals(AuthenticationLevel.None, ActionRisk.Informational.minAuthLevel())
        assertEquals(AuthenticationLevel.DeviceSession, ActionRisk.Low.minAuthLevel())
        assertEquals(AuthenticationLevel.DeviceSession, ActionRisk.Reversible.minAuthLevel())
        assertEquals(AuthenticationLevel.DeviceSession, ActionRisk.PhysicalMotion.minAuthLevel())
        assertEquals(AuthenticationLevel.DeviceSession, ActionRisk.PrivacySensitive.minAuthLevel())
        assertEquals(AuthenticationLevel.StepUp, ActionRisk.SecuritySensitive.minAuthLevel())
        assertEquals(AuthenticationLevel.StepUp, ActionRisk.HighPower.minAuthLevel())
        assertEquals(AuthenticationLevel.StepUpAndOwner, ActionRisk.LifeSafety.minAuthLevel())
    }

    @Test
    fun `AuthenticationLevel session constants are sensible`() {
        // The session-max-age must be positive
        // and the step-up-max-age shorter.
        assertTrue(AuthenticationLevel.SESSION_MAX_AGE_MS > 0L)
        assertTrue(AuthenticationLevel.STEP_UP_MAX_AGE_MS > 0L)
        assertTrue(AuthenticationLevel.STEP_UP_MAX_AGE_MS < AuthenticationLevel.SESSION_MAX_AGE_MS)
    }

    @Test
    fun `all 30+ capabilities declared per §4_3 are present`() {
        val expected = setOf(
            "OnOff", "Toggle", "Level", "Color", "ColorTemperature",
            "Temperature", "TargetTemperature", "FanSpeed", "Swing", "Mode", "Timer",
            "OpenClose", "Position", "Direction",
            "LockUnlock", "ArmDisarm",
            "StartStop", "PauseResume", "MediaTransport", "Volume", "Channel", "InputSource", "Scene",
            "EnergyRead", "EnergyControl", "Charging",
            "CameraStream", "CameraPtz", "CameraTalk", "CameraRecord",
            "Doorbell", "Presence",
            "MotionDetection", "ContactDetection", "SmokeDetection", "CarbonMonoxideDetection",
            "WaterLeakDetection", "AirQuality", "Irrigation", "Custom"
        )
        val actual = Capability.values().map { it.name }.toSet()
        for (name in expected) {
            if (name !in actual) {
                fail("Expected capability '$name' (per §4.3) is missing from the enum.")
            }
        }
    }
}
