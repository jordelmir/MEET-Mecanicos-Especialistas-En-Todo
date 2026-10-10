package com.elysium369.meet.safety.science.domain

/**
 * Conservative, deterministic gate for a future public-interest financial-investigation workflow.
 *
 * This policy does not score people, detect crime, infer illicit wealth, or authorize disclosure.
 * It only decides whether source-backed material is sufficiently documented to be considered by
 * a human reviewer. It must not be treated as a complete investigation workflow until integrated
 * with server authority, case permissions, provenance, and end-to-end tests.
 */
enum class FinancialEvidenceKind {
    VISIBLE_WEALTH_OBSERVATION,
    PUBLIC_PROCUREMENT_RECORD,
    PUBLIC_CORPORATE_RECORD,
    PUBLIC_AUDIT_REPORT,
    OFFICIAL_JUDICIAL_RECORD,
    OFFICIAL_REGULATORY_RECORD,
    AUTHORIZED_TRANSACTION_RECORD,
    OTHER_DOCUMENTED_SOURCE,
}

data class FinancialEvidenceReference(
    val referenceId: String,
    val kind: FinancialEvidenceKind,
    val lawfullyObtained: Boolean,
    val sourceVerified: Boolean,
    val independenceGroup: String?,
    val documentsSpecificDiscrepancy: Boolean,
) {
    init {
        require(referenceId.isNotBlank()) { "Evidence reference ID must not be blank" }
    }
}

enum class FinancialReviewDisposition {
    INSUFFICIENT_EVIDENCE,
    ELIGIBLE_FOR_HUMAN_REVIEW,
}

enum class FinancialReviewReason {
    VISIBLE_WEALTH_ALONE_IS_NOT_EVIDENCE_OF_ILLEGALITY,
    NO_LAWFUL_VERIFIED_DOCUMENTED_DISCREPANCY,
    INDEPENDENT_CORROBORATION_MISSING,
    SOURCE_BACKED_MATERIAL_ELIGIBLE_FOR_HUMAN_REVIEW_ONLY,
}

data class FinancialObservationReviewAssessment(
    val disposition: FinancialReviewDisposition,
    val reasons: Set<FinancialReviewReason>,
    val eligibleEvidenceReferenceIds: Set<String>,
    val independentSourceGroupCount: Int,
)

object FinancialObservationReviewPolicy {
    private val documentaryKinds = setOf(
        FinancialEvidenceKind.PUBLIC_PROCUREMENT_RECORD,
        FinancialEvidenceKind.PUBLIC_CORPORATE_RECORD,
        FinancialEvidenceKind.PUBLIC_AUDIT_REPORT,
        FinancialEvidenceKind.OFFICIAL_JUDICIAL_RECORD,
        FinancialEvidenceKind.OFFICIAL_REGULATORY_RECORD,
        FinancialEvidenceKind.AUTHORIZED_TRANSACTION_RECORD,
        FinancialEvidenceKind.OTHER_DOCUMENTED_SOURCE,
    )

    /**
     * Visible wealth observations are intentionally excluded from the evidence gate.
     *
     * The positive disposition means only "eligible for human review". It is not an allegation,
     * criminality finding, public alert, verified transaction, or permission to disclose data.
     */
    fun assess(references: List<FinancialEvidenceReference>): FinancialObservationReviewAssessment {
        val qualifyingReferences = references.filter { reference ->
            reference.kind in documentaryKinds &&
                reference.lawfullyObtained &&
                reference.sourceVerified &&
                reference.documentsSpecificDiscrepancy &&
                !reference.independenceGroup.isNullOrBlank()
        }

        val independentGroups = qualifyingReferences
            .mapNotNull { it.independenceGroup?.trim()?.takeIf(String::isNotEmpty) }
            .toSet()

        if (qualifyingReferences.isEmpty()) {
            val reasons = if (references.isNotEmpty() && references.all {
                    it.kind == FinancialEvidenceKind.VISIBLE_WEALTH_OBSERVATION
                }
            ) {
                setOf(FinancialReviewReason.VISIBLE_WEALTH_ALONE_IS_NOT_EVIDENCE_OF_ILLEGALITY)
            } else {
                setOf(FinancialReviewReason.NO_LAWFUL_VERIFIED_DOCUMENTED_DISCREPANCY)
            }
            return FinancialObservationReviewAssessment(
                disposition = FinancialReviewDisposition.INSUFFICIENT_EVIDENCE,
                reasons = reasons,
                eligibleEvidenceReferenceIds = emptySet(),
                independentSourceGroupCount = independentGroups.size,
            )
        }

        if (independentGroups.size < 2) {
            return FinancialObservationReviewAssessment(
                disposition = FinancialReviewDisposition.INSUFFICIENT_EVIDENCE,
                reasons = setOf(FinancialReviewReason.INDEPENDENT_CORROBORATION_MISSING),
                eligibleEvidenceReferenceIds = qualifyingReferences.map { it.referenceId }.toSet(),
                independentSourceGroupCount = independentGroups.size,
            )
        }

        return FinancialObservationReviewAssessment(
            disposition = FinancialReviewDisposition.ELIGIBLE_FOR_HUMAN_REVIEW,
            reasons = setOf(FinancialReviewReason.SOURCE_BACKED_MATERIAL_ELIGIBLE_FOR_HUMAN_REVIEW_ONLY),
            eligibleEvidenceReferenceIds = qualifyingReferences.map { it.referenceId }.toSet(),
            independentSourceGroupCount = independentGroups.size,
        )
    }
}
