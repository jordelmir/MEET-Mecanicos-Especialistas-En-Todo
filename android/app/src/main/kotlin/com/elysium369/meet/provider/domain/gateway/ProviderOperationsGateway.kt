package com.elysium369.meet.provider.domain.gateway

import com.elysium369.meet.provider.domain.models.ProviderCapability
import com.elysium369.meet.provider.domain.models.ProviderContext
import com.elysium369.meet.provider.domain.models.ProviderDashboard
import com.elysium369.meet.provider.domain.result.ProviderResult
import java.util.UUID

interface ProviderOperationsGateway {
    suspend fun goOnline(
        capability: ProviderCapability,
        telemetrySessionId: UUID,
        appVersion: String,
        deviceBatteryPct: Int?,
        networkClass: String?,
    ): ProviderResult<ProviderContext>

    suspend fun goOffline(
        reason: String? = null,
    ): ProviderResult<ProviderContext>

    suspend fun getConsoleSnapshot(
        capability: ProviderCapability,
    ): ProviderResult<ProviderDashboard>
}
