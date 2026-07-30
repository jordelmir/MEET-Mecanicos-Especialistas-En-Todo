package com.elysium369.meet.ride.domain

import java.util.Locale

@JvmInline
value class CurrencyCode private constructor(val value: String) {
    companion object {
        private val ISO_4217_SHAPE = Regex("[A-Z]{3}")

        fun of(raw: String): CurrencyCode {
            val normalized = raw.trim().uppercase(Locale.ROOT)
            require(ISO_4217_SHAPE.matches(normalized)) {
                "Currency must be a three-letter ISO-4217 code"
            }
            return CurrencyCode(normalized)
        }
    }

    override fun toString(): String = value
}

data class RideMoney(
    val minorUnits: Long,
    val currency: CurrencyCode,
) {
    init {
        require(minorUnits >= 0) { "Money cannot be negative" }
    }

    operator fun plus(other: RideMoney): RideMoney {
        requireSameCurrency(other)
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
    }

    operator fun minus(other: RideMoney): RideMoney {
        requireSameCurrency(other)
        require(minorUnits >= other.minorUnits) { "Money result cannot be negative" }
        return copy(minorUnits = minorUnits - other.minorUnits)
    }

    fun commission(basisPoints: Int = DEFAULT_COMMISSION_BASIS_POINTS): RideMoney {
        return copy(
            minorUnits = CommissionCalculator.calculate(
                base = AmountMinor.of(minorUnits),
                rate = BasisPoints.of(basisPoints),
            ).value,
        )
    }

    private fun requireSameCurrency(other: RideMoney) {
        require(currency == other.currency) {
            "Cannot combine ${currency.value} and ${other.currency.value}"
        }
    }

    companion object {
        const val DEFAULT_COMMISSION_BASIS_POINTS =
            RideCommissionPolicy.PLATFORM_RATE_BASIS_POINTS

        fun of(minorUnits: Long, currency: String): RideMoney =
            RideMoney(minorUnits, CurrencyCode.of(currency))

        fun zero(currency: CurrencyCode): RideMoney = RideMoney(0, currency)
    }
}

object CostaRicaRidePolicy {
    val promotionalGrant: RideMoney = RideMoney.of(
        minorUnits = 100_000,
        currency = "CRC",
    )
}
