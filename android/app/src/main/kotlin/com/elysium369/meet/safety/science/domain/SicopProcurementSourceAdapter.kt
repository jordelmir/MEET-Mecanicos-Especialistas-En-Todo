package com.elysium369.meet.safety.science.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Lawful Public Procurement Ingestion Adapter for Costa Rica (SICOP).
 *
 * Governed by Costa Rica Ley General de Contratación Pública N.° 9986.
 * All public tenders and awards are accessible under statutory transparency principles.
 *
 * Invariant:
 * Computing a valid SHA-256 hash confirms byte integrity only; it does not
 * determine whether the contractor performed lawfully or whether any crime occurred.
 */
enum class SicopProcurementType {
    LICITACION_MAYOR,
    LICITACION_MENOR,
    LICITACION_REDUCIDA,
    CONTRATACION_DIRECTA,
    URGENCIA_EXCEPCIONAL,
}

data class SicopRawRecord(
    val expedienteNumber: String,
    val procurementType: SicopProcurementType,
    val buyerInstitution: String,
    val vendorTaxId: String,
    val vendorName: String,
    val amountMinorUnits: Long,
    val currencyCode: String,
    val awardDateIso: String,
    val officialPortalUrl: String,
)

data class NormalizedProcurementRecord(
    val canonicalRecordId: String,
    val contentSha256: String,
    val rawRecord: SicopRawRecord,
    val evidenceReference: FinancialEvidenceReference,
    val statutoryLegalBasis: String,
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

object SicopProcurementSourceAdapter {

    const val STATUTORY_LEGAL_BASIS = "Costa Rica Ley N.° 9986 (Ley General de Contratación Pública, Art. 16)"
    const val SICOP_SOURCE_GROUP = "CR_SICOP_OFFICIAL_PORTAL"

    private val expedienteRegex = Regex("""^\d{4}[A-Z]{2}-\d{6}-\d{10}$""")
    private val costaRicaTaxIdRegex = Regex("""^\d{1}-\d{3}-\d{6}$""")

    /**
     * Computes the byte-exact canonical SHA-256 digest of the procurement record.
     */
    fun computeCanonicalDigest(record: SicopRawRecord): String {
        val canonicalPayload = buildString {
            append("expediente=").append(record.expedienteNumber.trim()).append(";")
            append("type=").append(record.procurementType.name).append(";")
            append("buyer=").append(record.buyerInstitution.trim()).append(";")
            append("taxId=").append(record.vendorTaxId.trim()).append(";")
            append("vendor=").append(record.vendorName.trim()).append(";")
            append("amount=").append(record.amountMinorUnits).append(";")
            append("currency=").append(record.currencyCode.trim().uppercase()).append(";")
            append("date=").append(record.awardDateIso.trim()).append(";")
            append("url=").append(record.officialPortalUrl.trim())
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalPayload.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates and normalizes an authentic SICOP record.
     */
    fun normalize(
        record: SicopRawRecord,
        hasSpecificDocumentedDiscrepancy: Boolean = false,
    ): Result<NormalizedProcurementRecord> {
        if (!expedienteRegex.matches(record.expedienteNumber.trim())) {
            return Result.failure(
                IllegalArgumentException("Invalid SICOP expediente format: ${record.expedienteNumber}"),
            )
        }
        if (!costaRicaTaxIdRegex.matches(record.vendorTaxId.trim())) {
            return Result.failure(
                IllegalArgumentException("Invalid Costa Rica Cédula Jurídica format: ${record.vendorTaxId}"),
            )
        }
        if (record.amountMinorUnits < 0) {
            return Result.failure(
                IllegalArgumentException("Procurement amount cannot be negative: ${record.amountMinorUnits}"),
            )
        }
        if (record.buyerInstitution.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Buyer institution cannot be blank"),
            )
        }
        if (record.vendorName.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Vendor name cannot be blank"),
            )
        }

        val sha256 = computeCanonicalDigest(record)
        val canonicalId = "SICOP-CR-${record.expedienteNumber.trim()}"

        val evidenceRef = FinancialEvidenceReference(
            referenceId = canonicalId,
            kind = FinancialEvidenceKind.PUBLIC_PROCUREMENT_RECORD,
            lawfullyObtained = true,
            sourceVerified = true,
            independenceGroup = SICOP_SOURCE_GROUP,
            documentsSpecificDiscrepancy = hasSpecificDocumentedDiscrepancy,
        )

        return Result.success(
            NormalizedProcurementRecord(
                canonicalRecordId = canonicalId,
                contentSha256 = sha256,
                rawRecord = record,
                evidenceReference = evidenceRef,
                statutoryLegalBasis = STATUTORY_LEGAL_BASIS,
            ),
        )
    }

    /**
     * Strict entity resolution between two legal descriptors.
     * Never merges two corporate entities solely based on textual similarity.
     */
    fun resolveEntityMatch(
        first: EconomicEntityDescriptor,
        second: EconomicEntityDescriptor,
    ): EntityResolutionStatus {
        val firstTaxId = first.taxId?.trim()?.takeIf(String::isNotEmpty)
        val secondTaxId = second.taxId?.trim()?.takeIf(String::isNotEmpty)

        if (firstTaxId != null && secondTaxId != null) {
            return if (firstTaxId == secondTaxId) {
                EntityResolutionStatus.EXACT_TAX_ID_MATCH
            } else {
                EntityResolutionStatus.DIFFERENT_ENTITIES
            }
        }

        // If one or both tax IDs are missing, require human review; do not auto-merge.
        return EntityResolutionStatus.UNRESOLVED_REQUIRES_HUMAN_REVIEW
    }
}
