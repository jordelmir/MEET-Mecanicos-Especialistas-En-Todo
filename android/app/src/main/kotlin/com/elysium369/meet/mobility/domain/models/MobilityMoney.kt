package com.elysium369.meet.mobility.domain.models

import java.util.Locale

@JvmInline
value class CurrencyCode private constructor(val value: String) {
    companion object {
        private val ISO_REGEX = Regex("^[A-Z]{3}$")

        fun of(raw: String): CurrencyCode {
            val normalized = raw.trim().uppercase(Locale.ROOT)
            require(ISO_REGEX.matches(normalized)) {
                "CurrencyCode must be an ISO-4217 3-letter code, got '$raw'"
            }
            return CurrencyCode(normalized)
        }
    }

    override fun toString(): String = value
}

data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode,
) {
    init {
        require(minorUnits >= 0L) { "Money minorUnits must be non-negative: $minorUnits" }
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
        return Money(Math.addExact(minorUnits, other.minorUnits), currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
        require(minorUnits >= other.minorUnits) { "Money subtraction cannot be negative" }
        return Money(minorUnits - other.minorUnits, currency)
    }

    companion object {
        fun of(minorUnits: Long, currency: String): Money = Money(minorUnits, CurrencyCode.of(currency))
        fun zero(currency: CurrencyCode): Money = Money(0L, currency)
    }
}

data class SignedMoney(
    val minorUnits: Long,
    val currency: CurrencyCode,
) {
    companion object {
        fun of(minorUnits: Long, currency: String): SignedMoney = SignedMoney(minorUnits, CurrencyCode.of(currency))
    }
}
