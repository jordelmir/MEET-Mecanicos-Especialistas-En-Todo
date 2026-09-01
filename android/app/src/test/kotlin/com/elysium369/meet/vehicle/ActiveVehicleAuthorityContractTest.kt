package com.elysium369.meet.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveVehicleAuthorityContractTest {
    @Test
    fun `navigation and obd session lifecycle are not active vehicle change reasons`() {
        val reasons = ActiveVehicleChangeReason.entries.map { it.name }.toSet()
        assertFalse("Navigation must never mutate active vehicle", "NAVIGATION" in reasons)
        assertFalse("OBD disconnect must never clear active vehicle", "OBD_DISCONNECTED" in reasons)
        assertFalse("Screen disposal must never clear active vehicle", "SCREEN_DISPOSED" in reasons)
    }

    @Test
    fun `all mutations have explicit auditable reasons`() {
        assertTrue(ActiveVehicleChangeReason.USER_SELECTED in ActiveVehicleChangeReason.entries)
        assertTrue(ActiveVehicleChangeReason.VEHICLE_DELETED in ActiveVehicleChangeReason.entries)
        assertTrue(ActiveVehicleChangeReason.OWNER_CHANGED in ActiveVehicleChangeReason.entries)
        assertTrue(ActiveVehicleChangeReason.RESTORED in ActiveVehicleChangeReason.entries)
    }
}
