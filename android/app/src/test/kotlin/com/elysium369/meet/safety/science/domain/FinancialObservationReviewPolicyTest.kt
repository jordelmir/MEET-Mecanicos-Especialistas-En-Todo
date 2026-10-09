package com.elysium369.meet.safety.science.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialObservationReviewPolicyTest {

    @Test
    fun visibleWealthAloneNeverQualifiesForInvestigativeReview() {
        val result = FinancialObservationReviewPolicy.assess(
            listOf(
                evidence(
                    id = "observation-1",
                    kind = FinancialEvidenceKind.VISIBLE_WEALTH_OBSERVATION,
                    sourceGroup = "citizen-observation",
                    discrepancy = false,
                ),
            ),
        )

        assertEquals(FinancialReviewDisposition.INSUFFICIENT_EVIDENCE, result.disposition)
        assertTrue(
            result.reasons.contains(
                FinancialReviewReason.VISIBLE_WEALTH_ALONE_IS_NOT_EVIDENCE_OF_ILLEGALITY,
            ),
        )
        assertTrue(result.eligibleEvidenceReferenceIds.isEmpty())
    }

    @Test
    fun documentarySourceWithoutSpecificDiscrepancyIsInsufficient() {
        val result = FinancialObservationReviewPolicy.assess(
            listOf(
                evidence(
                    id = "company-register-1",
                    kind = FinancialEvidenceKind.PUBLIC_CORPORATE_RECORD,
                    sourceGroup = "registry-a",
                    discrepancy = false,
                ),
            ),
        )

        assertEquals(FinancialReviewDisposition.INSUFFICIENT_EVIDENCE, result.disposition)
        assertTrue(result.eligibleEvidenceReferenceIds.isEmpty())
    }

    @Test
    fun unauthorizedOrUnverifiedSourceCannotQualify() {
        val unauthorized = evidence(
            id = "transaction-1",
            kind = FinancialEvidenceKind.AUTHORIZED_TRANSACTION_RECORD,
            sourceGroup = "source-a",
            discrepancy = true,
            lawfullyObtained = false,
        )
        val unverified = evidence(
            id = "audit-1",
            kind = FinancialEvidenceKind.PUBLIC_AUDIT_REPORT,
            sourceGroup = "source-b",
            discrepancy = true,
            sourceVerified = false,
        )

        val result = FinancialObservationReviewPolicy.assess(listOf(unauthorized, unverified))

        assertEquals(FinancialReviewDisposition.INSUFFICIENT_EVIDENCE, result.disposition)
        assertTrue(result.eligibleEvidenceReferenceIds.isEmpty())
    }

    @Test
    fun multipleCopiesFromOneSourceAreNotIndependentCorroboration() {
        val firstCopy = evidence(
            id = "article-copy-1",
            kind = FinancialEvidenceKind.OTHER_DOCUMENTED_SOURCE,
            sourceGroup = "same-originating-document",
            discrepancy = true,
        )
        val secondCopy = evidence(
            id = "article-copy-2",
            kind = FinancialEvidenceKind.PUBLIC_AUDIT_REPORT,
            sourceGroup = "same-originating-document",
            discrepancy = true,
        )

        val result = FinancialObservationReviewPolicy.assess(listOf(firstCopy, secondCopy))

        assertEquals(FinancialReviewDisposition.INSUFFICIENT_EVIDENCE, result.disposition)
        assertTrue(result.reasons.contains(FinancialReviewReason.INDEPENDENT_CORROBORATION_MISSING))
        assertEquals(1, result.independentSourceGroupCount)
    }

    @Test
    fun independentlySourcedDocumentedDiscrepancyAllowsHumanReviewOnly() {
        val procurement = evidence(
            id = "award-1",
            kind = FinancialEvidenceKind.PUBLIC_PROCUREMENT_RECORD,
            sourceGroup = "procurement-portal",
            discrepancy = true,
        )
        val audit = evidence(
            id = "audit-1",
            kind = FinancialEvidenceKind.PUBLIC_AUDIT_REPORT,
            sourceGroup = "audit-office-report",
            discrepancy = true,
        )

        val result = FinancialObservationReviewPolicy.assess(listOf(procurement, audit))

        assertEquals(FinancialReviewDisposition.ELIGIBLE_FOR_HUMAN_REVIEW, result.disposition)
        assertEquals(setOf("award-1", "audit-1"), result.eligibleEvidenceReferenceIds)
        assertEquals(2, result.independentSourceGroupCount)
        assertFalse(
            result.reasons.contains(
                FinancialReviewReason.VISIBLE_WEALTH_ALONE_IS_NOT_EVIDENCE_OF_ILLEGALITY,
            ),
        )
    }

    private fun evidence(
        id: String,
        kind: FinancialEvidenceKind,
        sourceGroup: String?,
        discrepancy: Boolean,
        lawfullyObtained: Boolean = true,
        sourceVerified: Boolean = true,
    ) = FinancialEvidenceReference(
        referenceId = id,
        kind = kind,
        lawfullyObtained = lawfullyObtained,
        sourceVerified = sourceVerified,
        independenceGroup = sourceGroup,
        documentsSpecificDiscrepancy = discrepancy,
    )
}
