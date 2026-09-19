package com.elysium369.meet.safety.data.remote

import com.elysium369.meet.safety.data.local.SafetyCommandOutboxEntity
import com.elysium369.meet.safety.domain.SafetyGatewayResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSafetyCommandGateway @Inject constructor() : SafetyCommandGateway {

    override suspend fun execute(
        command: SafetyCommandOutboxEntity,
        payloadJson: String,
    ): SafetyGatewayResult {
        return try {
            // TODO: Implement actual Supabase RPC call
            // For now, return transport failure to prevent data loss
            // Commands will stay in RETRYABLE until the real gateway is wired
            SafetyGatewayResult.TransportFailure(
                code = "GATEWAY_NOT_IMPLEMENTED",
                message = "SafetyCommandGateway RPC not yet wired",
            )
        } catch (e: Exception) {
            SafetyGatewayResult.TransportFailure(
                code = "GATEWAY_EXCEPTION",
                message = e.message?.take(200) ?: "Unknown gateway error",
            )
        }
    }
}
