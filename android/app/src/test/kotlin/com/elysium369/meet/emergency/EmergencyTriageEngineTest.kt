package com.elysium369.meet.emergency

import com.elysium369.meet.core.domain.VehicleContext
import org.junit.Assert.*
import org.junit.Test

class EmergencyTriageEngineTest {

    private val sampleContext = VehicleContext(
        vehicleId = "V-001",
        ownerPrincipalId = "USER-001",
        make = "Hyundai",
        model = "Accent",
        year = 2005
    )

    @Test
    fun testAccidentWithInjuriesEscalatesTo911() {
        val session = EmergencyTriageEngine.initiateSession(sampleContext, EmergencyType.ACCIDENT)
        val updatedSteps = session.steps.map {
            if (it.stepId == "STEP_INJURIES") it.copy(selectedOptionIndex = 1) // Injured
            else it
        }
        val resolution = EmergencyTriageEngine.evaluateTriage(session.copy(steps = updatedSteps))

        assertEquals(EmergencyResolution.CALL_EMERGENCY_SERVICES, resolution)
    }

    @Test
    fun testBatteryWithJumpEquipmentSuggestsSelfHelp() {
        val session = EmergencyTriageEngine.initiateSession(sampleContext, EmergencyType.BATTERY)
        val updatedSteps = session.steps.map {
            if (it.stepId == "STEP_JUMP") it.copy(selectedOptionIndex = 0) // Has cables
            else it
        }
        val resolution = EmergencyTriageEngine.evaluateTriage(session.copy(steps = updatedSteps))

        assertEquals(EmergencyResolution.SELF_HELP_GUIDE, resolution)
    }
}
