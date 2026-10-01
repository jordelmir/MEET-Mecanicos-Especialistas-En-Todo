package com.elysium.nexus.ui.hub

import com.elysium.nexus.core.device.DeviceCatalog
import com.elysium.nexus.core.device.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation restore (§38): the back button must never reset the
 * session. The stack codec round-trips every restorable destination
 * so a recreated Activity reopens where the user left it.
 */
class HubStackCodecTest {

    @Test
    fun hub_roundTrips() {
        assertEquals(HubDestination.Hub, HubStackCodec.decode("hub"))
    }

    @Test
    fun tvControls_roundTrips() {
        assertEquals(HubDestination.TvControls, HubStackCodec.decode("tvc"))
    }

    @Test
    fun category_roundTrips() {
        val d = HubStackCodec.decode("cat:TV")
        assertNotNull(d)
        assertEquals(HubDestination.Category(DeviceCategory.TV), d)
    }

    @Test
    fun consolePicker_roundTrips() {
        val d = HubStackCodec.decode("cpk:PLAYSTATION:ps5")
        assertNotNull(d)
        assertTrue(d is HubDestination.ConsolePicker)
        if (d is HubDestination.ConsolePicker) {
            assertEquals(DeviceCategory.PLAYSTATION, d.category)
            assertEquals("ps5", d.subcategory.id)
        }
    }

    @Test
    fun consoleDevice_andConnect_resolveTemplateFromCatalog() {
        val template = DeviceCatalog.all.first { it.category == DeviceCategory.PLAYSTATION }
        val code = HubStackCodec.encode(HubDestination.ConsoleDevice(template))
        assertNotNull(code)
        val restored = HubStackCodec.decode(code!!)
        assertTrue(restored is HubDestination.ConsoleDevice)
        if (restored is HubDestination.ConsoleDevice) {
            assertEquals(template.id, restored.template.id)
        }

        val connect = HubStackCodec.decode(HubStackCodec.encode(HubDestination.Connect(template))!!)
        assertTrue(connect is HubDestination.Connect)
        if (connect is HubDestination.Connect) {
            assertEquals(template.id, connect.template.id)
        }
    }

    @Test
    fun control_roundTripsProfileId() {
        assertEquals(
            HubDestination.Control("profile-42"),
            HubStackCodec.decode("ctl:profile-42")
        )
    }

    @Test
    fun learner_roundTripsWithoutResult() {
        val d = HubStackCodec.decode("learn")
        assertNotNull(d)
        // The result itself is not restorable — re-listening is correct.
        assertTrue(d is HubDestination.IrLearner)
        if (d is HubDestination.IrLearner) assertNull(d.learnResult)
    }

    @Test
    fun irLearner_encodesToLearnCode() {
        assertEquals("learn", HubStackCodec.encode(HubDestination.IrLearner(null)))
    }

    @Test
    fun liveObjectDestinationsAreNotEncodable() {
        val template = DeviceCatalog.all.first { it.category == DeviceCategory.XBOX }
        val sub = DeviceCategory.xboxSubcategories.first()
        assertNull(
            HubStackCodec.encode(
                HubDestination.AutomationEditor(automation = null)
            )
        )
        assertNull(
            HubStackCodec.encode(
                HubDestination.MacPairing(
                    com.elysium.nexus.ui.mac.DiscoveredHost(
                        id = "h1", name = "mac", type = com.elysium.nexus.ui.mac.HostType.MAC_DESKTOP,
                        signalStrength = 3, isOnline = true
                    )
                )
            )
        )
        // ConsolePicker with valid data round-trips (control path used in stores).
        assertNotNull(HubStackCodec.encode(HubDestination.ConsolePicker(DeviceCategory.XBOX, sub)))
    }

    @Test
    fun fullStack_roundTripsRealSession() {
        val stack = mutableListOf<HubDestination>()
        stack.add(HubDestination.Hub)
        stack.add(HubDestination.TvControls)
        stack.add(HubDestination.IrLearner(null))
        stack.add(HubDestination.TvControls)
        stack.add(HubDestination.Hub)

        val encoded = HubStackCodec.encodeStack(stack)
        val restored = HubStackCodec.decodeStack(encoded)
        assertEquals(stack, restored)
    }

    @Test
    fun emptyRestore_fallsBackToHub() {
        assertEquals(listOf(HubDestination.Hub), HubStackCodec.decodeStack(emptyList()))
        assertEquals(listOf(HubDestination.Hub), HubStackCodec.decodeStack(listOf("garbage")))
    }
}