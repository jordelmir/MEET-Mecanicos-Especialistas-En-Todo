package com.elysium369.meet.fuel.domain

import com.elysium369.meet.core.money.CurrencyCode
import com.elysium369.meet.core.money.Money
import java.net.URI
import java.util.UUID

enum class FuelPurchaseTruth { POS_AUTHORITATIVE, ERP_IMPORTED, RECEIPT_VERIFIED, STAFF_DECLARED, CUSTOMER_DECLARED }
enum class FuelPurchaseState { PENDING, SETTLED, VOIDED, REFUNDED }
enum class FuelCouponState { ISSUED, CLAIMED, RESERVED, REDEEMED, EXPIRED, REVOKED }
enum class FuelBenefitType { EXTERNAL_REWARD, STORE_ITEM_DISCOUNT, CAR_WASH_REWARD, POINTS, CASHBACK_ACCOUNTING, FREE_PRODUCT, PARTNER_REWARD, FUEL_PRICE_CREDIT }
enum class FuelIssuePolicy { ONE_PER_TRANSACTION, ONE_PER_EVERY_N_SPEND, MAX_N_PER_TRANSACTION, ONE_PER_DAY, ONE_PER_CUSTOMER, FIRST_PURCHASE_ONLY, NTH_PURCHASE, STREAK, SEGMENT_SPECIFIC }

data class FuelCampaignTerms(
    val startAtEpochMs: Long,
    val endAtEpochMs: Long,
    val benefitDescription: String,
    val eligibility: String,
    val restrictions: String,
    val redemptionProcedure: String,
    val termsVersion: Int,
    val termsHash: String,
) {
    init {
        require(endAtEpochMs > startAtEpochMs)
        require(benefitDescription.isNotBlank())
        require(eligibility.isNotBlank())
        require(restrictions.isNotBlank())
        require(redemptionProcedure.isNotBlank())
        require(termsVersion > 0)
        require(termsHash.matches(Regex("[a-f0-9]{64}")))
    }
}

data class FuelCampaignVersion(
    val campaignVersionId: UUID,
    val campaignId: UUID,
    val version: Int,
    val qualifyingSpend: Money,
    val issuePolicy: FuelIssuePolicy,
    val maxPerTransaction: Int?,
    val benefitType: FuelBenefitType,
    val terms: FuelCampaignTerms,
    val publishedAtEpochMs: Long?,
    val regulatoryApprovalRef: String?,
) {
    init {
        require(qualifyingSpend.currency == CurrencyCode.CRC)
        require(qualifyingSpend.amountMinor > 0)
        require(version > 0)
        require(maxPerTransaction == null || maxPerTransaction > 0)
        require(benefitType != FuelBenefitType.FUEL_PRICE_CREDIT || !regulatoryApprovalRef.isNullOrBlank())
    }

    val immutable: Boolean get() = publishedAtEpochMs != null
}

object FuelRewardPolicy {
    fun awardUnits(settledPurchase: Money, campaign: FuelCampaignVersion): Int {
        require(settledPurchase.currency == campaign.qualifyingSpend.currency)
        if (settledPurchase.amountMinor < campaign.qualifyingSpend.amountMinor) return 0
        val raw = when (campaign.issuePolicy) {
            FuelIssuePolicy.ONE_PER_EVERY_N_SPEND, FuelIssuePolicy.MAX_N_PER_TRANSACTION ->
                settledPurchase.amountMinor / campaign.qualifyingSpend.amountMinor
            else -> 1L
        }
        val capped = campaign.maxPerTransaction?.let { minOf(raw, it.toLong()) } ?: raw
        require(capped <= Int.MAX_VALUE) { "Reward unit overflow" }
        return capped.toInt()
    }
}

@JvmInline
value class OpaqueQrToken private constructor(val value: String) {
    companion object {
        private val token = Regex("[A-Za-z0-9_-]{22,128}")

        fun fromPublicUrl(raw: String, allowedHosts: Set<String>): OpaqueQrToken {
            val uri = runCatching { URI(raw) }.getOrElse { throw IllegalArgumentException("Invalid QR URL") }
            require(uri.scheme == "https") { "QR must use HTTPS" }
            require(uri.host in allowedHosts) { "Untrusted QR host" }
            val segments = uri.path.trim('/').split('/')
            require(segments.size == 2 && segments[0] == "q") { "Unexpected QR route" }
            require(token.matches(segments[1])) { "QR token lacks required entropy or shape" }
            require(uri.query == null && uri.fragment == null) { "QR cannot expose query data" }
            return OpaqueQrToken(segments[1])
        }
    }
}

data class FuelCoupon(
    val couponId: UUID,
    val campaignVersionId: UUID,
    val ownerCustomerId: UUID?,
    val issuedFromPurchaseId: UUID,
    val state: FuelCouponState,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val redeemedAtEpochMs: Long?,
    val redemptionId: UUID?,
)
