package com.elysium.nexus.fabric.resilience

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Direction
import com.elysium.nexus.fabric.canonical.UniversalAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisconnectNeutralizerTest {

    private val deviceId = DeviceId("tv-test-01")

    @Test
    fun `trackAction records holdable actions`() {
        val neutralizer = DisconnectNeutralizer()
        val playAction = UniversalAction.MediaPlay(targetDeviceId = deviceId)

        neutralizer.trackAction(playAction)
        assertTrue(neutralizer.hasInflight())
        assertEquals(1, neutralizer.inflightCount(deviceId))
    }

    @Test
    fun `trackAction ignores momentary actions`() {
        val neutralizer = DisconnectNeutralizer()
        val volumeAction = UniversalAction.VolumeUp(targetDeviceId = deviceId)
        val navAction = UniversalAction.Navigate(targetDeviceId = deviceId, direction = Direction.Up)

        neutralizer.trackAction(volumeAction)
        neutralizer.trackAction(navAction)

        assertFalse(neutralizer.hasInflight())
        assertEquals(0, neutralizer.inflightCount(deviceId))
    }

    @Test
    fun `clearAction removes tracked action`() {
        val neutralizer = DisconnectNeutralizer()
        val playAction = UniversalAction.MediaPlay(targetDeviceId = deviceId)

        neutralizer.trackAction(playAction)
        assertEquals(1, neutralizer.inflightCount(deviceId))

        neutralizer.clearAction(playAction)
        assertEquals(0, neutralizer.inflightCount(deviceId))
        assertFalse(neutralizer.hasInflight())
    }

    @Test
    fun `neutralize generates MediaStop for MediaPlay`() {
        val neutralizer = DisconnectNeutralizer()
        val playAction = UniversalAction.MediaPlay(targetDeviceId = deviceId)

        neutralizer.trackAction(playAction)
        val neutralActions = neutralizer.neutralize(deviceId)

        assertEquals(1, neutralActions.size)
        assertTrue(neutralActions[0] is UniversalAction.MediaStop)
        assertEquals(deviceId, neutralActions[0].targetDeviceId)
        assertFalse(neutralizer.hasInflight())
    }

    @Test
    fun `neutralizeAll clears all devices and returns neutral map`() {
        val neutralizer = DisconnectNeutralizer()
        val dev1 = DeviceId("dev-1")
        val dev2 = DeviceId("dev-2")

        neutralizer.trackAction(UniversalAction.MediaPlay(targetDeviceId = dev1))
        neutralizer.trackAction(UniversalAction.Mute(targetDeviceId = dev2))

        val neutralMap = neutralizer.neutralizeAll()

        assertEquals(2, neutralMap.size)
        assertTrue(neutralMap[dev1]!![0] is UniversalAction.MediaStop)
        assertTrue(neutralMap[dev2]!![0] is UniversalAction.Mute)
        assertFalse(neutralizer.hasInflight())
    }

    @Test
    fun `reset clears all tracking state`() {
        val neutralizer = DisconnectNeutralizer()
        neutralizer.trackAction(UniversalAction.MediaPlay(targetDeviceId = deviceId))
        neutralizer.reset()

        assertFalse(neutralizer.hasInflight())
        assertEquals(0, neutralizer.inflightCount(deviceId))
    }
}
