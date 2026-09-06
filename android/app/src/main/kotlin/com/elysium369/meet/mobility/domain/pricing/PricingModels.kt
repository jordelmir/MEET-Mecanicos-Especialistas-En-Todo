package com.elysium369.meet.mobility.domain.pricing

import com.elysium369.meet.mobility.domain.models.MarketId
import com.elysium369.meet.mobility.domain.models.Money
import com.elysium369.meet.mobility.domain.models.ServiceCategoryId
import java.time.Instant
import java.util.UUID

data class Rate(
    val numerator: Long,
    val denominator: Long,
) {
    init {
        require(denominator > 0L) { "Rate denominator must be > 0, got $denominator" }
        require(numerator >= 0L) { "Rate numerator must be >= 0, got $numerator" }
    }

    companion object {
        val ONE = Rate(1L, 1L)
        fun ofBasisPoints(bps: Long): Rate = Rate(bps, 10_000L)
        fun ofPercentage(pct: Long): Rate = Rate(pct, 100L)
    }
}

fun Money.multiply(rate: Rate): Money {
    val product = Math.multiplyExact(minorUnits, rate.numerator)
    return copy(minorUnits = product / rate.denominator)
}

enum class PricingMode {
    PLATFORM_UPFRONT,
    DYNAMIC_PLATFORM,
    RIDER_PROPOSED,
    DRIVER_NEGOTIATED,
    REGULATED_METERED,
    FIXED_ZONE,
}

data class RideQuote(
    val quoteId: UUID,
    val rideRequestId: UUID?,
    val marketId: MarketId,
    val serviceCategoryId: ServiceCategoryId,
    val baseFare: Money,
    val distanceFare: Money,
    val timeFare: Money,
    val demandAdjustment: Money,
    val tollEstimate: Money,
    val taxes: Money,
    val discount: Money,
    val total: Money,
    val expiresAt: Instant,
    val pricingPolicyVersion: Long,
) {
    init {
        require(pricingPolicyVersion >= 1L) { "pricingPolicyVersion must be >= 1" }
        require(total.minorUnits >= 0L) { "Quote total cannot be negative" }
    }
}

data class PricingInput(
    val marketId: MarketId,
    val categoryId: ServiceCategoryId,
    val distanceMeters: Long,
    val estimatedDurationSeconds: Long,
    val scheduledFor: Instant? = null,
    val demandMultiplierRate: Rate = Rate.ONE,
)
