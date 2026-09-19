package com.elysium369.meet.safety.data.remote

import com.elysium369.meet.safety.data.local.SafetyCommandOutboxEntity
import com.elysium369.meet.safety.domain.SafetyGatewayResult

interface SafetyCommandGateway {
    suspend fun execute(
        command: SafetyCommandOutboxEntity,
        payloadJson: String,
    ): SafetyGatewayResult
}
