package com.elysium369.meet

import com.elysium369.meet.core.money.Money
import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.services.serviceos.*
import org.junit.Assert.*
import org.junit.Test

class ServiceOperatingSystemTest {

    @Test
    fun testStructuredServiceOfferTotalCost() {
        val offer = StructuredServiceOffer(
            requestId = "REQ-1",
            providerId = "PROV-1",
            providerName = "Taller Motortech",
            providerRating = 4.8,
            providerVerifiedCasesCount = 17,
            technicalHypothesis = "Posible falla de bobina de encendido cilindro 1",
            proposedScopeOfWork = "Diagnóstico osciloscópico + reemplazo de bobina",
            laborCost = Money(25000L, CurrencyCode.CRC),
            diagnosticCost = Money(10000L, CurrencyCode.CRC),
            partsIncluded = false,
            estimatedDurationHours = 1.5,
            estimatedArrivalOrStartMs = System.currentTimeMillis() + 3600000L,
            warrantyDays = 90,
            warrantyTerms = "90 días de garantía sobre mano de obra",
            modality = ServiceModality.WORKSHOP_FACILITY,
            roadDistanceKm = 2.4
        )

        assertEquals(Money(35000L, CurrencyCode.CRC), offer.totalEstimatedCost)
        assertEquals(35000L, offer.totalEstimatedCost.amountMinor)
    }

    @Test
    fun testProviderMatchingEngineRecommendations() {
        val request = ServiceRequestV2(
            urgency = ServiceRequestUrgency.URGENT_BREAKDOWN,
            mobility = MobilityCondition.IMMOBILIZED_TOW_REQUIRED,
            preferredModality = ServiceModality.WORKSHOP_FACILITY,
            locationZone = PrivacyLocationZone(
                approximateZoneName = "Alajuelita",
                approximateRadiusKm = 4.0,
                latitude = 9.9000,
                longitude = -84.1000
            ),
            evidence = ServiceEvidencePayload(
                vehicleId = "V_HYUNDAI",
                vehicleDisplayName = "HYUNDAI ACCENT VERNA 2005",
                maskedVin = "*******5678",
                activeDtcs = listOf("P0301"),
                userReportedSymptom = "El carro tironea y pierde fuerza",
                userSymptomCategory = "Luz Check Engine / Falla motor"
            )
        )

        val specializedProvider = ProviderOrganization(
            id = "P1",
            legalName = "Autoservicio del Valle S.A.",
            commercialName = "Autoservicio del Valle",
            type = ProviderType.WORKSHOP,
            isVerifiedByMeet = true,
            physicalAddress = "Alajuelita Centro",
            latitude = 9.9050,
            longitude = -84.1020,
            supportedMakes = listOf("HYUNDAI", "KIA", "TOYOTA"),
            totalVerifiedRepairsCount = 25,
            onTimeRatePercent = 95,
            isEmergencyModeActive = true
        )

        val match = ProviderMatchingEngine.evaluateMatch(request, specializedProvider)

        assertTrue(match.totalScorePercent >= 80)
        assertTrue(match.isRecommended)
        assertTrue(match.positiveSignals.any { it.contains("HYUNDAI") })
        assertTrue(match.positiveSignals.any { it.contains("reparaciones verificadas") })
        assertTrue(match.positiveSignals.any { it.contains("Modo Emergencia") })
    }

    @Test
    fun testChangeOrderCalculation() {
        val changeOrder = ChangeOrder(
            workOrderId = "WO-100",
            discoveredFinding = "Soporte de motor quebrado",
            description = "Se detectó soporte roto durante desmontaje de alternador",
            laborDelta = Money(15000L, CurrencyCode.CRC),
            partsDelta = Money(22000L, CurrencyCode.CRC)
        )

        assertEquals(Money(37000L, CurrencyCode.CRC), changeOrder.totalAdditionalCost)
        assertEquals(ChangeOrderStatus.PENDING_CUSTOMER_APPROVAL, changeOrder.status)
    }

    @Test
    fun testCredentialExpirationLogic() {
        val expiredCert = CredentialCertificate(
            documentType = "Póliza de Responsabilidad Civil",
            issuer = "INS",
            validUntilMs = System.currentTimeMillis() - 86400000L, // ayer
            isVerified = true
        )
        assertTrue(expiredCert.isExpired)

        val validCert = CredentialCertificate(
            documentType = "Certificación ASE Master",
            issuer = "ASE",
            validUntilMs = System.currentTimeMillis() + 8640000000L, // futuro
            isVerified = true
        )
        assertFalse(validCert.isExpired)
    }
}
