package com.elysium369.meet.provider.domain.models

enum class ProviderOperationalState {
    OFFLINE,
    ONLINE_STANDBY,
    BUSY_DISPATCH,
    BUSY_ENGAGED,
    SUSPENDED_SAFETY,
    RESTRICTED_DOCUMENTS;

    val isAvailableForWork: Boolean
        get() = this == ONLINE_STANDBY

    val isActivelyEngaged: Boolean
        get() = this == BUSY_DISPATCH || this == BUSY_ENGAGED

    val isBlocked: Boolean
        get() = this == SUSPENDED_SAFETY || this == RESTRICTED_DOCUMENTS

    companion object {
        fun fromString(value: String): ProviderOperationalState {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown ProviderOperationalState: $value")
        }
    }
}
