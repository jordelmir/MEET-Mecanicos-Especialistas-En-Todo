package com.elysium369.meet.ride.domain

@JvmInline
value class AmountMinor private constructor(val value: Long) {
    companion object {
        val zero: AmountMinor = AmountMinor(0)

        fun of(value: Long): AmountMinor {
            require(value >= 0) { "Amount in minor units cannot be negative" }
            return AmountMinor(value)
        }
    }
}

@JvmInline
value class BasisPoints private constructor(val value: Int) {
    companion object {
        const val SCALE = 10_000

        fun of(value: Int): BasisPoints {
            require(value in 0..SCALE) {
                "Basis points must be between 0 and $SCALE"
            }
            return BasisPoints(value)
        }
    }
}

/**
 * Every field uses the same [currency]. Excluded fields remain explicit so a
 * caller cannot accidentally add them to the commissionable base.
 */
data class CommissionableRideAmounts(
    val currency: CurrencyCode,
    val transportFare: AmountMinor = AmountMinor.zero,
    val approvedWait: AmountMinor = AmountMinor.zero,
    val approvedStops: AmountMinor = AmountMinor.zero,
    val approvedSurcharges: AmountMinor = AmountMinor.zero,
    val collectedCancellationFee: AmountMinor = AmountMinor.zero,
    val driverFundedDiscount: AmountMinor = AmountMinor.zero,
    val refundedCommissionable: AmountMinor = AmountMinor.zero,
    val tip: AmountMinor = AmountMinor.zero,
    val tolls: AmountMinor = AmountMinor.zero,
    val taxes: AmountMinor = AmountMinor.zero,
    val platformFundedPromotion: AmountMinor = AmountMinor.zero,
    val processorFees: AmountMinor = AmountMinor.zero,
) {
    fun commissionableBase(): AmountMinor {
        val earned = listOf(
            transportFare,
            approvedWait,
            approvedStops,
            approvedSurcharges,
            collectedCancellationFee,
        ).fold(0L) { total, amount -> Math.addExact(total, amount.value) }

        val reductions = listOf(
            driverFundedDiscount,
            refundedCommissionable,
        ).fold(0L) { total, amount -> Math.addExact(total, amount.value) }

        return AmountMinor.of(
            if (reductions >= earned) 0 else earned - reductions,
        )
    }
}
data class RideCommissionCalculation(
    val commissionableBase: RideMoney,
    val platformCommission: RideMoney,
    val policyVersion: String,
)

object CommissionCalculator {
    private const val HALF_SCALE = BasisPoints.SCALE / 2L

    /**
     * Calculates half-up commission without multiplying a potentially
     * Long.MAX_VALUE base directly by the rate.
     */
    fun calculate(
        base: AmountMinor,
        rate: BasisPoints,
    ): AmountMinor {
        val scale = BasisPoints.SCALE.toLong()
        val wholeBase = base.value / scale
        val remainder = base.value % scale
        val wholeCommission = Math.multiplyExact(wholeBase, rate.value.toLong())
        val roundedRemainder = (
            Math.multiplyExact(remainder, rate.value.toLong()) + HALF_SCALE
            ) / scale

        return AmountMinor.of(
            Math.addExact(wholeCommission, roundedRemainder),
        )
    }
}

object RideCommissionPolicy {
    const val PLATFORM_RATE_BASIS_POINTS = 500
    const val version = "ride-commission-v1"

    val platformRate: BasisPoints = BasisPoints.of(PLATFORM_RATE_BASIS_POINTS)

    fun calculate(amounts: CommissionableRideAmounts): RideCommissionCalculation {
        val base = amounts.commissionableBase()
        val commission = CommissionCalculator.calculate(
            base = base,
            rate = platformRate,
        )
        return RideCommissionCalculation(
            commissionableBase = RideMoney(base.value, amounts.currency),
            platformCommission = RideMoney(commission.value, amounts.currency),
            policyVersion = version,
        )
    }
}
