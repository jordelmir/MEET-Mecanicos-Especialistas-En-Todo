package com.elysium369.meet.ride.safety

import com.elysium369.meet.data.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

interface RideSafetyGateway {
    suspend fun emitSafetySignal(
        tripId: String,
        signalType: SafetySignalType,
        severity: SafetySignalSeverity,
        details: String,
    ): Result<String>
}

class SupabaseRideSafetyGateway @Inject constructor() : RideSafetyGateway {
    override suspend fun emitSafetySignal(
        tripId: String,
        signalType: SafetySignalType,
        severity: SafetySignalSeverity,
        details: String,
    ): Result<String> = runCatching {
        val detailsJson = buildJsonObject { put("description", details) }
        val params = buildJsonObject {
            put("p_trip_id", tripId)
            put("p_signal_type", signalType.name)
            put("p_severity", severity.name)
            put("p_details", detailsJson.toString())
        }
        SupabaseManager.client.postgrest.rpc("ride_emit_safety_signal_v1", params).data
    }
}
