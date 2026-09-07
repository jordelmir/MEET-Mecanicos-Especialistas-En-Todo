package com.elysium369.meet.platform.marketos

import com.elysium369.meet.core.money.CurrencyCode

/**
 * Strongly typed MarketCode value object.
 * Prevents ad-hoc country string comparisons and country-specific spaghetti.
 */
@JvmInline
value class MarketCode(val value: String) {
    init {
        require(value.isNotBlank() && value.matches(Regex("^[A-Z0-9_]{2,10}$"))) {
            "Invalid MarketCode: $value. Must be alphanumeric uppercase (e.g. CR, CR_SJO, MX, US)."
        }
    }
}

enum class DistanceUnit {
    KILOMETERS,
    MILES
}

/**
 * Authoritative market configuration.
 * Drives market localization, units, allowed verticals, and payment rails as pure configuration.
 */
data class MarketConfig(
    val code: MarketCode,
    val currency: CurrencyCode,
    val defaultLocale: String,
    val timezone: String,
    val distanceUnit: DistanceUnit,
    val enabledVerticals: Set<MarketVertical>,
    val electronicPaymentsEnabled: Boolean,
    val paymentProviders: Set<String>,
) {
    init {
        require(defaultLocale.isNotBlank()) { "defaultLocale cannot be blank" }
        require(timezone.isNotBlank()) { "timezone cannot be blank" }
        if (electronicPaymentsEnabled) {
            require(paymentProviders.isNotEmpty()) {
                "Market $code has electronic payments enabled but zero payment providers configured"
            }
        }
    }
}
