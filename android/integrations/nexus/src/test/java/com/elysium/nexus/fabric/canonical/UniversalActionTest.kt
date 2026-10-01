package com.elysium.nexus.fabric.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UniversalActionTest {

    private val testDeviceId = DeviceId("test-tv-001")

    @Test
    fun `PowerOn carries correct required capability`() {
        val action = UniversalAction.PowerOn(targetDeviceId = testDeviceId)
        assertEquals(Capability.OnOff, action.requiredCapability())
        assertEquals(testDeviceId, action.targetDeviceId)
        assertTrue(action.correlationId.isNotBlank())
    }

    @Test
    fun `VolumeUp carries Volume capability`() {
        val action = UniversalAction.VolumeUp(targetDeviceId = testDeviceId)
        assertEquals(Capability.Volume, action.requiredCapability())
    }

    @Test
    fun `SetVolume validates range 0 to 1`() {
        val action = UniversalAction.SetVolume(targetDeviceId = testDeviceId, level = 0.5f)
        assertEquals(0.5f, action.level)
        assertEquals(Capability.Volume, action.requiredCapability())
    }

    @Test
    fun `SetVolume rejects out of range`() {
        try {
            UniversalAction.SetVolume(targetDeviceId = testDeviceId, level = 1.5f)
            fail("Expected IllegalArgumentException for level > 1")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Volume level"))
        }
    }

    @Test
    fun `SetVolume rejects negative`() {
        try {
            UniversalAction.SetVolume(targetDeviceId = testDeviceId, level = -0.1f)
            fail("Expected IllegalArgumentException for negative level")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Volume level"))
        }
    }

    @Test
    fun `ChannelUp carries Channel capability`() {
        val action = UniversalAction.ChannelUp(targetDeviceId = testDeviceId)
        assertEquals(Capability.Channel, action.requiredCapability())
    }

    @Test
    fun `InputSelect requires non-blank inputId`() {
        val action = UniversalAction.InputSelect(targetDeviceId = testDeviceId, inputId = "HDMI1")
        assertEquals(Capability.InputSource, action.requiredCapability())
        assertEquals("HDMI1", action.inputId)
    }

    @Test
    fun `InputSelect rejects blank inputId`() {
        try {
            UniversalAction.InputSelect(targetDeviceId = testDeviceId, inputId = "  ")
            fail("Expected IllegalArgumentException for blank inputId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("inputId"))
        }
    }

    @Test
    fun `MediaPlay carries MediaTransport capability`() {
        val action = UniversalAction.MediaPlay(targetDeviceId = testDeviceId)
        assertEquals(Capability.MediaTransport, action.requiredCapability())
    }

    @Test
    fun `Navigate carries MediaTransport capability`() {
        val action = UniversalAction.Navigate(
            targetDeviceId = testDeviceId,
            direction = Direction.Up
        )
        assertEquals(Capability.MediaTransport, action.requiredCapability())
    }

    @Test
    fun `SetTemperature validates range`() {
        val action = UniversalAction.SetTemperature(
            targetDeviceId = testDeviceId,
            targetCelsius = 22f,
            mode = ClimateMode.Cool
        )
        assertEquals(22f, action.targetCelsius)
        assertEquals(ClimateMode.Cool, action.mode)
        assertEquals(Capability.TargetTemperature, action.requiredCapability())
    }

    @Test
    fun `SetTemperature rejects out of range`() {
        try {
            UniversalAction.SetTemperature(
                targetDeviceId = testDeviceId,
                targetCelsius = 200f
            )
            fail("Expected IllegalArgumentException for temperature > 150")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Temperature"))
        }
    }

    @Test
    fun `SetFanSpeed carries FanSpeed capability`() {
        val action = UniversalAction.SetFanSpeed(targetDeviceId = testDeviceId, level = 0.7f)
        assertEquals(Capability.FanSpeed, action.requiredCapability())
        assertEquals(0.7f, action.level)
    }

    @Test
    fun `SetMode carries Mode capability`() {
        val action = UniversalAction.SetMode(
            targetDeviceId = testDeviceId,
            mode = ClimateMode.Heat
        )
        assertEquals(Capability.Mode, action.requiredCapability())
        assertEquals(ClimateMode.Heat, action.mode)
    }

    @Test
    fun `Custom action requires non-blank key`() {
        val action = UniversalAction.Custom(
            targetDeviceId = testDeviceId,
            key = "reboot",
            payload = mapOf("force" to "true")
        )
        assertEquals(Capability.Custom, action.requiredCapability())
        assertEquals("reboot", action.key)
    }

    @Test
    fun `Custom action rejects blank key`() {
        try {
            UniversalAction.Custom(targetDeviceId = testDeviceId, key = "")
            fail("Expected IllegalArgumentException for blank key")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("key"))
        }
    }

    @Test
    fun `all action types have unique correlation ids`() {
        val a1 = UniversalAction.PowerOn(targetDeviceId = testDeviceId)
        val a2 = UniversalAction.PowerOn(targetDeviceId = testDeviceId)
        assertTrue("Two distinct actions must have different correlation IDs",
            a1.correlationId != a2.correlationId)
    }

    @Test
    fun `all Direction values exist`() {
        assertEquals(4, Direction.values().size)
        assertNotNull(Direction.valueOf("Up"))
        assertNotNull(Direction.valueOf("Down"))
        assertNotNull(Direction.valueOf("Left"))
        assertNotNull(Direction.valueOf("Right"))
    }

    @Test
    fun `exhaustive requiredCapability covers all action subtypes`() {
        // If a new action is added without updating requiredCapability(),
        // this test surfaces it via the compiler (non-exhaustive when).
        val actions = listOf(
            UniversalAction.PowerOn(testDeviceId),
            UniversalAction.PowerOff(testDeviceId),
            UniversalAction.PowerToggle(testDeviceId),
            UniversalAction.VolumeUp(testDeviceId),
            UniversalAction.VolumeDown(testDeviceId),
            UniversalAction.Mute(testDeviceId),
            UniversalAction.SetVolume(testDeviceId, 0.5f),
            UniversalAction.ChannelUp(testDeviceId),
            UniversalAction.ChannelDown(testDeviceId),
            UniversalAction.InputSelect(testDeviceId, "HDMI1"),
            UniversalAction.MediaPlay(testDeviceId),
            UniversalAction.MediaPause(testDeviceId),
            UniversalAction.MediaStop(testDeviceId),
            UniversalAction.MediaNext(testDeviceId),
            UniversalAction.MediaPrevious(testDeviceId),
            UniversalAction.Navigate(testDeviceId, Direction.Up),
            UniversalAction.Ok(testDeviceId),
            UniversalAction.Back(testDeviceId),
            UniversalAction.Home(testDeviceId),
            UniversalAction.Menu(testDeviceId),
            UniversalAction.SetTemperature(testDeviceId, 22f),
            UniversalAction.SetFanSpeed(testDeviceId, 0.5f),
            UniversalAction.SetMode(testDeviceId, ClimateMode.Auto),
            UniversalAction.Custom(testDeviceId, "test")
        )
        for (action in actions) {
            assertNotNull("requiredCapability() must not be null for ${action::class.simpleName}",
                action.requiredCapability())
        }
    }
}
