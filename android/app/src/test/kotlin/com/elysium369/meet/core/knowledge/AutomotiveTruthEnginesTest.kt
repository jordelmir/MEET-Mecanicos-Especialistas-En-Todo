package com.elysium369.meet.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomotiveTruthEnginesTest {

    @Test
    fun `verified measurement requires source unit condition instrument and tolerance`() {
        val spec = MeasurementSpecification(
            measurementId = "m_ground_drop",
            quantityType = "VOLTAGE_DROP",
            nominalValue = 0.1,
            unitCode = "",
            measurementCondition = "",
            requiredInstrument = "",
            tolerance = "",
            sourceClaimId = null,
            verificationStatus = MeasurementVerificationStatus.VERIFIED
        )

        val issues = MeasurementSpecValidator().validate(spec)

        assertTrue(issues.all { it.severity == KnowledgeIssueSeverity.BLOCKING })
        assertTrue(issues.map { it.code }.contains("VERIFIED_MEASUREMENT_SOURCE_MISSING"))
        assertTrue(issues.map { it.code }.contains("MEASUREMENT_UNIT_MISSING"))
        assertTrue(issues.map { it.code }.contains("MEASUREMENT_CONDITION_MISSING"))
    }

    @Test
    fun `reviewed measurement is usable without pending disclaimer`() {
        val spec = MeasurementSpecification(
            measurementId = "m_reviewed",
            quantityType = "VOLTAGE",
            minimumValue = 12.0,
            maximumValue = 13.0,
            unitCode = "V",
            measurementCondition = "KOEO at battery posts",
            requiredInstrument = "calibrated multimeter",
            tolerance = "as stated in source",
            sourceClaimId = "claim_reviewed_voltage",
            verificationStatus = MeasurementVerificationStatus.VERIFIED
        )

        assertTrue(MeasurementSpecValidator().validate(spec).isEmpty())
        assertNull(MeasurementSpecValidator().displayDisclaimer(spec))
    }

    @Test
    fun `reference vehicle claim never becomes target vehicle fact`() {
        val claim = claim(
            id = "claim_ocs_reference",
            applicability = ApplicabilityStatus.REFERENCE_VEHICLE_ONLY,
            scope = KnowledgeScopeType.REFERENCE_VEHICLE
        )

        val resolution = AutomotiveApplicabilityResolver().resolve(
            claim,
            ApplicabilityEvidence(
                vinConfirmed = true,
                oemConfirmed = true,
                visualConfirmed = true,
                physicalEvidenceIds = setOf("all")
            )
        )

        assertEquals(ApplicabilityStatus.REFERENCE_VEHICLE_ONLY, resolution.effectiveStatus)
        assertFalse(resolution.canUseAsVehicleFact)
    }

    @Test
    fun `required confirmations downgrade presence to physical verification`() {
        val claim = claim(
            id = "claim_variant_sensor",
            applicability = ApplicabilityStatus.PRESENT_CONDITIONAL,
            scope = KnowledgeScopeType.TARGET_VARIANT
        ).copy(
            requiresVinConfirmation = true,
            requiresOemConfirmation = true
        )

        val resolution = AutomotiveApplicabilityResolver().resolve(claim)

        assertEquals(ApplicabilityStatus.VERIFY_PHYSICALLY, resolution.effectiveStatus)
        assertFalse(resolution.canUseAsVehicleFact)
        assertEquals(listOf("VIN", "OEM"), resolution.missingEvidence)
    }

    @Test
    fun `presence and absence for same variant creates explicit conflict`() {
        val claims = listOf(
            claim("claim_maf_present", ApplicabilityStatus.PRESENT_DOCUMENTED),
            claim("claim_maf_absent", ApplicabilityStatus.ABSENT_DOCUMENTED)
        )

        val conflicts = KnowledgeConflictDetector().detect(claims)

        assertEquals(1, conflicts.size)
        assertEquals(setOf("claim_maf_present", "claim_maf_absent"), conflicts.single().claimIds.toSet())
    }

    @Test
    fun `DTC remains a hypothesis until all evidence is complete`() {
        val decision = DiagnosticTruthEngine().evaluate(
            DiagnosticClaimRequest(
                dtcCode = "P0230",
                componentName = "bomba de combustible",
                requiredEvidence = setOf("relay", "fuse", "power", "ground", "pressure", "current"),
                completedEvidence = setOf("relay", "fuse"),
                confidence = ConfidenceLevel.MEDIUM
            )
        )

        assertFalse(decision.replacementAllowed)
        assertTrue(decision.statement.contains("no confirma la pieza danada"))
        assertTrue(decision.statement.contains("Requiere prueba fisica"))
        assertTrue(decision.missingEvidence.contains("pressure"))
    }

    @Test
    fun `high confidence cannot authorize replacement without required physical evidence`() {
        val decision = DiagnosticTruthEngine().evaluate(
            DiagnosticClaimRequest(
                dtcCode = "P0230",
                componentName = "bomba de combustible",
                requiredEvidence = emptySet(),
                completedEvidence = emptySet(),
                confidence = ConfidenceLevel.HIGH
            )
        )

        assertFalse(decision.replacementAllowed)
        assertTrue(decision.statement.contains("no confirma la pieza danada"))
    }

    private fun claim(
        id: String,
        applicability: ApplicabilityStatus,
        scope: KnowledgeScopeType = KnowledgeScopeType.TARGET_VARIANT
    ) = TechnicalClaim(
        claimId = id,
        subjectId = "sensor_maf",
        predicate = "is_present",
        value = applicability.name,
        vehicleScopeId = "hyundai_accent_verna_2005_1_6_at",
        scopeType = scope,
        applicability = applicability,
        confidence = ConfidenceLevel.HIGH,
        sourceCitationId = "source_reviewed"
    )
}
