package com.elysium369.meet.provider.domain.models

import java.time.Instant

data class ProviderDashboard(
    val context: ProviderContext,
    val performance: ProviderPerformance,
    val balance: ProviderBalance,
    val preferences: ProviderPreferences,
    val recentNotifications: List<ProviderNotification>,
    val snapshotGeneratedAt: Instant,
)
