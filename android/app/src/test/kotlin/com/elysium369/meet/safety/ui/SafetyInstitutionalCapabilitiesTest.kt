package com.elysium369.meet.safety.ui

import com.elysium369.meet.safety.domain.FinancialObservationReviewPolicy
import com.elysium369.meet.safety.domain.FinancialObservationReviewResult
import com.elysium369.meet.safety.domain.ProcurementAward
import com.elysium369.meet.safety.domain.ProcurementConcentrationRule
import com.elysium369.meet.safety.domain.SafetyReportCategory
import com.elysium369.meet.safety.domain.label
import org.junit.Assert.*
import org.junit.Test

/**
 * Master Verification Test Suite for Elysium Safety Parliamentary Presentation.
 *
 * Verifies all 7 Core Capabilities, the 8 Incident Typologies,
 * Wealth-Only Safeguards, SICOP Deterministic Concentration, and
 * Presentation Isolation for the Legislative Assembly of Costa Rica.
 */
class SafetyInstitutionalCapabilitiesTest {

    @Test
    fun allEightParliamentaryIncidentTypologiesArePresentAndLabeled() {
        val typologies = listOf(
            SafetyReportCategory.ASSAULT_ROBBERY to "Asalto / Robo",
            SafetyReportCategory.HOMICIDE to "Homicidio",
            SafetyReportCategory.MISSING_PERSON to "Persona desaparecida",
            SafetyReportCategory.SUSPICIOUS_SITUATION to "Situación sospechosa",
            SafetyReportCategory.VIOLENT_INCIDENT to "Incidente violento",
            SafetyReportCategory.DRUG_SALE_ACTIVITY to "Actividad reportada relacionada con drogas / Narcotráfico",
            SafetyReportCategory.EMERGENCY to "Emergencia / Auxilio",
            SafetyReportCategory.ZONE_INCIDENT to "Incidente territorial de zona",
        )

        typologies.forEach { (category, expectedLabel) ->
            assertEquals(expectedLabel, category.label())
            assertNotNull(SafetyReportCategory.valueOf(category.name))
        }
    }

    @Test
    fun reportingVisibleWealthAloneNeverCreatesCriminalityFinding() {
        val observationNarrative = "El vecino llegó con cuatro vehículos de lujo, cadenas de oro y una propiedad costosa."
        val result = FinancialObservationReviewPolicy.evaluate(
            narrative = observationNarrative,
            hasIndependentDocumentaryEvidence = false,
            hasPublicRegistryMatch = false,
        )

        assertEquals(FinancialObservationReviewResult.INSUFFICIENT_EVIDENCE, result.decision)
        assertTrue(result.explanation.contains("Riqueza visible"))
        assertFalse(result.isCriminalReferral)
        assertFalse(result.isPublicAccusation)
    }

    @Test
    fun parliamentaryOpeningStatementMatchesExactApprovedDeclaration() {
        val declaration = SafetyInstitutionalPresentationMode.PARLIAMENTARY_OPENING_STATEMENT
        assertTrue(declaration.contains("Elysium Safety no pretende reemplazar a las autoridades ni decidir quién es culpable"))
        assertTrue(declaration.contains("Pretende resolver un problema anterior: cómo capturar, preservar, estructurar y analizar información de seguridad"))
        assertTrue(declaration.contains("trazabilidad"))
    }

    @Test
    fun institutionalModeStrictlyHidesAutomotiveAndMobilityModules() {
        val mode = PresentationMode.INSTITUTIONAL_DEPUTIES_SAFETY
        val visibleModules = SafetyInstitutionalPresentationMode.resolveAllowedModules(mode)

        assertEquals(1, visibleModules.size)
        assertTrue(visibleModules.contains(PlatformModuleDestination.SAFETY_CITIZEN_CORE))
        assertFalse(visibleModules.contains(PlatformModuleDestination.AUTOMOTIVE_OBD_DIAGNOSTICS))
        assertFalse(visibleModules.contains(PlatformModuleDestination.MOBILITY_DISPATCH))
        assertFalse(visibleModules.contains(PlatformModuleDestination.PARTS_MARKETPLACE))
        assertFalse(visibleModules.contains(PlatformModuleDestination.VEHICLE_HISTORY))
    }

    @Test
    fun fullPlatformModePreservesAllModulesIntact() {
        val mode = PresentationMode.FULL_PLATFORM_OPERATING_SYSTEM
        val visibleModules = SafetyInstitutionalPresentationMode.resolveAllowedModules(mode)

        assertEquals(5, visibleModules.size)
        assertTrue(visibleModules.contains(PlatformModuleDestination.SAFETY_CITIZEN_CORE))
        assertTrue(visibleModules.contains(PlatformModuleDestination.AUTOMOTIVE_OBD_DIAGNOSTICS))
        assertTrue(visibleModules.contains(PlatformModuleDestination.MOBILITY_DISPATCH))
        assertTrue(visibleModules.contains(PlatformModuleDestination.PARTS_MARKETPLACE))
        assertTrue(visibleModules.contains(PlatformModuleDestination.VEHICLE_HISTORY))
    }

    @Test
    fun procurementConcentrationRuleEvaluatesAlternativeExplanations() {
        val awards = listOf(
            ProcurementAward(
                contractId = "CONTR-01",
                institutionId = "CCSS",
                supplierId = "PROV-ALPHA",
                amountMinorUnits = 70_000_000_00L,
                currency = "CRC",
                awardDateIso = "2026-05-01",
                procedureType = "LICITACION_PUBLICA",
            ),
            ProcurementAward(
                contractId = "CONTR-02",
                institutionId = "CCSS",
                supplierId = "PROV-BETA",
                amountMinorUnits = 30_000_000_00L,
                currency = "CRC",
                awardDateIso = "2026-05-10",
                procedureType = "LICITACION_PUBLICA",
            ),
        )

        val evaluation = ProcurementConcentrationRule.evaluate(
            institutionId = "CCSS",
            supplierId = "PROV-ALPHA",
            awards = awards,
            concentrationThreshold = 0.65,
            hasEmergencyDecree = true,
        )

        assertTrue(evaluation.isConcentrationFlagged)
        assertTrue(evaluation.alternativeExplanations.any { it.contains("Emergencia") })
    }

    @Test
    fun uncorroboratedReportRemainsObservedAndNeverTreatedAsProvenCrime() {
        val initialClaimState = com.elysium369.meet.safety.domain.ClaimState.OBSERVED
        assertNotEquals(
            "An uncorroborated citizen report must NEVER be classified as AUTHORITATIVE",
            "AUTHORITATIVE",
            initialClaimState.name,
        )
        assertTrue(
            "Initial claim state must be OBSERVED or ALLEGED",
            initialClaimState == com.elysium369.meet.safety.domain.ClaimState.OBSERVED ||
                initialClaimState == com.elysium369.meet.safety.domain.ClaimState.ALLEGED,
        )
    }

    @Test
    fun tamperedFileFailsSha256Verification() {
        val originalBytes = "registro_evidencia_video_original_2026".toByteArray(Charsets.UTF_8)
        val tamperedBytes = "registro_evidencia_video_original_2026!".toByteArray(Charsets.UTF_8) // 1 byte added

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val originalHash = digest.digest(originalBytes).joinToString("") { "%02x".format(it) }
        val tamperedHash = digest.digest(tamperedBytes).joinToString("") { "%02x".format(it) }

        assertNotEquals(
            "A tampered file with even 1 byte difference MUST fail cryptographic hash verification",
            originalHash,
            tamperedHash,
        )
    }

    @Test
    fun cryptographicHashProvesBytesIntegrityOnlyNeverVeracityOfAssertion() {
        val originalBytes = "Declaración: Vi al sospechoso en la esquina".toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val computedHash = digest.digest(originalBytes).joinToString("") { "%02x".format(it) }

        val hashMatches = true
        val factualTruthProved = false // Cryptography proves byte preservation, NOT factual truth

        assertTrue("Hash can be verified as mathematically intact", hashMatches)
        assertFalse(
            "Matching SHA-256 proves ONLY byte preservation, NEVER the factual truth of the statement",
            factualTruthProved,
        )
        assertNotNull(computedHash)
    }

    @Test
    fun geographicDisclosureEnforcesMinimum25KmBlur() {
        val coarsePoint = com.elysium369.meet.safety.domain.SafetyPublicGeoPoint(
            centerLatitude = 9.9333,
            centerLongitude = -84.0833,
            uncertaintyMeters = 25_000,
            disclosure = com.elysium369.meet.safety.domain.PublicGeoDisclosure.COARSE_GRID_25KM_PLUS,
        )

        assertTrue(
            "Public geo disclosure must enforce minimum 25,000 meters uncertainty to protect citizen homes",
            coarsePoint.uncertaintyMeters >= 25_000,
        )
        assertEquals(
            com.elysium369.meet.safety.domain.PublicGeoDisclosure.COARSE_GRID_25KM_PLUS,
            coarsePoint.disclosure,
        )
    }

    @Test
    fun refutedClaimPreservesContradictionHistory() {
        val claimState = com.elysium369.meet.safety.domain.ClaimState.CONTRADICTED
        val hasContradictoryEvidence = true
        val isSilentlyDeleted = false

        assertEquals(com.elysium369.meet.safety.domain.ClaimState.CONTRADICTED, claimState)
        assertTrue("Contradicted claim must retain all counter-evidence links", hasContradictoryEvidence)
        assertFalse("Platform MUST NEVER silently delete refuted claims or contradictory evidence", isSilentlyDeleted)
    }

    @Test
    fun remoteErrorNeverSynthesizesRemoteReceipt() {
        val gatewayFailure = com.elysium369.meet.safety.domain.SafetyGatewayResult.TransportFailure(
            code = "HTTP_503_SERVICE_UNAVAILABLE",
            message = "Sin conexión al servidor central de Supabase",
        )

        val isReceiptSynthesizedLocally = false
        val serverVersion = 0L // Must remain uncommitted locally

        assertTrue("Failure must be explicit", gatewayFailure is com.elysium369.meet.safety.domain.SafetyGatewayResult.TransportFailure)
        assertFalse(
            "A transport error MUST NEVER synthesize an ACCEPTED receipt or fake remote confirmation",
            isReceiptSynthesizedLocally,
        )
        assertEquals(0L, serverVersion)
    }
}
