package com.elysium369.meet.safety.science.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SicopProcurementSourceAdapterTest {

    private val sampleValidRecord = SicopRawRecord(
        expedienteNumber = "2024LN-000001-0001101142",
        procurementType = SicopProcurementType.LICITACION_MAYOR,
        buyerInstitution = "Caja Costarricense de Seguro Social",
        vendorTaxId = "3-101-123456",
        vendorName = "Suministros Hospitalarios de Costa Rica S.A.",
        amountMinorUnits = 15000000000L, // 150M CRC
        currencyCode = "CRC",
        awardDateIso = "2024-05-10T14:30:00Z",
        officialPortalUrl = "https://www.sicop.go.cr/proc/2024LN-000001-0001101142",
    )

    @Test
    fun validRecordNormalizesSuccessfullyWithCanonicalHash() {
        val result = SicopProcurementSourceAdapter.normalize(sampleValidRecord)

        assertTrue(result.isSuccess)
        val normalized = result.getOrThrow()

        assertEquals("SICOP-CR-2024LN-000001-0001101142", normalized.canonicalRecordId)
        assertEquals(64, normalized.contentSha256.length)
        assertTrue(normalized.contentSha256.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(SicopProcurementSourceAdapter.STATUTORY_LEGAL_BASIS, normalized.statutoryLegalBasis)
        assertEquals(FinancialEvidenceKind.PUBLIC_PROCUREMENT_RECORD, normalized.evidenceReference.kind)
        assertTrue(normalized.evidenceReference.lawfullyObtained)
        assertTrue(normalized.evidenceReference.sourceVerified)
        assertEquals(SicopProcurementSourceAdapter.SICOP_SOURCE_GROUP, normalized.evidenceReference.independenceGroup)
    }

    @Test
    fun canonicalHashIsDeterministicAndDetectsModifications() {
        val hash1 = SicopProcurementSourceAdapter.computeCanonicalDigest(sampleValidRecord)
        val hash2 = SicopProcurementSourceAdapter.computeCanonicalDigest(sampleValidRecord)
        assertEquals(hash1, hash2)

        val modifiedRecord = sampleValidRecord.copy(amountMinorUnits = sampleValidRecord.amountMinorUnits + 1L)
        val modifiedHash = SicopProcurementSourceAdapter.computeCanonicalDigest(modifiedRecord)

        assertNotEquals(hash1, modifiedHash)
    }

    @Test
    fun invalidExpedienteFormatIsRejected() {
        val invalidRecord = sampleValidRecord.copy(expedienteNumber = "EXPEDIENTE-INVALIDO-123")
        val result = SicopProcurementSourceAdapter.normalize(invalidRecord)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid SICOP expediente format") == true)
    }

    @Test
    fun invalidCostaRicanTaxIdIsRejected() {
        val invalidRecord = sampleValidRecord.copy(vendorTaxId = "TAX-ID-XYZ")
        val result = SicopProcurementSourceAdapter.normalize(invalidRecord)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid Costa Rica Cédula Jurídica format") == true)
    }

    @Test
    fun negativeProcurementAmountIsRejected() {
        val invalidRecord = sampleValidRecord.copy(amountMinorUnits = -5000L)
        val result = SicopProcurementSourceAdapter.normalize(invalidRecord)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Procurement amount cannot be negative") == true)
    }

    @Test
    fun identicalTaxIdsResolveToExactMatch() {
        val entityA = EconomicEntityDescriptor(
            registeredName = "Constructora El Roble S.A.",
            taxId = "3-101-998877",
        )
        val entityB = EconomicEntityDescriptor(
            registeredName = "Constructora El Roble Sociedad Anónima",
            taxId = "3-101-998877",
        )

        val resolution = SicopProcurementSourceAdapter.resolveEntityMatch(entityA, entityB)
        assertEquals(EntityResolutionStatus.EXACT_TAX_ID_MATCH, resolution)
    }

    @Test
    fun differingTaxIdsWithSimilarNamesNeverMergeSilently() {
        val entityA = EconomicEntityDescriptor(
            registeredName = "Servicios Médicos San José S.A.",
            taxId = "3-101-111111",
        )
        val entityB = EconomicEntityDescriptor(
            registeredName = "Servicios Médicos San José S.A.",
            taxId = "3-101-222222",
        )

        val resolution = SicopProcurementSourceAdapter.resolveEntityMatch(entityA, entityB)
        assertEquals(EntityResolutionStatus.DIFFERENT_ENTITIES, resolution)
    }

    @Test
    fun missingTaxIdRequiresHumanReviewRatherThanAutomaticMerge() {
        val entityA = EconomicEntityDescriptor(
            registeredName = "Distribuidora Universal S.A.",
            taxId = "3-101-555555",
        )
        val entityB = EconomicEntityDescriptor(
            registeredName = "Distribuidora Universal S.A.",
            taxId = null,
        )

        val resolution = SicopProcurementSourceAdapter.resolveEntityMatch(entityA, entityB)
        assertEquals(EntityResolutionStatus.UNRESOLVED_REQUIRES_HUMAN_REVIEW, resolution)
    }
}
