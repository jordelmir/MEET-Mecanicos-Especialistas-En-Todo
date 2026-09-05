package com.elysium369.meet.property.domain

import com.elysium369.meet.platform.marketos.ClaimTruthState
import com.elysium369.meet.platform.marketos.TruthClaim
import java.util.UUID

enum class PropertyOperation { SALE, RENT, RENT_TO_OWN, TEMPORARY, PRESALE, ASSIGNMENT }
enum class PropertyListingState { DRAFT, COMPLIANCE_REVIEW, PUBLISHED, RESERVED, UNDER_DUE_DILIGENCE, CLOSED, WITHDRAWN; val isActive: Boolean get() = this in listOf(DRAFT, COMPLIANCE_REVIEW, PUBLISHED, RESERVED, UNDER_DUE_DILIGENCE) }
enum class PropertyTrustRisk { VERIFIED_OWNER, AUTHORIZED_AGENT, DOCUMENT_PENDING, REGISTRY_MISMATCH, HIGH_RISK, UNKNOWN }
enum class AddressDisclosure { APPROXIMATE_ZONE, AUTHORIZED_EXACT }

data class PropertyPassport(
    val propertyId: UUID,
    val registryNumberMasked: String,
    val cadastralPlanMasked: String?,
    val claims: Map<String, TruthClaim>,
    val refreshedAtEpochMs: Long,
) {
    fun claim(key: String): TruthClaim = claims[key] ?: TruthClaim(
        key = key,
        state = ClaimTruthState.UNKNOWN,
        observedAtEpochMs = 0,
        evidenceRef = null,
        authority = null,
    )

    val ownershipVerified: Boolean get() = claim("registered_owner").state == ClaimTruthState.REGISTRY_VERIFIED
    val titleIsNotKnownClean: Boolean get() = !claim("encumbrances").isVerified
}

data class PropertyListing(
    val listingId: UUID,
    val propertyId: UUID,
    val sellerPrincipalId: UUID,
    val operation: PropertyOperation,
    val propertyTypeCode: String,
    val approximateZone: String,
    val exactAddressEncryptedRef: String?,
    val addressDisclosure: AddressDisclosure,
    val state: PropertyListingState,
    val complianceApprovedAtEpochMs: Long?,
    val version: Long,
) {
    val canPublish: Boolean
        get() = approximateZone.isNotBlank() && propertyTypeCode.isNotBlank() &&
            (operation != PropertyOperation.PRESALE || complianceApprovedAtEpochMs != null)

    fun publicAddress(): String = approximateZone
}

object PropertyTrustEngine {
    fun assess(passport: PropertyPassport, sellerAuthorized: Boolean, duplicateListing: Boolean): PropertyTrustRisk {
        if (duplicateListing) return PropertyTrustRisk.HIGH_RISK
        val owner = passport.claim("registered_owner")
        val registry = passport.claim("registry_identity")
        if (owner.state == ClaimTruthState.DOCUMENT_OBSERVED && registry.state == ClaimTruthState.REGISTRY_VERIFIED) {
            return PropertyTrustRisk.REGISTRY_MISMATCH
        }
        if (owner.state == ClaimTruthState.REGISTRY_VERIFIED) return PropertyTrustRisk.VERIFIED_OWNER
        if (sellerAuthorized && registry.isVerified) return PropertyTrustRisk.AUTHORIZED_AGENT
        if (passport.claims.values.any { it.state == ClaimTruthState.DOCUMENT_OBSERVED }) {
            return PropertyTrustRisk.DOCUMENT_PENDING
        }
        return PropertyTrustRisk.UNKNOWN
    }
}
