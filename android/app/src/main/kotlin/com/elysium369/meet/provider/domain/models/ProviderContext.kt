package com.elysium369.meet.provider.domain.models

import java.time.Instant
import java.util.UUID

data class ProviderContext(
    val providerId: UUID,
    val capability: ProviderCapability,
    val operationalState: ProviderOperationalState,
    val currentWorkId: UUID?,
    val lastStatusChangeAt: Instant,
    val deviceBatteryPct: Int?,
    val networkClass: String?,
    val appVersion: String,
    val telemetrySessionId: UUID,
) {
    init {
        if (deviceBatteryPct != null) {
            require(deviceBatteryPct in 0..100) { "Battery percentage must be in 0..100, got $deviceBatteryPct" }
        }
        require(appVersion.isNotBlank()) { "App version cannot be blank" }
    }
}
