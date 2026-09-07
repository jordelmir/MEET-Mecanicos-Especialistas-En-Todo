package com.elysium369.meet.ride.accessibility

import org.junit.Assert.*
import org.junit.Test

class RideAccessibilityEngineTest {

    @Test
    fun `create accessibility profile`() {
        val e = RideAccessibilityEngine()
        val p = e.createProfile("user1", setOf(AccessibilityNeed.WHEELCHAIR_STANDARD))
        assertTrue(p.needsAccessibleVehicle)
        assertEquals(1, p.needs.size)
    }

    @Test
    fun `profile with companion`() {
        val e = RideAccessibilityEngine()
        val p = e.createProfile("user1",
            needs = setOf(AccessibilityNeed.ELDERLY_ASSISTANCE),
            companionName = "Ana García",
            companionPhone = "+506 8888-1234",
            companionRelationship = CompanionRelationship.CHILD,
        )
        assertTrue(p.hasCompanion)
        assertEquals("Ana García", p.companionName)
    }

    @Test
    fun `child seat detection`() {
        val e = RideAccessibilityEngine()
        val p = e.createProfile("user1", setOf(AccessibilityNeed.CHILD_SEAT_TODDLER))
        assertTrue(p.needsChildSeat)
        assertFalse(p.needsAccessibleVehicle)
    }

    @Test
    fun `certify driver with equipment`() {
        val e = RideAccessibilityEngine()
        val cert = e.certifyDriver("d1",
            trainedNeeds = setOf(AccessibilityNeed.VISUAL_IMPAIRMENT, AccessibilityNeed.HEARING_IMPAIRMENT),
            vehicleEquipment = setOf(VehicleEquipment.WHEELCHAIR_RAMP, VehicleEquipment.WHEELCHAIR_ANCHOR),
        )
        assertTrue(cert.isValid)
        assertTrue(cert.canServe(setOf(AccessibilityNeed.WHEELCHAIR_STANDARD)))
    }

    @Test
    fun `match compatible driver to passenger`() {
        val e = RideAccessibilityEngine()
        e.createProfile("p1", setOf(AccessibilityNeed.WHEELCHAIR_STANDARD))
        e.certifyDriver("d1",
            trainedNeeds = setOf(AccessibilityNeed.WHEELCHAIR_STANDARD),
            vehicleEquipment = setOf(VehicleEquipment.WHEELCHAIR_RAMP, VehicleEquipment.WHEELCHAIR_ANCHOR),
        )
        val result = e.matchDriverToPassenger("d1", "p1")
        assertTrue(result.isCompatible)
        assertTrue(result.matchScore > 0.5)
    }

    @Test
    fun `incompatible driver missing equipment`() {
        val e = RideAccessibilityEngine()
        e.createProfile("p1", setOf(AccessibilityNeed.WHEELCHAIR_ELECTRIC))
        e.certifyDriver("d1",
            trainedNeeds = emptySet(),
            vehicleEquipment = emptySet(), // No equipment!
        )
        val result = e.matchDriverToPassenger("d1", "p1")
        assertFalse(result.isCompatible)
        assertTrue(result.missingEquipment.isNotEmpty())
    }

    @Test
    fun `no profile means universal compatibility`() {
        val e = RideAccessibilityEngine()
        e.certifyDriver("d1", emptySet(), emptySet())
        val result = e.matchDriverToPassenger("d1", "unknown-passenger")
        assertTrue(result.isCompatible)
        assertEquals(1.0, result.matchScore, 0.01)
    }

    @Test
    fun `caregiver sharing`() {
        val e = RideAccessibilityEngine()
        e.createProfile("p1", setOf(AccessibilityNeed.ELDERLY_ASSISTANCE))
        assertTrue(e.enableCaregiverSharing("p1", "caregiver-1"))
        val updated = e.getProfile("p1")!!
        assertTrue(updated.caregiverShareEnabled)
        assertEquals("caregiver-1", updated.caregiverId)
    }

    @Test
    fun `find compatible drivers`() {
        val e = RideAccessibilityEngine()
        e.createProfile("p1", setOf(AccessibilityNeed.WHEELCHAIR_STANDARD))
        e.certifyDriver("d1", emptySet(),
            setOf(VehicleEquipment.WHEELCHAIR_RAMP, VehicleEquipment.WHEELCHAIR_ANCHOR))
        e.certifyDriver("d2", emptySet(), emptySet()) // No equipment
        val compatible = e.findCompatibleDrivers("p1")
        assertEquals(1, compatible.size)
        assertEquals("d1", compatible[0])
    }

    @Test
    fun `display labels are in Spanish`() {
        assertEquals("Silla de ruedas", AccessibilityNeed.WHEELCHAIR_STANDARD.displayLabel)
        assertEquals("Discapacidad visual", AccessibilityNeed.VISUAL_IMPAIRMENT.displayLabel)
        assertEquals("Solo texto", CommunicationPreference.TEXT_ONLY.displayLabel)
    }

    @Test
    fun `profile summary describes needs`() {
        val e = RideAccessibilityEngine()
        val p = e.createProfile("u1", setOf(
            AccessibilityNeed.VISUAL_IMPAIRMENT,
            AccessibilityNeed.SERVICE_ANIMAL,
        ), communicationPref = CommunicationPreference.TEXT_ONLY)
        assertTrue(p.summary.contains("Discapacidad visual"))
        assertTrue(p.summary.contains("Animal de servicio"))
        assertTrue(p.summary.contains("Solo texto"))
    }
}
