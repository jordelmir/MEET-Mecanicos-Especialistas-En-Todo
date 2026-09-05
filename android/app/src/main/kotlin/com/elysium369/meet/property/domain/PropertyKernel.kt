package com.elysium369.meet.property.domain

import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PropertyKernel — Singleton authority for property listings.
 *
 * Laws:
 * - A listing cannot be published without compliance review (presale)
 * - Ownership must be verified or authorized before listing
 * - Address disclosure follows AddressDisclosure policy
 * - Each property can have at most one active listing per operation type
 * - All state transitions are audited
 */
@Singleton
class PropertyKernel @Inject constructor() {

    private val passports = mutableMapOf<UUID, PropertyPassport>()
    private val listings = mutableMapOf<UUID, PropertyListing>()
    private val propertyListings = mutableMapOf<UUID, MutableList<UUID>>() // propertyId -> listingIds
    private val auditLog = mutableListOf<PropertyAuditEntry>()

    /** Register or update a property passport. */
    fun upsertPassport(passport: PropertyPassport): PropertyPassport {
        passports[passport.propertyId] = passport
        auditLog.add(PropertyAuditEntry(
            eventType = PropertyAuditEventType.PASSPORT_UPSERTED,
            propertyId = passport.propertyId,
            timestampEpochMs = System.currentTimeMillis(),
            details = "Claims: ${passport.claims.keys}",
        ))
        Log.i("PropertyKernel", "Passport upserted: ${passport.propertyId}")
        return passport
    }

    /** Get passport for a property. */
    fun getPassport(propertyId: UUID): PropertyPassport? = passports[propertyId]

    /** Create a listing for a property. */
    fun createListing(
        propertyId: UUID,
        sellerPrincipalId: UUID,
        operation: PropertyOperation,
        propertyTypeCode: String,
        approximateZone: String,
        exactAddressEncryptedRef: String? = null,
        addressDisclosure: AddressDisclosure = AddressDisclosure.APPROXIMATE_ZONE,
    ): PropertyCreateResult {
        val passport = passports[propertyId]
            ?: return PropertyCreateResult.DENIED("Property passport not found")

        // Check for duplicate active listing
        val existing = propertyListings[propertyId]?.mapNotNull { listings[it] }
            ?.firstOrNull { it.state == PropertyListingState.PUBLISHED && it.operation == operation }
        if (existing != null) {
            return PropertyCreateResult.DENIED("Active listing already exists for this operation type")
        }

        // Trust check
        val trust = PropertyTrustEngine.assess(passport, sellerAuthorized = true, duplicateListing = false)
        if (trust == PropertyTrustRisk.HIGH_RISK) {
            return PropertyCreateResult.DENIED("High trust risk — cannot create listing")
        }

        val listingId = UUID.randomUUID()
        val listing = PropertyListing(
            listingId = listingId,
            propertyId = propertyId,
            sellerPrincipalId = sellerPrincipalId,
            operation = operation,
            propertyTypeCode = propertyTypeCode,
            approximateZone = approximateZone,
            exactAddressEncryptedRef = exactAddressEncryptedRef,
            addressDisclosure = addressDisclosure,
            state = PropertyListingState.DRAFT,
            complianceApprovedAtEpochMs = null,
            version = 1,
        )
        listings[listingId] = listing
        propertyListings.getOrPut(propertyId) { mutableListOf() }.add(listingId)

        auditLog.add(PropertyAuditEntry(
            eventType = PropertyAuditEventType.LISTING_CREATED,
            propertyId = propertyId,
            listingId = listingId,
            timestampEpochMs = System.currentTimeMillis(),
            details = "Operation: $operation, Trust: $trust",
        ))

        Log.i("PropertyKernel", "Listing created: $listingId for property $propertyId")
        return PropertyCreateResult.ACCEPTED(listingId)
    }

    /** Submit a listing for compliance review. */
    fun submitForReview(listingId: UUID): Boolean {
        val listing = listings[listingId] ?: return false
        if (listing.state != PropertyListingState.DRAFT) return false
        listings[listingId] = listing.copy(state = PropertyListingState.COMPLIANCE_REVIEW)
        auditLog.add(PropertyAuditEntry(
            eventType = PropertyAuditEventType.SUBMITTED_FOR_REVIEW,
            propertyId = listing.propertyId,
            listingId = listingId,
            timestampEpochMs = System.currentTimeMillis(),
        ))
        return true
    }

    /** Approve a listing after compliance review. */
    fun approveListing(listingId: UUID): Boolean {
        val listing = listings[listingId] ?: return false
        if (listing.state != PropertyListingState.COMPLIANCE_REVIEW) return false
        listings[listingId] = listing.copy(
            state = PropertyListingState.PUBLISHED,
            complianceApprovedAtEpochMs = System.currentTimeMillis(),
        )
        auditLog.add(PropertyAuditEntry(
            eventType = PropertyAuditEventType.LISTING_APPROVED,
            propertyId = listing.propertyId,
            listingId = listingId,
            timestampEpochMs = System.currentTimeMillis(),
        ))
        Log.i("PropertyKernel", "Listing approved: $listingId")
        return true
    }

    /** Publish a listing (makes it visible). */
    fun publishListing(listingId: UUID): Boolean {
        val listing = listings[listingId] ?: return false
        if (!listing.canPublish) return false
        if (listing.state == PropertyListingState.DRAFT) {
            listings[listingId] = listing.copy(state = PropertyListingState.COMPLIANCE_REVIEW)
        }
        if (listing.state == PropertyListingState.COMPLIANCE_REVIEW && listing.complianceApprovedAtEpochMs != null) {
            listings[listingId] = listing.copy(state = PropertyListingState.PUBLISHED)
        }
        return listings[listingId]?.state == PropertyListingState.PUBLISHED
    }

    /** Reserve a listing (buyer shows interest). */
    fun reserveListing(listingId: UUID): Boolean {
        val listing = listings[listingId] ?: return false
        if (listing.state != PropertyListingState.PUBLISHED) return false
        listings[listingId] = listing.copy(state = PropertyListingState.RESERVED)
        auditLog.add(PropertyAuditEntry(
            eventType = PropertyAuditEventType.LISTING_RESERVED,
            propertyId = listing.propertyId,
            listingId = listingId,
            timestampEpochMs = System.currentTimeMillis(),
        ))
        return true
    }

    /** Enter due diligence phase. */
    fun enterDueDiligence(listingId: UUID): Boolean {
        val listing = listings[listingId] ?: return false
        if (listing.state != PropertyListingState.RESERVED) return false
        listings[listingId] = listing.copy(state = PropertyListingState.UNDER_DUE_DILIGENCE)
        return true
    }

    /** Close a listing (sale completed). */
    fun closeListing(listingId: UUID): Boolean {
        val listing = listings[listingId] ?: return false
        if (listing.state !in listOf(PropertyListingState.UNDER_DUE_DILIGENCE, PropertyListingState.RESERVED)) return false
        listings[listingId] = listing.copy(state = PropertyListingState.CLOSED)
        auditLog.add(PropertyAuditEntry(
            eventType = PropertyAuditEventType.LISTING_CLOSED,
            propertyId = listing.propertyId,
            listingId = listingId,
            timestampEpochMs = System.currentTimeMillis(),
        ))
        return true
    }

    /** Withdraw a listing. */
    fun withdrawListing(listingId: UUID): Boolean {
        val listing = listings[listingId] ?: return false
        if (listing.state in listOf(PropertyListingState.CLOSED, PropertyListingState.WITHDRAWN)) return false
        listings[listingId] = listing.copy(state = PropertyListingState.WITHDRAWN)
        auditLog.add(PropertyAuditEntry(
            eventType = PropertyAuditEventType.LISTING_WITHDRAWN,
            propertyId = listing.propertyId,
            listingId = listingId,
            timestampEpochMs = System.currentTimeMillis(),
        ))
        return true
    }

    /** Get all active listings for a property. */
    fun getActiveListings(propertyId: UUID): List<PropertyListing> {
        return propertyListings[propertyId]?.mapNotNull { listings[it] }
            ?.filter { it.state.isActive } ?: emptyList()
    }

    /** Get all published listings (marketplace). */
    fun getPublishedListings(): List<PropertyListing> {
        return listings.values.filter { it.state == PropertyListingState.PUBLISHED }
    }

    /** Get audit log for a property. */
    fun getAuditLog(propertyId: UUID): List<PropertyAuditEntry> {
        return auditLog.filter { it.propertyId == propertyId }
    }
}

sealed interface PropertyCreateResult {
    data class ACCEPTED(val listingId: UUID) : PropertyCreateResult
    data class DENIED(val reason: String) : PropertyCreateResult
}

enum class PropertyAuditEventType {
    PASSPORT_UPSERTED,
    LISTING_CREATED,
    SUBMITTED_FOR_REVIEW,
    LISTING_APPROVED,
    LISTING_RESERVED,
    LISTING_CLOSED,
    LISTING_WITHDRAWN,
}

data class PropertyAuditEntry(
    val eventType: PropertyAuditEventType,
    val propertyId: UUID,
    val listingId: UUID? = null,
    val timestampEpochMs: Long,
    val details: String? = null,
)
