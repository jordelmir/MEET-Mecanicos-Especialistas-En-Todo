package com.elysium369.meet.safety.intelligence.domain

/**
 * Domain Models for Elysium Safety Global Financial Intelligence & Evidence Graph.
 *
 * Epistemic Foundations:
 * 1. Evidence != Guilt: A documented link or contract does not imply wrongdoing.
 * 2. Conservative Entity Resolution: Strict identification by Tax ID / Cédula Jurídica;
 *    never merge entities based on partial name match or common address.
 * 3. Two-person rule and verifiable provenance on all investigative cases.
 */

enum class EconomicEntityType {
    CORPORATION,
    PUBLIC_INSTITUTION,
    DOCUMENTED_ASSET,
    INDIVIDUAL_ACTOR,
    CONTRACT_TENDER,
}

enum class ResolutionConfidence {
    EXACT_IDENTIFIER_MATCH,
    OFFICIAL_REGISTRY_FILING,
    PROBABLE_MATCH_REVIEW_REQUIRED,
    REJECTED_WEAK_MATCH,
}

data class EconomicEntity(
    val entityId: String,
    val entityType: EconomicEntityType,
    val registeredName: String,
    val canonicalTaxId: String?,
    val jurisdiction: String = "CR",
    val aliases: List<String> = emptyList(),
    val resolutionConfidence: ResolutionConfidence = ResolutionConfidence.EXACT_IDENTIFIER_MATCH,
    val sourceRecordIds: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)

enum class RelationshipType {
    SHAREHOLDER,
    LEGAL_REPRESENTATIVE,
    BENEFICIAL_OWNER,
    CONTRACT_AWARDED_TO,
    BUYER_INSTITUTION,
    SUBSIDIARY_OF,
    JOINT_VENTURE_PARTNER,
}

enum class EpistemicLinkStatus {
    DOCUMENTED_OFFICIAL,
    CORROBORATED_CROSS_SOURCE,
    DISPUTED_OR_AMBIGUOUS,
    UNVERIFIED_CLAIM,
}

data class EntityRelationship(
    val relationshipId: String,
    val sourceEntityId: String,
    val targetEntityId: String,
    val relationshipType: RelationshipType,
    val sharePercentage: Double? = null,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val sourceRecordId: String,
    val epistemicStatus: EpistemicLinkStatus = EpistemicLinkStatus.DOCUMENTED_OFFICIAL,
    val notes: String? = null,
)

enum class SourceRecordType {
    PUBLIC_PROCUREMENT_SICOP,
    NATIONAL_CORPORATE_REGISTRY,
    OFFICIAL_AUDIT_REPORT,
    JUDICIAL_DECISION,
    REGULATORY_SANCTION,
    AUTHORIZED_LEGAL_DISCLOSURE,
}

data class SourceRecord(
    val sourceRecordId: String,
    val sourceType: SourceRecordType,
    val publisherAuthority: String,
    val documentTitle: String,
    val officialReferenceNumber: String,
    val canonicalUrl: String? = null,
    val contentSha256: String,
    val publicationDate: Long,
    val retrievalTimestamp: Long,
    val legalProvenanceBasis: String,
    val isVerified: Boolean = true,
)

enum class FinancialObservationType {
    CONTRACT_AWARD_AMOUNT,
    DISCLOSED_TRANSFER,
    AUDIT_DISCREPANCY_VALUATION,
    ESTIMATED_CONTRACT_CEILING,
}

data class FinancialObservation(
    val observationId: String,
    val amountMinorUnits: Long,
    val currency: String = "CRC",
    val observationDate: Long,
    val sourceRecordId: String,
    val observationType: FinancialObservationType,
    val fromEntityId: String? = null,
    val toEntityId: String? = null,
    val isVerified: Boolean = true,
)

enum class InvestigativeCaseLifecycle {
    DRAFT,
    SUBMITTED,
    SOURCE_VALIDATION,
    CORROBORATION,
    ANALYST_REVIEW,
    EDITORIAL_REVIEW,
    DISCLOSURE_APPROVED,
    CLOSED_OR_CORRECTED,
}

enum class CaseAccessClassification {
    RESTRICTED_INTERNAL,
    CONFIDENTIAL_INSTITUTIONAL,
    APPROVED_FOR_PUBLIC_RELEASE,
}

data class CaseAuditEntry(
    val timestamp: Long,
    val actorId: String,
    val action: String,
    val justification: String,
)

data class InvestigativeCase(
    val caseId: String,
    val title: String,
    val purposeAndScope: String,
    val responsibleOrganization: String,
    val leadInvestigatorId: String,
    val accessClassification: CaseAccessClassification = CaseAccessClassification.RESTRICTED_INTERNAL,
    val lifecycleState: InvestigativeCaseLifecycle = InvestigativeCaseLifecycle.DRAFT,
    val entityIds: List<String> = emptyList(),
    val sourceRecordIds: List<String> = emptyList(),
    val relationshipIds: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val auditLog: List<CaseAuditEntry> = emptyList(),
)
