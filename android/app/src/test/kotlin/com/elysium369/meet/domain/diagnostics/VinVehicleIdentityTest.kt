package com.elysium369.meet.domain.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VinVehicleIdentityTest {
    @Test
    fun `normalizes a valid ecu vin without accepting forbidden characters`() {
        assertEquals("KMHDU46D05U123456", VinVehicleIdentity.normalize(" kmhdu46d05u123456 "))
        assertNull(VinVehicleIdentity.normalize("KMHDU46D0IU123456"))
        assertNull(VinVehicleIdentity.normalize("N/A"))
    }

    @Test
    fun `same owner and vin always resolve to the same durable vehicle id`() {
        val first = VinVehicleIdentity.stableVehicleId("user-a", "KMHDU46D05U123456")
        val later = VinVehicleIdentity.stableVehicleId("user-a", "kmhdu46d05u123456")

        assertEquals(first, later)
    }

    @Test
    fun `different cars and different users can never share the generated identity`() {
        val firstCar = VinVehicleIdentity.stableVehicleId("user-a", "KMHDU46D05U123456")
        val secondCar = VinVehicleIdentity.stableVehicleId("user-a", "1HGCM82633A004352")
        val otherUser = VinVehicleIdentity.stableVehicleId("user-b", "KMHDU46D05U123456")

        assertNotEquals(firstCar, secondCar)
        assertNotEquals(firstCar, otherUser)
    }
}
