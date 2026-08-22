package com.elysium369.meet.core.money

import java.text.NumberFormat
import java.util.Locale

/**
 * MEET Vehicle Life OS — Immutable Financial Value Object.
 * Enforces Doctrine #7: Zero Double/Float representations for monetary amounts.
 * Stores values strictly as minor units (e.g., cents, centavos) in [Long].
 */
data class Money(
    val amountMinor: Long,
    val currency: CurrencyCode
) : Comparable<Money> {

    init {
        require(amountMinor >= 0) { "Money amount cannot be negative: $amountMinor" }
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add distinct currencies: $currency and ${other.currency}" }
        val sum = Math.addExact(amountMinor, other.amountMinor)
        return Money(sum, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract distinct currencies: $currency and ${other.currency}" }
        require(amountMinor >= other.amountMinor) { "Resulting money cannot be negative" }
        return Money(amountMinor - other.amountMinor, currency)
    }

    operator fun times(multiplier: Int): Money {
        require(multiplier >= 0) { "Multiplier cannot be negative: $multiplier" }
        val product = Math.multiplyExact(amountMinor, multiplier.toLong())
        return Money(product, currency)
    }

    operator fun div(divisor: Long): Money {
        require(divisor > 0) { "Divisor must be strictly positive" }
        return Money(amountMinor / divisor, currency)
    }

    override fun compareTo(other: Money): Int {
        require(currency == other.currency) { "Cannot compare distinct currencies: $currency and ${other.currency}" }
        return amountMinor.compareTo(other.amountMinor)
    }

    fun formatted(): String {
        return when (currency.decimalPlaces) {
            0 -> "${currency.symbol}${NumberFormat.getIntegerInstance(Locale.US).format(amountMinor)}"
            2 -> {
                val major = amountMinor / 100
                val minor = amountMinor % 100
                val formattedMajor = NumberFormat.getIntegerInstance(Locale.US).format(major)
                "${currency.symbol}$formattedMajor.%02d".format(minor)
            }
            else -> "${currency.symbol}$amountMinor"
        }
    }

    companion object {
        fun zero(currency: CurrencyCode): Money = Money(0L, currency)

        fun fromMajor(amountMajor: Long, currency: CurrencyCode): Money {
            val factor = if (currency.decimalPlaces == 2) 100L else 1L
            val minor = Math.multiplyExact(amountMajor, factor)
            return Money(minor, currency)
        }

        fun fromMajorUnits(amountMajor: Long, amountMinorCents: Long, currency: CurrencyCode): Money {
            val factor = if (currency.decimalPlaces == 2) 100L else 1L
            val minor = Math.addExact(Math.multiplyExact(amountMajor, factor), amountMinorCents)
            return Money(minor, currency)
        }
    }
}
