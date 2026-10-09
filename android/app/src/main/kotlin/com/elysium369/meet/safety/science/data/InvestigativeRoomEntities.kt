package com.elysium369.meet.safety.science.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Persistence Entities for Financial Intelligence & Investigative Evidence.
 * Mirrors local schema without breaking existing database tables.
 */

@Entity(
    tableName = "safety_investigative_cases",
    indices = [
        Index(value = ["lifecycleState"]),
        Index(value = ["accessClassification"]),
    ],
)
data class InvestigativeCaseEntity(
    @PrimaryKey val caseId: String,
    val title: String,
    val purposeAndScope: String,
    val responsibleOrganization: String,
    val leadInvestigatorId: String,
    val accessClassification: String, // RESTRICTED_INTERNAL | CONFIDENTIAL_INSTITUTIONAL | APPROVED_FOR_PUBLIC_RELEASE
    val lifecycleState: String, // DRAFT -> SUBMITTED -> SOURCE_VALIDATION -> CORROBORATION -> ANALYST_REVIEW -> EDITORIAL_REVIEW -> DISCLOSURE_APPROVED -> CLOSED_OR_CORRECTED
    val entityIdsJson: String = "[]",
    val sourceRecordIdsJson: String = "[]",
    val relationshipIdsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "safety_source_records",
    indices = [
        Index(value = ["sourceType"]),
        Index(value = ["contentSha256"], unique = true),
        Index(value = ["officialReferenceNumber"]),
    ],
)
data class SourceRecordEntity(
    @PrimaryKey val sourceRecordId: String,
    val sourceType: String, // PUBLIC_PROCUREMENT_SICOP | NATIONAL_CORPORATE_REGISTRY | OFFICIAL_AUDIT_REPORT | ...
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

@Entity(
    tableName = "safety_financial_observations",
    indices = [
        Index(value = ["sourceRecordId"]),
        Index(value = ["fromEntityId"]),
        Index(value = ["toEntityId"]),
    ],
)
data class FinancialObservationEntity(
    @PrimaryKey val observationId: String,
    val amountMinorUnits: Long,
    val currency: String = "CRC",
    val observationDate: Long,
    val sourceRecordId: String,
    val observationType: String,
    val fromEntityId: String? = null,
    val toEntityId: String? = null,
    val isVerified: Boolean = true,
)

@Entity(
    tableName = "safety_investigative_signals",
    indices = [
        Index(value = ["caseId"]),
        Index(value = ["ruleId"]),
    ],
)
data class InvestigativeSignalEntity(
    @PrimaryKey val signalId: String,
    val caseId: String,
    val ruleId: String,
    val disposition: String,
    val explanation: String,
    val alternativeHypothesesJson: String = "[]",
    val detectedAt: Long,
    val reviewedBy: String? = null,
)
