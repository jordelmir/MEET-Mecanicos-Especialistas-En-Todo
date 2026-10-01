package com.elysium369.meet.ui.screens.provider

import com.elysium369.meet.provider.domain.models.ProviderDomainCategory

/** Existing provider identity stays intact; unknown categories require a human choice. */
internal object ProviderCatalogEditPolicy {
    fun category(storedId: String?, providerType: String): ProviderDomainCategory? {
        if (!storedId.isNullOrBlank()) {
            return ProviderDomainCategory.entries.firstOrNull { it.id.equals(storedId, ignoreCase = true) }
        }
        return when (providerType.trim().lowercase()) {
            "mechanic", "workshop" -> ProviderDomainCategory.AUTOMOTIVE_MECHANIC
            "tow_provider", "tow_truck", "tow", "tow_driver" -> ProviderDomainCategory.TOW_TRUCK
            "auto_locksmith", "locksmith", "keys" -> ProviderDomainCategory.LOCKSMITH_SECURITY
            else -> null
        }
    }

    data class Rates(val hourly: Long, val base: Long, val perKm: Long, val warrantyDays: Int, val warrantyKm: Int)

    fun rates(hourly: String, base: String, perKm: String, warrantyDays: String, warrantyKm: String): Rates? {
        val values = Rates(
            hourly.trim().toLongOrNull() ?: return null,
            base.trim().toLongOrNull() ?: return null,
            perKm.trim().toLongOrNull() ?: return null,
            warrantyDays.trim().toIntOrNull() ?: return null,
            warrantyKm.trim().toIntOrNull() ?: return null,
        )
        return values.takeIf { it.hourly >= 0 && it.base >= 0 && it.perKm >= 0 && it.warrantyDays >= 0 && it.warrantyKm >= 0 }
    }
}
