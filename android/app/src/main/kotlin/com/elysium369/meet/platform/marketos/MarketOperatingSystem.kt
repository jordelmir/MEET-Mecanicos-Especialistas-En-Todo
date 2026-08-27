package com.elysium369.meet.platform.marketos

import com.elysium369.meet.core.money.Money
import java.security.MessageDigest
import java.util.UUID

enum class MarketVertical { AUTOMOTIVE_REPAIR, TOW, PARTS, RIDE, LEGAL, REAL_ESTATE, FUEL_REWARDS }

enum class OrganizationKind { SOLO_PROFESSIONAL, LAW_FIRM, BROKERAGE, LANDLORD, FUEL_NETWORK, FUEL_STATION }

enum class OrganizationRole {
    OWNER, ADMIN, PARTNER, PROFESSIONAL, PARALEGAL, CASE_MANAGER, COMPLIANCE, BILLING,
    BROKER, PROPERTY_MANAGER, REGIONAL_MANAGER, STATION_MANAGER, SHIFT_SUPERVISOR,
    MARKETING_MANAGER, CRM_MANAGER, FINANCE, AUDITOR, CASHIER, ATTENDANT, VIEW_ONLY,
}

enum class CredentialStatus { UNVERIFIED, PENDING, ACTIVE, SUSPENDED, EXPIRED, REVOKED, NOT_APPLICABLE }

data class ProfessionalCredentialProof(
    val credentialId: UUID,
    val principalId: UUID,
    val authority: String,
    val identifierMasked: String,
    val status: CredentialStatus,
    val checkedAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val evidenceRef: String,
    val sourceVersion: String,
) {
    init {
        require(authority.isNotBlank())
        require(identifierMasked.isNotBlank())
        require(evidenceRef.isNotBlank())
    }

    fun isActiveAt(nowEpochMs: Long, freshnessWindowMs: Long): Boolean =
        status == CredentialStatus.ACTIVE &&
            checkedAtEpochMs <= nowEpochMs &&
            nowEpochMs - checkedAtEpochMs <= freshnessWindowMs &&
            (expiresAtEpochMs == null || expiresAtEpochMs >= nowEpochMs)
}

enum class ClaimTruthState {
    DECLARED, DOCUMENT_OBSERVED, AUTHORITY_VERIFIED, REGISTRY_VERIFIED, CADASTRAL_VERIFIED,
    MUNICIPAL_VERIFIED, NOTARIAL_VERIFIED, PHYSICALLY_INSPECTED, POS_AUTHORITATIVE,
    ERP_IMPORTED, RECEIPT_VERIFIED, STAFF_DECLARED, CUSTOMER_DECLARED, UNKNOWN,
}

data class TruthClaim(
    val key: String,
    val state: ClaimTruthState,
    val observedAtEpochMs: Long,
    val evidenceRef: String?,
    val authority: String?,
) {
    val isVerified: Boolean
        get() = state in setOf(
            ClaimTruthState.AUTHORITY_VERIFIED,
            ClaimTruthState.REGISTRY_VERIFIED,
            ClaimTruthState.CADASTRAL_VERIFIED,
            ClaimTruthState.MUNICIPAL_VERIFIED,
            ClaimTruthState.NOTARIAL_VERIFIED,
            ClaimTruthState.PHYSICALLY_INSPECTED,
            ClaimTruthState.POS_AUTHORITATIVE,
        ) && !evidenceRef.isNullOrBlank()
}

data class Organization(
    val organizationId: UUID,
    val legalName: String,
    val commercialName: String,
    val kind: OrganizationKind,
    val jurisdiction: String,
    val version: Long,
    val active: Boolean,
) {
    init {
        require(legalName.isNotBlank())
        require(jurisdiction.matches(Regex("[A-Z]{2}(?:-[A-Z0-9]{1,3})?")))
        require(version >= 0)
    }
}

data class OrganizationMembership(
    val organizationId: UUID,
    val principalId: UUID,
    val roles: Set<OrganizationRole>,
    val validFromEpochMs: Long,
    val validUntilEpochMs: Long?,
    val revokedAtEpochMs: Long?,
) {
    fun grants(role: OrganizationRole, nowEpochMs: Long): Boolean =
        role in roles && revokedAtEpochMs == null && validFromEpochMs <= nowEpochMs &&
            (validUntilEpochMs == null || validUntilEpochMs >= nowEpochMs)
}

data class MarketplaceOffer(
    val offerId: UUID,
    val requestId: UUID,
    val organizationId: UUID,
    val professionalPrincipalId: UUID?,
    val fee: Money,
    val externalExpenses: Money,
    val scope: String,
    val exclusions: String,
    val validUntilEpochMs: Long,
    val version: Long,
) {
    init {
        require(fee.currency == externalExpenses.currency)
        require(scope.isNotBlank())
        require(exclusions.isNotBlank())
        require(version >= 0)
    }
}

data class MarketCommandEnvelope(
    val commandId: UUID,
    val aggregateId: UUID,
    val aggregateType: String,
    val commandType: String,
    val expectedVersion: Long,
    val idempotencyKey: UUID,
    val actorPrincipalId: UUID,
    val payloadCanonicalJson: String,
    val payloadVersion: Int,
    val createdAtEpochMs: Long,
) {
    init {
        require(expectedVersion >= 0)
        require(payloadVersion > 0)
        require(payloadCanonicalJson.startsWith("{") && payloadCanonicalJson.endsWith("}"))
    }

    val canonicalDigest: String by lazy {
        val canonical = listOf(
            commandId, aggregateId, aggregateType, commandType, expectedVersion,
            idempotencyKey, actorPrincipalId, payloadVersion, payloadCanonicalJson,
        ).joinToString("\u001f")
        MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

enum class DeliveryProofState {
    MODEL_EXISTS, CLIENT_IMPLEMENTED, SERVER_AUTHORITATIVE, DEVICE_VERIFIED,
    PHYSICALLY_VERIFIED, PRODUCTION_VALIDATED,
}
