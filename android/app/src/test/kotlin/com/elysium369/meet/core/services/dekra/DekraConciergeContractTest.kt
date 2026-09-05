package com.elysium369.meet.core.services.dekra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DekraConciergeContractTest {
    @Test
    fun `known safety concern forces tow`() {
        assertEquals(
            DekraTransportPlan.TOW_ONLY,
            DekraConciergePolicy.transportPlanFor(DekraVehicleCondition.SAFETY_CONCERN),
        )
        assertEquals(
            DekraTransportPlan.TOW_ONLY,
            DekraConciergePolicy.transportPlanFor(DekraVehicleCondition.IMMOBILIZED),
        )
    }

    @Test
    fun `request fails closed without every critical acknowledgement`() {
        val request = validRequest().copy(
            precheckAuthorized = false,
            independentResultAcknowledged = false,
        )

        val errors = DekraConciergePolicy.validate(request)

        assertTrue(DekraRequestValidationError.PRECHECK_AUTHORIZATION_REQUIRED in errors)
        assertTrue(DekraRequestValidationError.INDEPENDENT_RESULT_ACKNOWLEDGEMENT_REQUIRED in errors)
    }

    @Test
    fun `request rejects drive plan when vehicle is unsafe`() {
        val request = validRequest().copy(
            vehicleCondition = DekraVehicleCondition.SAFETY_CONCERN,
            transportPlan = DekraTransportPlan.DRIVE_AFTER_PRECHECK,
        )

        assertTrue(
            DekraRequestValidationError.UNSAFE_TRANSPORT_PLAN in DekraConciergePolicy.validate(request),
        )
    }

    @Test
    fun `valid request passes and privacy helpers mask identifiers`() {
        assertTrue(DekraConciergePolicy.validate(validRequest()).isEmpty())
        assertEquals("*************0042", DekraConciergePolicy.maskVin("KMHCG41DP5U120042"))
        assertEquals("****23", DekraConciergePolicy.maskPlate("ABC123"))
        assertFalse(DekraConciergePolicy.maskVin("KMHCG41DP5U120042")!!.contains("KMHCG"))
    }

    @Test
    fun `request rejects raw vin and plate in publication contract`() {
        val request = validRequest().copy(
            maskedVin = "KMHCG41DP5U120042",
            maskedPlate = "ABC123",
        )

        val errors = DekraConciergePolicy.validate(request)

        assertTrue(DekraRequestValidationError.VIN_NOT_MASKED in errors)
        assertTrue(DekraRequestValidationError.PLATE_NOT_MASKED in errors)
    }

    @Test
    fun `official knowledge covers every manual chapter`() {
        assertEquals(7, DekraInspectionKnowledge.officialInspectionSections.size)
        assertTrue(DekraInspectionKnowledge.officialInspectionSections.all { it.items.isNotEmpty() })
        assertTrue(DekraInspectionKnowledge.OFFICIAL_BOOKING_URL.startsWith("https://booking.dekra.com/"))
        assertTrue(DekraInspectionKnowledge.OFFICIAL_MANUAL_URL.startsWith("https://repositorio.mopt.go.cr/"))
    }

    @Test
    fun `only verified licensed transport providers can serve concierge request`() {
        val eligible = DekraProviderSnapshot("ride_driver", true, true, "B1-123456")
        assertTrue(DekraProviderEligibilityPolicy.isEligible(eligible))
        assertTrue(DekraProviderEligibilityPolicy.isEligible(eligible.copy(providerType = "tow_provider")))
        assertFalse(DekraProviderEligibilityPolicy.isEligible(eligible.copy(providerType = "mechanic")))
        assertFalse(DekraProviderEligibilityPolicy.isEligible(eligible.copy(verified = false)))
        assertFalse(DekraProviderEligibilityPolicy.isEligible(eligible.copy(licenseNumber = "")))
    }

    private fun validRequest() = DekraConciergeRequest(
        vehicleId = "vehicle-1",
        vehicleDisplayName = "Hyundai Accent 2005",
        maskedVin = "*************0042",
        maskedPlate = "****23",
        appointmentMode = DekraAppointmentMode.CONFIRMED,
        station = "DEKRA Alajuelita",
        appointmentDateTime = "2026-09-10 10:30",
        reservationCode = "CR000001234567",
        pickupZone = "San José",
        contactPhone = "8888-8888",
        vehicleCondition = DekraVehicleCondition.NORMAL,
        transportPlan = DekraTransportPlan.DRIVE_AFTER_PRECHECK,
        activeDtcs = emptyList(),
        notes = "",
        precheckAuthorized = true,
        custodyAuthorized = true,
        independentResultAcknowledged = true,
        officialFeeAcknowledged = true,
        stationRulesAcknowledged = true,
    )
}
