package com.elysium369.meet.safety.science.domain

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Source-agnostic normalizer for documented public-procurement records.
 *
 * This is a domain utility, NOT a live portal connector or ingestion pipeline.
 * Trust flags must come from trusted server-side provenance records; never accept client
 * assertions as authoritative. Defaults deliberately remain unverified.
 *
 * SHA-256 detects changes to canonicalized fields; it does not prove truth or guilt.
 */
enum class PublicProcurementType {
    TENDER,
    DIRECT_CONTRACT,
    EXCEPTIONAL_URGENCY,
    OTHER,
}

data class PublicProcurementRawRecord(
    val expedienteNumber: String,
    val procurementType: PublicProcurementType,
    val buyerInstitution: String,
    val vendorTaxId: String,
    val vendorName: String,
    val amountMinorUnits: Long,
    val currencyCode: String,
    val awardDateIso: String,
    val sourceSystem: String,
    val sourceDocumentUrl: String,
    val independenceGroup: String? = null,
    val lawfullyObtained: Boolean = false,
    val sourceVerified: Boolean = false,
    val hasSpecificDocumentedDiscrepancy: Boolean = false,
    val statutoryLegalBasis: String? = null,
)

data class NormalizedProcurementRecord(
    val canonicalRecordId: String,
    val contentSha256: String,
    val rawRecord: PublicProcurementRawRecord,
    val evidenceReference: FinancialEvidenceReference,
    val statutoryLegalBasis: String?,
)

enum class EntityResolutionStatus {
    EXACT_TAX_ID_MATCH,
    DIFFERENT_ENTITIES,
    UNRESOLVED_REQUIRES_HUMAN_REVIEW,
}

data class EconomicEntityDescriptor(
    val registeredName: String,
    val taxId: String?,
    val jurisdiction: String = "CR",
)

object PublicProcurementRecordNormalizer {

    /** Digest over normalized record fields and its declared source pointer. */
    fun computeCanonicalDigest(record: PublicProcurementRawRecord): String {
        val canonicalPayload = buildString {
            append("record=").append(record.expedienteNumber.trim()).append(";")
            append("type=").append(record.procurementType.name).append(";")
            append("buyer=").append(record.buyerInstitution.trim()).append(";")
            append("taxId=").append(record.vendorTaxId.trim()).append(";")
            append("vendor=").append(record.vendorName.trim()).append(";")
            append("amount=").append(record.amountMinorUnits).append(";")
            append("currency=").append(record.currencyCode.trim().uppercase()).append(";")
            append("date=").append(record.awardDateIso.trim()).append(";")
            append("sourceSystem=").append(record.sourceSystem.trim()).append(";")
            append("sourceUrl=").append(record.sourceDocumentUrl.trim())
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalPayload.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Preserves supplied provenance; it never promotes unverified metadata.
     * Trust fields are meaningful only when assigned from a trusted authority.
     */
    fun normalize(
        record: PublicProcurementRawRecord,
        hasSpecificDocumentedDiscrepancy: Boolean = record.hasSpecificDocumentedDiscrepancy,
    ): Result<NormalizedProcurementRecord> {
        if (record.expedienteNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("Record reference cannot be blank"))
        }
        if (record.vendorTaxId.isBlank()) {
            return Result.failure(IllegalArgumentException("Vendor identifier cannot be blank"))
        }
        if (record.amountMinorUnits < 0) {
            return Result.failure(IllegalArgumentException("Procurement amount cannot be negative"))
        }
        if (record.buyerInstitution.isBlank()) {
            return Result.failure(IllegalArgumentException("Buyer institution cannot be blank"))
        }
        if (record.vendorName.isBlank()) {
            return Result.failure(IllegalArgumentException("Vendor name cannot be blank"))
        }
        if (record.currencyCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Currency code cannot be blank"))
        }
        if (record.sourceSystem.isBlank()) {
            return Result.failure(IllegalArgumentException("Source system cannot be blank"))
        }

        val sourceUri = runCatching { URI(record.sourceDocumentUrl.trim()) }.getOrNull()
        val sourceScheme = sourceUri?.scheme?.lowercase()
        if (sourceUri == null || sourceScheme == null || sourceScheme !in setOf("http", "https") ||
            sourceUri.host.isNullOrBlank()
        ) {
            return Result.failure(IllegalArgumentException("Source document URL must be an absolute HTTP(S) URL"))
        }

        val canonicalId = "PUBLIC-PROCUREMENT-${record.expedienteNumber.trim()}"
        val evidenceRef = FinancialEvidenceReference(
            referenceId = canonicalId,
            kind = FinancialEvidenceKind.PUBLIC_PROCUREMENT_RECORD,
            lawfullyObtained = record.lawfullyObtained,
            sourceVerified = record.sourceVerified,
            independenceGroup = record.independenceGroup?.trim()?.takeIf(String::isNotEmpty),
            documentsSpecificDiscrepancy = hasSpecificDocumentedDiscrepancy,
        )

        return Result.success(
            NormalizedProcurementRecord(
                canonicalRecordId = canonicalId,
                contentSha256 = computeCanonicalDigest(record),
                rawRecord = record,
                evidenceReference = evidenceRef,
                statutoryLegalBasis = record.statutoryLegalBasis?.trim()?.takeIf(String::isNotEmpty),
            ),
        )
    }

    /** Never merges legal entities by name similarity alone. */
    fun resolveEntityMatch(
        first: EconomicEntityDescriptor,
        second: EconomicEntityDescriptor,
    ): EntityResolutionStatus {
        val firstTaxId = first.taxId?.trim()?.takeIf(String::isNotEmpty)
        val secondTaxId = second.taxId?.trim()?.takeIf(String::isNotEmpty)
        if (firstTaxId != null && secondTaxId != null) {
            return if (firstTaxId == secondTaxId && first.jurisdiction == second.jurisdiction) {
                EntityResolutionStatus.EXACT_TAX_ID_MATCH
            } else {
                EntityResolutionStatus.DIFFERENT_ENTITIES
            }
        }
        return EntityResolutionStatus.UNRESOLVED_REQUIRES_HUMAN_REVIEW
    }
}
