package com.elysium369.meet.ride.dispatch

import com.elysium369.meet.data.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class DispatchWaveResult(
    @SerialName("wave_id") val waveId: String,
    @SerialName("wave_number") val waveNumber: Int,
    @SerialName("candidates_found") val candidatesFound: Int,
    @SerialName("candidates_eligible") val candidatesEligible: Int,
)

interface RideExposureGateway {
    suspend fun acknowledgeRequestSeen(requestId: String): Result<Boolean>
    suspend fun countSeenDrivers(requestId: String): Result<Int>
    suspend fun publishDispatch(
        requestId: String,
        waveNumber: Int = 1,
        radiusMeters: Int = 3000,
        maxEtaSeconds: Int = 600
    ): Result<DispatchWaveResult>
}

class SupabaseRideExposureGateway @javax.inject.Inject constructor() : RideExposureGateway {
    override suspend fun acknowledgeRequestSeen(requestId: String): Result<Boolean> = runCatching {
        val params = buildJsonObject {
            put("p_request_id", requestId)
        }
        val response = SupabaseManager.client.postgrest.rpc("ride_ack_request_seen_v1", params)
        response.decodeAs<Boolean>()
    }

    override suspend fun countSeenDrivers(requestId: String): Result<Int> = runCatching {
        val params = buildJsonObject {
            put("p_request_id", requestId)
        }
        val response = SupabaseManager.client.postgrest.rpc("ride_count_seen_drivers_v1", params)
        response.decodeAs<Int>()
    }

    override suspend fun publishDispatch(
        requestId: String,
        waveNumber: Int,
        radiusMeters: Int,
        maxEtaSeconds: Int
    ): Result<DispatchWaveResult> = runCatching {
        val params = buildJsonObject {
            put("p_request_id", requestId)
            put("p_wave_number", waveNumber)
            put("p_radius_meters", radiusMeters)
            put("p_max_eta_seconds", maxEtaSeconds)
        }
        val response = SupabaseManager.client.postgrest.rpc("ride_dispatch_publish_v1", params)
        response.decodeAs<DispatchWaveResult>()
    }
}
