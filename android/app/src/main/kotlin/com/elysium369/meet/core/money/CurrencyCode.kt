package com.elysium369.meet.core.money

/**
 * Supported currency codes for MEET Commerce, Ledger, and TCO.
 */
enum class CurrencyCode(val symbol: String, val decimalPlaces: Int) {
    CRC("₡", 0), // Costa Rican Colón (no decimals in minor units)
    USD("$", 2), // US Dollar (minor unit = cents)
    EUR("€", 2), // Euro (minor unit = cents)
    MXN("$", 2), // Mexican Peso
    COP("$", 0)  // Colombian Peso
}
