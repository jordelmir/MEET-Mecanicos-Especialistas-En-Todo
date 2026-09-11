package com.elysium369.meet.core.money

import java.util.Locale

/**
 * Exception thrown when a monetary operation encounters an unsupported or unmapped currency.
 * Fails closed to prevent financial contamination.
 */
class UnsupportedCurrencyException(currencyRaw: String) :
    IllegalArgumentException("Unsupported currency code: '$currencyRaw'. Financial operations must fail closed.")

/**
 * Supported currency codes for MEET Commerce, Ledger, and TCO.
 * Canonical ISO 4217 representation — single source of truth.
 */
enum class CurrencyCode(val standardSymbol: String, val decimalPlaces: Int) {
    CRC("₡", 0), // Costa Rican Colón (no decimals in minor units)
    USD("$", 2), // US Dollar (minor unit = cents)
    EUR("€", 2), // Euro (minor unit = cents)
    MXN("$", 2), // Mexican Peso
    COP("$", 0); // Colombian Peso

    companion object {
        fun fromString(value: String): CurrencyCode = when (value.trim().uppercase(Locale.ROOT)) {
            "CRC", "COLONES", "COLÓN", "₡" -> CRC
            "USD", "DOLLARS", "DÓLARES", "$" -> USD
            "EUR", "EUROS", "€" -> EUR
            "MXN", "PESOS", "PESOS MEXICANOS" -> MXN
            "COP", "PESOS COLOMBIANOS" -> COP
            else -> throw UnsupportedCurrencyException(value)
        }

        fun fromStringOrNull(value: String?): CurrencyCode? = when (value?.trim()?.uppercase(Locale.ROOT)) {
            "CRC", "COLONES", "COLÓN", "₡" -> CRC
            "USD", "DOLLARS", "DÓLARES", "$" -> USD
            "EUR", "EUROS", "€" -> EUR
            "MXN", "PESOS", "PESOS MEXICANOS" -> MXN
            "COP", "PESOS COLOMBIANOS" -> COP
            else -> null
        }
    }
}
