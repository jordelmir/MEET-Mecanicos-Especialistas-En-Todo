package com.elysium369.meet.platform.marketos.data

import com.elysium369.meet.data.local.entities.FuelCouponProjectionEntity
import com.elysium369.meet.data.local.entities.LegalMatterProjectionEntity
import com.elysium369.meet.data.local.entities.MarketCommandOutboxEntity
import com.elysium369.meet.data.local.entities.MarketOrganizationProjectionEntity
import com.elysium369.meet.data.local.entities.PropertyListingProjectionEntity
import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

sealed interface MarketCommandRemoteResult {
    data class Accepted(val result: JsonObject) : MarketCommandRemoteResult
    data class Rejected(val code: String, val retryable: Boolean) : MarketCommandRemoteResult
    data class TransportFailure(val code: String) : MarketCommandRemoteResult
}

data class MarketProjectionSnapshot(
    val organizations: List<MarketOrganizationProjectionEntity>,
    val legalMatters: List<LegalMatterProjectionEntity>,
    val propertyListings: List<PropertyListingProjectionEntity>,
    val fuelCoupons: List<FuelCouponProjectionEntity>,
)

data class MarketCatalogCategory(
    val code: String,
    val parentCode: String?,
    val displayName: String,
    val sortOrder: Int,
    val taxonomyVersion: Int,
    val sourceCheckedAtEpochMs: Long,
)

interface MarketOsRemoteGateway {
    suspend fun execute(command: MarketCommandOutboxEntity): MarketCommandRemoteResult
    suspend fun fetchVisibleSnapshot(ownerPrincipalId: String): Result<MarketProjectionSnapshot>
    suspend fun fetchCatalog(vertical: String, jurisdiction: String = "CR"): Result<List<MarketCatalogCategory>>
    fun realtimeWakeUps(): Flow<Unit>
}

@Serializable
private data class CatalogCategoryWire(
    val code: String,
    @SerialName("parent_code") val parentCode: String? = null,
    @SerialName("display_name_es") val displayName: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("taxonomy_version") val taxonomyVersion: Int,
    @SerialName("source_checked_at") val sourceCheckedAt: String,
)

@Serializable
private data class OrganizationWire(
    @SerialName("organization_id") val organizationId: String,
    val kind: String,
    @SerialName("commercial_name") val commercialName: String,
    val status: String,
    val version: Long,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class MembershipWire(
    @SerialName("organization_id") val organizationId: String,
    val roles: List<String>,
)

@Serializable
private data class LegalMatterWire(
    @SerialName("matter_id") val matterId: String,
    @SerialName("category_code") val categoryCode: String,
    @SerialName("human_summary") val humanSummary: String,
    val state: String,
    @SerialName("disclosure_level") val disclosureLevel: String,
    val version: Long,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class LegalDeadlineWire(
    @SerialName("matter_id") val matterId: String,
    @SerialName("due_at") val dueAt: String,
    val status: String,
)

@Serializable
private data class PropertyListingWire(
    @SerialName("listing_id") val listingId: String,
    @SerialName("property_id") val propertyId: String,
    val operation: String,
    @SerialName("property_type_code") val propertyTypeCode: String,
    @SerialName("approximate_zone") val approximateZone: String,
    @SerialName("asking_amount_minor") val askingAmountMinor: Long,
    val currency: String,
    @SerialName("ownership_truth") val ownershipTruth: String,
    @SerialName("registry_truth") val registryTruth: String,
    @SerialName("listing_version") val listingVersion: Long,
)

@Serializable
private data class FuelWalletWire(
    @SerialName("coupon_id") val couponId: String,
    @SerialName("campaign_version_id") val campaignVersionId: String,
    @SerialName("benefit_title") val benefitTitle: String,
    @SerialName("opaque_public_url") val opaquePublicUrl: String? = null,
    val state: String,
    @SerialName("expires_at") val expiresAt: String,
    val version: Long,
    @SerialName("updated_at") val updatedAt: String,
)

@Singleton
class SupabaseMarketOsRemoteGateway @Inject constructor() : MarketOsRemoteGateway {
    override suspend fun execute(command: MarketCommandOutboxEntity): MarketCommandRemoteResult {
        val client = SupabaseModule.client
        if (client.auth.currentUserOrNull()?.id != command.ownerPrincipalId) {
            return MarketCommandRemoteResult.Rejected("AUTH_SESSION_MISMATCH", retryable = false)
        }
        val invocation = runCatching { command.toInvocation() }.getOrElse {
            return MarketCommandRemoteResult.Rejected("INVALID_LOCAL_COMMAND", retryable = false)
        }
        return try {
            val response = client.postgrest.rpc(invocation.first, invocation.second)
                .decodeAs<kotlinx.serialization.json.JsonElement>()
                .jsonObject
            MarketCommandRemoteResult.Accepted(response)
        } catch (error: Exception) {
            val code = error.marketErrorCode()
            if (code in TERMINAL_CODES) {
                MarketCommandRemoteResult.Rejected(code, retryable = false)
            } else if (code in CONFLICT_CODES) {
                MarketCommandRemoteResult.Rejected(code, retryable = false)
            } else {
                MarketCommandRemoteResult.TransportFailure(code)
            }
        }
    }

    override suspend fun fetchVisibleSnapshot(ownerPrincipalId: String): Result<MarketProjectionSnapshot> = runCatching {
        val client = SupabaseModule.client
        require(client.auth.currentUserOrNull()?.id == ownerPrincipalId) { "AUTH_SESSION_MISMATCH" }
        val now = System.currentTimeMillis()
        val memberships = client.postgrest["market_organization_members"]
            .select().decodeList<MembershipWire>().associateBy(MembershipWire::organizationId)
        val organizations = client.postgrest["market_organizations"]
            .select().decodeList<OrganizationWire>().map { row ->
                MarketOrganizationProjectionEntity(
                    ownerPrincipalId = ownerPrincipalId,
                    organizationId = row.organizationId,
                    kind = row.kind,
                    commercialName = row.commercialName,
                    rolesJson = memberships[row.organizationId]?.roles.orEmpty().sorted().joinToString(","),
                    status = row.status,
                    serverVersion = row.version,
                    updatedAtEpochMs = row.updatedAt.epochMs(now),
                )
            }
        val deadlines = client.postgrest["legal_deadlines"]
            .select().decodeList<LegalDeadlineWire>()
            .asSequence().filter { it.status == "OPEN" }
            .groupBy(LegalDeadlineWire::matterId)
            .mapValues { (_, values) -> values.minOfOrNull { it.dueAt.epochMs(Long.MAX_VALUE) } }
        val matters = client.postgrest["legal_matters"]
            .select().decodeList<LegalMatterWire>().map { row ->
                LegalMatterProjectionEntity(
                    ownerPrincipalId = ownerPrincipalId,
                    matterId = row.matterId,
                    categoryCode = row.categoryCode,
                    humanSummary = row.humanSummary,
                    state = row.state,
                    disclosureLevel = row.disclosureLevel,
                    nextDeadlineEpochMs = deadlines[row.matterId],
                    serverVersion = row.version,
                    updatedAtEpochMs = row.updatedAt.epochMs(now),
                )
            }
        val listings = client.postgrest.rpc(
            "search_property_listings_v1",
            buildJsonObject {
                put("p_operation", JsonNull)
                put("p_limit", 100)
            },
        ).decodeList<PropertyListingWire>().map { row ->
            PropertyListingProjectionEntity(
                ownerPrincipalId = ownerPrincipalId,
                listingId = row.listingId,
                propertyId = row.propertyId,
                operation = row.operation,
                propertyTypeCode = row.propertyTypeCode,
                approximateZone = row.approximateZone,
                askingAmountMinor = row.askingAmountMinor,
                currency = row.currency,
                trustSummaryJson = "{\"ownership\":\"${row.ownershipTruth}\",\"registry\":\"${row.registryTruth}\"}",
                state = "PUBLISHED",
                serverVersion = row.listingVersion,
                updatedAtEpochMs = now,
            )
        }
        val coupons = client.postgrest.rpc("get_fuel_wallet_v1")
            .decodeList<FuelWalletWire>().map { row ->
                FuelCouponProjectionEntity(
                    ownerPrincipalId = ownerPrincipalId,
                    couponId = row.couponId,
                    campaignVersionId = row.campaignVersionId,
                    benefitTitle = row.benefitTitle,
                    opaquePublicUrl = row.opaquePublicUrl,
                    state = row.state,
                    expiresAtEpochMs = row.expiresAt.epochMs(now),
                    serverVersion = row.version,
                    updatedAtEpochMs = row.updatedAt.epochMs(now),
                )
            }
        MarketProjectionSnapshot(organizations, matters, listings, coupons)
    }

    override suspend fun fetchCatalog(
        vertical: String,
        jurisdiction: String,
    ): Result<List<MarketCatalogCategory>> = runCatching {
        require(vertical in setOf("LEGAL", "REAL_ESTATE", "FUEL_REWARDS"))
        SupabaseModule.client.postgrest.rpc(
            "get_market_catalog_v1",
            buildJsonObject {
                put("p_vertical", vertical)
                put("p_jurisdiction", jurisdiction)
            },
        ).decodeList<CatalogCategoryWire>().map { row ->
            MarketCatalogCategory(
                code = row.code,
                parentCode = row.parentCode,
                displayName = row.displayName,
                sortOrder = row.sortOrder,
                taxonomyVersion = row.taxonomyVersion,
                sourceCheckedAtEpochMs = row.sourceCheckedAt.epochMs(0L),
            )
        }
    }

    override fun realtimeWakeUps(): Flow<Unit> = flow {
        val channel = SupabaseModule.client.channel("elysium-market-os-projection")
        val changes = listOf("market_organizations", "legal_matters", "property_listings", "fuel_coupons")
            .map { table ->
                channel.postgresChangeFlow<PostgresAction>(schema = "public") { this.table = table }.map { Unit }
            }
        try {
            channel.subscribe()
            emit(Unit)
            emitAll(merge(*changes.toTypedArray()))
        } finally {
            channel.unsubscribe()
        }
    }

    private fun MarketCommandOutboxEntity.toInvocation(): Pair<String, JsonObject> {
        val payload = kotlinx.serialization.json.Json.parseToJsonElement(payloadJson).jsonObject
        val function = when (commandType) {
            "CREATE_ORGANIZATION" -> "create_market_organization_v1"
            "CREATE_LEGAL_MATTER" -> "create_legal_matter_v1"
            "SUBMIT_LEGAL_PROFILE" -> "submit_legal_professional_profile_v1"
            "SUBMIT_LEGAL_OFFER" -> "submit_legal_offer_v1"
            "RECORD_LEGAL_CONFLICT_CHECK" -> "record_legal_conflict_check_v1"
            "ACCEPT_LEGAL_OFFER" -> "accept_legal_offer_v1"
            "CREATE_PROPERTY_ASSET" -> "create_property_asset_v1"
            "CREATE_PROPERTY_LISTING" -> "create_property_listing_v1"
            "CREATE_PROPERTY_INQUIRY" -> "create_property_inquiry_v1"
            "GRANT_PROPERTY_ADDRESS" -> "grant_property_exact_address_v1"
            "PUBLISH_PROPERTY_LISTING" -> "publish_property_listing_v1"
            "SUBMIT_PROPERTY_OFFER" -> "submit_property_offer_v1"
            "ACCEPT_PROPERTY_OFFER" -> "accept_property_offer_v1"
            "PUBLISH_FUEL_CAMPAIGN" -> "publish_fuel_campaign_version_v1"
            "CREATE_FUEL_STATION" -> "create_fuel_station_v1"
            "CREATE_FUEL_CAMPAIGN_DRAFT" -> "create_fuel_campaign_draft_v1"
            "CREATE_FUEL_CAMPAIGN_VERSION" -> "create_fuel_campaign_version_v1"
            "RECORD_FUEL_CONSENT" -> "record_fuel_customer_consent_v1"
            "RECORD_FUEL_PURCHASE" -> "record_fuel_purchase_v1"
            "CLAIM_FUEL_PURCHASE" -> "claim_fuel_purchase_v1"
            "ISSUE_FUEL_REWARDS" -> "issue_fuel_rewards_v1"
            "REDEEM_FUEL_COUPON" -> "redeem_fuel_coupon_v1"
            "VOID_FUEL_PURCHASE" -> "void_fuel_purchase_v1"
            "REFUND_FUEL_PURCHASE" -> "refund_fuel_purchase_v1"
            "RECORD_MARKET_AI_CONSENT" -> "record_market_ai_consent_v1"
            else -> error("Unsupported command type")
        }
        val parameters = buildJsonObject {
            payload.forEach { (key, value) -> put(key, value) }
            put("p_idempotency_key", idempotencyKey)
            when (commandType) {
                "RECORD_LEGAL_CONFLICT_CHECK" -> put("p_expected_matter_version", expectedVersion)
                "ACCEPT_LEGAL_OFFER" -> put("p_expected_matter_version", expectedVersion)
                "SUBMIT_LEGAL_OFFER" -> put("p_expected_matter_version", expectedVersion)
                "GRANT_PROPERTY_ADDRESS" -> put("p_expected_property_version", expectedVersion)
                "PUBLISH_PROPERTY_LISTING", "VOID_FUEL_PURCHASE", "REFUND_FUEL_PURCHASE" -> put("p_expected_version", expectedVersion)
                "SUBMIT_PROPERTY_OFFER", "ACCEPT_PROPERTY_OFFER" -> put("p_expected_listing_version", expectedVersion)
                "PUBLISH_FUEL_CAMPAIGN" -> put("p_expected_campaign_version", expectedVersion)
                "CREATE_FUEL_CAMPAIGN_VERSION" -> put("p_expected_campaign_version", expectedVersion)
                "ISSUE_FUEL_REWARDS" -> put("p_expected_purchase_version", expectedVersion)
            }
        }
        return function to parameters
    }

    private fun Throwable.marketErrorCode(): String {
        val message = message.orEmpty().uppercase()
        return (TERMINAL_CODES + CONFLICT_CODES).firstOrNull(message::contains)
            ?: if (message.contains("JWT") || message.contains("AUTH")) "AUTH_REQUIRED" else "REMOTE_TRANSPORT_FAILURE"
    }

    private companion object {
        val CONFLICT_CODES = setOf("VERSION_CONFLICT", "OFFER_NOT_ACCEPTABLE", "COUPON_NOT_REDEEMABLE")
        val TERMINAL_CODES = setOf(
            "AUTH_REQUIRED", "ACTIVE_CAAB_REQUIRED", "FIRM_ROLE_REQUIRED", "STATION_ROLE_REQUIRED",
            "INVALID_SUMMARY", "UNKNOWN_LEGAL_CATEGORY", "PRESALE_COMPLIANCE_REQUIRED", "WRONG_STATION",
            "COUPON_EXPIRED", "COUPON_NOT_FOUND", "PURCHASE_NOT_AUTHORITATIVE", "INVALID_LOCAL_COMMAND",
            "EXACT_ADDRESS_GRANT_REQUIRED", "LEGAL_DOCUMENT_ACCESS_DENIED", "QR_REPLAY_REJECTED",
            "QR_PRESENTATION_EXPIRED", "PURCHASE_QR_REPLAY_REJECTED", "PURCHASE_QR_EXPIRED",
        )
    }
}

private fun String.epochMs(fallback: Long): Long =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(fallback)
