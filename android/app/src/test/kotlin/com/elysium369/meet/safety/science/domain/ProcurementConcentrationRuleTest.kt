package com.elysium369.meet.safety.science.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcurementConcentrationRuleTest {

    private val targetBuyer = "Municipalidad de San José"
    private val targetVendor = "3-101-778899"

    @Test
    fun sampleSizeBelowThresholdProducesInsufficientData() {
        val singleAward = createAward(
            expediente = "2024LN-000001-0001101142",
            buyer = targetBuyer,
            vendor = targetVendor,
            amount = 1000000L,
        )

        val result = ProcurementConcentrationRule.evaluate(
            buyerInstitution = targetBuyer,
            vendorTaxId = targetVendor,
            institutionAwards = listOf(singleAward),
        )

        assertEquals(AnomalySignalDisposition.INSUFFICIENT_DATA, result.disposition)
        assertFalse(result.eligibleForHumanReview)
        assertTrue(result.mandatoryAlternativeHypotheses.isEmpty())
    }

    @Test
    fun unverifiedSourceProvenanceProducesInsufficientData() {
        val awards = listOf(
            createAward("2024LN-000001-0001101142", targetBuyer, targetVendor, 1000000L),
            createAward("2024LN-000002-0001101142", targetBuyer, targetVendor, 1500000L),
            createAward("2024LN-000003-0001101142", targetBuyer, targetVendor, 2000000L),
            createAward("2024LN-000004-0001101142", targetBuyer, "3-101-000000", 500000L),
        ).map { award ->
            award.copy(
                evidenceReference = award.evidenceReference.copy(sourceVerified = false),
            )
        }

        val result = ProcurementConcentrationRule.evaluate(
            buyerInstitution = targetBuyer,
            vendorTaxId = targetVendor,
            institutionAwards = awards,
        )

        assertEquals(AnomalySignalDisposition.INSUFFICIENT_DATA, result.disposition)
        assertFalse(result.eligibleForHumanReview)
        assertTrue(result.deterministicFormulaExplanation.contains("provenance", ignoreCase = true))
    }

    @Test
    fun concentrationExceedingThresholdTriggersSignalWithMandatoryHypotheses() {
        val awards = listOf(
            createAward("2024LN-000001-0001101142", targetBuyer, targetVendor, 1000000L),
            createAward("2024LN-000002-0001101142", targetBuyer, targetVendor, 1500000L),
            createAward("2024LN-000003-0001101142", targetBuyer, targetVendor, 2000000L),
            createAward("2024LN-000004-0001101142", targetBuyer, "3-101-000000", 500000L),
        )

        val result = ProcurementConcentrationRule.evaluate(
            buyerInstitution = targetBuyer,
            vendorTaxId = targetVendor,
            institutionAwards = awards,
            thresholdPercent = 70.0,
        )

        assertEquals(AnomalySignalDisposition.CONCENTRATION_SIGNAL_DETECTED, result.disposition)
        assertEquals(75.0, result.concentrationRatioPercent, 0.01)
        assertEquals(3, result.vendorAwardsCount)
        assertEquals(4, result.totalInstitutionAwardsCount)
        assertEquals(4500000L, result.totalVendorAmountMinorUnits)
        assertTrue(result.eligibleForHumanReview)

        // Verifies non-negotiable invariant: signal MUST provide alternative legitimate hypotheses
        assertTrue(result.mandatoryAlternativeHypotheses.isNotEmpty())
        assertTrue(
            result.mandatoryAlternativeHypotheses.any {
                it.contains("Proveedor exclusivo", ignoreCase = true)
            },
        )
    }

    @Test
    fun normalDistributionDoesNotTriggerSignal() {
        val awards = listOf(
            createAward("2024LN-000001-0001101142", targetBuyer, targetVendor, 1000000L),
            createAward("2024LN-000002-0001101142", targetBuyer, "3-101-111111", 1000000L),
            createAward("2024LN-000003-0001101142", targetBuyer, "3-101-222222", 1000000L),
            createAward("2024LN-000004-0001101142", targetBuyer, "3-101-333333", 1000000L),
        )

        val result = ProcurementConcentrationRule.evaluate(
            buyerInstitution = targetBuyer,
            vendorTaxId = targetVendor,
            institutionAwards = awards,
            thresholdPercent = 70.0,
        )

        assertEquals(AnomalySignalDisposition.NORMAL_DISTRIBUTION, result.disposition)
        assertEquals(25.0, result.concentrationRatioPercent, 0.01)
        assertFalse(result.eligibleForHumanReview)
        assertTrue(result.mandatoryAlternativeHypotheses.isEmpty())
    }

    private fun createAward(
        expediente: String,
        buyer: String,
        vendor: String,
        amount: Long,
    ): NormalizedProcurementRecord {
        val raw = PublicProcurementRawRecord(
            expedienteNumber = expediente,
            procurementType = PublicProcurementType.TENDER,
            buyerInstitution = buyer,
            vendorTaxId = vendor,
            vendorName = "Vendor Test S.A.",
            amountMinorUnits = amount,
            currencyCode = "CRC",
            awardDateIso = "2024-01-01T00:00:00Z",
            sourceSystem = "public-records-test",
            sourceDocumentUrl = "https://records.example.test/procurement/$expediente",
            independenceGroup = "official-records-test",
            lawfullyObtained = true,
            sourceVerified = true,
            hasSpecificDocumentedDiscrepancy = true,
            statutoryLegalBasis = "Test legal basis",
        )
        return PublicProcurementRecordNormalizer.normalize(raw).getOrThrow()
    }
}
