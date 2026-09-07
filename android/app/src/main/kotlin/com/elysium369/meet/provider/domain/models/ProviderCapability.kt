package com.elysium369.meet.provider.domain.models

enum class ProviderCapability {
    RIDE_DRIVER,
    TOW_OPERATOR,
    MECHANIC,
    PARTS_SELLER,
    INSPECTOR,
    DELIVERY_COURIER;

    companion object {
        fun fromString(value: String): ProviderCapability {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown ProviderCapability: $value")
        }
    }
}
