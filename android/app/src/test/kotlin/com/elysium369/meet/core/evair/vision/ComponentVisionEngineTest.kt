package com.elysium369.meet.core.evair.vision

import com.elysium369.meet.core.evair.domain.VehicleIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentVisionEngineTest {

    private val visionEngine = ComponentVisionEngine()
    private val hyundaiIdentity = VehicleIdentity(
        vehicleId = "VIN_ACCENT_2005",
        vin = "KMH_ACCENT_2005",
        make = "Hyundai",
        model = "Accent",
        year = 2005,
        engineType = "1.6L Alpha II G4ED",
        transmissionType = "AT",
        label = "Accent 2005"
    )

    @Test
    fun `identifies TPS sensor and associated inspection parameters`() = runBlocking {
        val match = visionEngine.identifyComponent(hyundaiIdentity, "¿Dónde está el sensor TPS?")
        assertNotNull(match)
        assertEquals("SENSOR_TPS", match?.canonicalId)
        assertTrue(match?.confidence ?: 0.0 >= 0.90)
        assertTrue(match?.isVerifiedForVehicle == true)
        assertTrue(match?.associatedPids?.contains("0111") == true)
    }

    @Test
    fun `identifies MAP sensor on manifold`() = runBlocking {
        val match = visionEngine.identifyComponent(hyundaiIdentity, "revisar sensor MAP")
        assertNotNull(match)
        assertEquals("SENSOR_MAP", match?.canonicalId)
        assertTrue(match?.associatedPids?.contains("010B") == true)
    }

    @Test
    fun `returns null for unknown arbitrary queries to prevent hallucinations`() = runBlocking {
        val match = visionEngine.identifyComponent(hyundaiIdentity, "reactor de fusión espacial")
        assertNull(match)
    }
}
