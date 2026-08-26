package com.elysium369.meet.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "market_organization_projections",
    primaryKeys = ["ownerPrincipalId", "organizationId"],
    indices = [Index(value = ["ownerPrincipalId", "updatedAtEpochMs"])],
)
data class MarketOrganizationProjectionEntity(
    val ownerPrincipalId: String,
    val organizationId: String,
    val kind: String,
    val commercialName: String,
    val rolesJson: String,
    val status: String,
    val serverVersion: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "legal_matter_projections",
    primaryKeys = ["ownerPrincipalId", "matterId"],
    indices = [Index(value = ["ownerPrincipalId", "state", "updatedAtEpochMs"])],
)
data class LegalMatterProjectionEntity(
    val ownerPrincipalId: String,
    val matterId: String,
    val categoryCode: String,
    val humanSummary: String,
    val state: String,
    val disclosureLevel: String,
    val nextDeadlineEpochMs: Long?,
    val serverVersion: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "property_listing_projections",
    primaryKeys = ["ownerPrincipalId", "listingId"],
    indices = [Index(value = ["ownerPrincipalId", "state", "updatedAtEpochMs"])],
)
data class PropertyListingProjectionEntity(
    val ownerPrincipalId: String,
    val listingId: String,
    val propertyId: String,
    val operation: String,
    val propertyTypeCode: String,
    val approximateZone: String,
    val askingAmountMinor: Long,
    val currency: String,
    val trustSummaryJson: String,
    val state: String,
    val serverVersion: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "fuel_coupon_projections",
    primaryKeys = ["ownerPrincipalId", "couponId"],
    indices = [Index(value = ["ownerPrincipalId", "state", "expiresAtEpochMs"])],
)
data class FuelCouponProjectionEntity(
    val ownerPrincipalId: String,
    val couponId: String,
    val campaignVersionId: String,
    val benefitTitle: String,
    val opaquePublicUrl: String?,
    val state: String,
    val expiresAtEpochMs: Long,
    val serverVersion: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "market_command_outbox",
    primaryKeys = ["ownerPrincipalId", "idempotencyKey"],
    indices = [Index(value = ["ownerPrincipalId", "status", "nextAttemptAtEpochMs"])],
)
data class MarketCommandOutboxEntity(
    val ownerPrincipalId: String,
    val idempotencyKey: String,
    val commandId: String,
    val aggregateId: String,
    val aggregateType: String,
    val commandType: String,
    val expectedVersion: Long,
    val canonicalDigest: String,
    val payloadJson: String,
    val payloadVersion: Int,
    val status: String,
    val attemptCount: Int,
    val nextAttemptAtEpochMs: Long,
    val lastErrorCode: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
