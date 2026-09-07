package com.elysium369.meet.provider.domain.models

import java.time.Instant
import java.util.UUID

enum class ProviderNotificationCategory {
    SAFETY_ALERT,
    EARNINGS_SETTLED,
    DOCUMENT_EXPIRING,
    DISPATCH_OFFER,
    PERFORMANCE_TIP,
    SYSTEM;

    companion object {
        fun fromString(value: String): ProviderNotificationCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: SYSTEM
        }
    }
}

data class ProviderNotification(
    val notificationId: UUID,
    val category: ProviderNotificationCategory,
    val title: String,
    val body: String,
    val deepLinkUri: String?,
    val isRead: Boolean,
    val createdAt: Instant,
) {
    init {
        require(title.isNotBlank()) { "Title cannot be blank" }
        require(body.isNotBlank()) { "Body cannot be blank" }
    }
}
