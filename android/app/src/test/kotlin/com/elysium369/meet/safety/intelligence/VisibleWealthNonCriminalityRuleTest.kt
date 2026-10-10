package com.elysium369.meet.safety.intelligence

import com.elysium369.meet.safety.intelligence.domain.SourceRecord
import com.elysium369.meet.safety.intelligence.domain.SourceRecordType
import com.elysium369.meet.safety.intelligence.domain.VisibleWealthNonCriminalityRule
import com.elysium369.meet.safety.intelligence.domain.WealthVerificationDisposition
import org.junit.Assert.*
import org.junit.Test

class VisibleWealthNonCriminalityRuleTest {

    @Test
    fun reporting_visible_wealth_alone_never_creates_criminality_finding() {
        val observation = "El vecino llegó con cuatro vehículos de lujo, cadenas de oro y una propiedad costosa."

        val assessment = VisibleWealthNonCriminalityRule.evaluate(
            observationText = observation,
            sourceRecords = emptyList(),
            hasVerifiedAuditOrTaxDiscrepancy = false,
        )

        assertEquals(
            WealthVerificationDisposition.REJECTED_LIFESTYLE_SURVEILLANCE_WITHOUT_OFFICIAL_DISCREPANCY,
            assessment.disposition,
        )
        assertTrue(assessment.isAutomatedSurveillanceRejected)
        assertFalse(assessment.eligibleForInstitutionalDossier)
        assertTrue(assessment.explanation.contains("Principio Constitucional"))
    }

    @Test
    fun visibleWealthObservationWithoutOfficialDiscrepancyIsStrictlyRejected() {
        val observation = "El vecino compró cuatro vehículos de lujo, tiene cadenas de oro y una propiedad costosa en el condominio."

        val assessment = VisibleWealthNonCriminalityRule.evaluate(
            observationText = observation,
            sourceRecords = emptyList(),
            hasVerifiedAuditOrTaxDiscrepancy = false,
        )

        assertEquals(
            WealthVerificationDisposition.REJECTED_LIFESTYLE_SURVEILLANCE_WITHOUT_OFFICIAL_DISCREPANCY,
            assessment.disposition,
        )
        assertTrue(assessment.isAutomatedSurveillanceRejected)
        assertFalse(assessment.eligibleForInstitutionalDossier)
        assertTrue(assessment.explanation.contains("Principio Constitucional"))
    }

    @Test
    fun officialAuditDiscrepancyWithLawfulSourcesIsEligibleForHumanAudit() {
        val observation = "Discrepancia documentada en licitación pública SICOP y reporte tributario."
        val officialRecord = SourceRecord(
            sourceRecordId = "src-01",
            sourceType = SourceRecordType.PUBLIC_PROCUREMENT_SICOP,
            publisherAuthority = "SICOP Costa Rica",
            documentTitle = "Adjudicación Licitación Pública",
            officialReferenceNumber = "2026LN-001",
            contentSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            publicationDate = 1715000000000L,
            retrievalTimestamp = 1715001000000L,
            legalProvenanceBasis = "Ley N.° 9986",
            isVerified = true,
        )

        val assessment = VisibleWealthNonCriminalityRule.evaluate(
            observationText = observation,
            sourceRecords = listOf(officialRecord),
            hasVerifiedAuditOrTaxDiscrepancy = true,
        )

        assertEquals(
            WealthVerificationDisposition.LAWFULLY_DOCUMENTED_DISCREPANCY_ELIGIBLE_FOR_HUMAN_AUDIT,
            assessment.disposition,
        )
        assertFalse(assessment.isAutomatedSurveillanceRejected)
        assertTrue(assessment.eligibleForInstitutionalDossier)
    }
}
