package com.elysium369.meet.safety.science.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicProcurementRecordNormalizerTest {
    private val sampleValidRecord = PublicProcurementRawRecord(
        expedienteNumber = "RECORD-2024-000001",
        procurementType = PublicProcurementType.TENDER,
        buyerInstitution = "Public Health Authority",
        vendorTaxId = "ENTITY-123456",
        vendorName = "Documented Supplier Ltd.",
        amountMinorUnits = 15000000000L,
        currencyCode = "CRC",
        awardDateIso = "2024-05-10T14:30:00Z",
        sourceSystem = "public-records-catalog",
        sourceDocumentUrl = "https://records.example.test/procurement/RECORD-2024-000001",
        independenceGroup = "official-records-catalog",
        lawfullyObtained = true,
        sourceVerified = true,
        hasSpecificDocumentedDiscrepancy = true,
        statutoryLegalBasis = "Applicable public-records framework (test fixture)",
    )

    @Test
    fun validRecordNormalizesWithCanonicalHashAndSuppliedProvenance() {
        val normalized = PublicProcurementRecordNormalizer.normalize(sampleValidRecord).getOrThrow()
        assertEquals("PUBLIC-PROCUREMENT-RECORD-2024-000001", normalized.canonicalRecordId)
        assertEquals(64, normalized.contentSha256.length)
        assertTrue(normalized.contentSha256.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(sampleValidRecord.statutoryLegalBasis, normalized.statutoryLegalBasis)
        assertEquals(FinancialEvidenceKind.PUBLIC_PROCUREMENT_RECORD, normalized.evidenceReference.kind)
        assertTrue(normalized.evidenceReference.lawfullyObtained)
        assertTrue(normalized.evidenceReference.sourceVerified)
        assertEquals("official-records-catalog", normalized.evidenceReference.independenceGroup)
        assertTrue(normalized.evidenceReference.documentsSpecificDiscrepancy)
    }

    @Test
    fun trustDefaultsDoNotPromoteAnUnverifiedSource() {
        val untrusted = sampleValidRecord.copy(
            independenceGroup = null,
            lawfullyObtained = false,
            sourceVerified = false,
        )
        val normalized = PublicProcurementRecordNormalizer.normalize(untrusted).getOrThrow()
        assertFalse(normalized.evidenceReference.lawfullyObtained)
        assertFalse(normalized.evidenceReference.sourceVerified)
        assertNull(normalized.evidenceReference.independenceGroup)
    }

    @Test
    fun canonicalHashIsDeterministicAndDetectsRecordChanges() {
        val first = PublicProcurementRecordNormalizer.computeCanonicalDigest(sampleValidRecord)
        val second = PublicProcurementRecordNormalizer.computeCanonicalDigest(sampleValidRecord)
        assertEquals(first, second)
        val modified = sampleValidRecord.copy(amountMinorUnits = sampleValidRecord.amountMinorUnits + 1L)
        assertNotEquals(first, PublicProcurementRecordNormalizer.computeCanonicalDigest(modified))
    }

    @Test
    fun blankRecordReferenceIsRejected() {
        assertTrue(PublicProcurementRecordNormalizer.normalize(sampleValidRecord.copy(expedienteNumber = " ")).isFailure)
    }

    @Test
    fun invalidSourceUrlIsRejected() {
        assertTrue(PublicProcurementRecordNormalizer.normalize(sampleValidRecord.copy(sourceDocumentUrl = "not a URL")).isFailure)
    }

    @Test
    fun negativeProcurementAmountIsRejected() {
        assertTrue(PublicProcurementRecordNormalizer.normalize(sampleValidRecord.copy(amountMinorUnits = -5L)).isFailure)
    }

    @Test
    fun legalEntityMatchRequiresSameIdentifiersAndJurisdiction() {
        val first = EconomicEntityDescriptor("Supplier", "ENTITY-1", "CR")
        val same = EconomicEntityDescriptor("Other display name", "ENTITY-1", "CR")
        val differentJurisdiction = EconomicEntityDescriptor("Supplier", "ENTITY-1", "US")
        val unresolved = EconomicEntityDescriptor("Supplier", null, "CR")
        assertEquals(EntityResolutionStatus.EXACT_TAX_ID_MATCH, PublicProcurementRecordNormalizer.resolveEntityMatch(first, same))
        assertEquals(EntityResolutionStatus.DIFFERENT_ENTITIES, PublicProcurementRecordNormalizer.resolveEntityMatch(first, differentJurisdiction))
        assertEquals(EntityResolutionStatus.UNRESOLVED_REQUIRES_HUMAN_REVIEW, PublicProcurementRecordNormalizer.resolveEntityMatch(first, unresolved))
    }
}
