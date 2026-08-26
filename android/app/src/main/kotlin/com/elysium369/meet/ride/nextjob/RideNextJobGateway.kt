package com.elysium369.meet.ride.nextjob

import com.elysium369.meet.data.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

interface RideNextJobGateway {
    suspend fun reserveNextJob(
        driverId: String,
        currentTripId: String,
        nextTripId: String,
        remainingCurrentEtaSec: Int,
        nextPickupEtaSec: Int,
    ): Result<String>

    suspend fun activateNextJob(driverId: String, reservationId: String): Result<Boolean>

    suspend fun getPrivacyProjection(nextTripId: String): Result<NextJobPrivacyProjection>
}

class SupabaseRideNextJobGateway @Inject constructor() : RideNextJobGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun reserveNextJob(
        driverId: String,
        currentTripId: String,
        nextTripId: String,
        remainingCurrentEtaSec: Int,
        nextPickupEtaSec: Int,
    ): Result<String> = runCatching {
        val params = buildJsonObject {
            put("p_driver_id", driverId)
            put("p_current_trip_id", currentTripId)
            put("p_next_trip_id", nextTripId)
            put("p_remaining_current_eta_sec", remainingCurrentEtaSec)
            put("p_next_pickup_eta_sec", nextPickupEtaSec)
        }
        val response = SupabaseManager.client.postgrest.rpc("ride_reserve_next_job_v1", params).data
        response
    }

    override suspend fun activateNextJob(
        driverId: String,
        reservationId: String,
    ): Result<Boolean> = runCatching {
        val params = buildJsonObject {
            put("p_driver_id", driverId)
            put("p_reservation_id", reservationId)
        }
        SupabaseManager.client.postgrest.rpc("ride_activate_next_job_v1", params)
        true
    }

    override suspend fun getPrivacyProjection(nextTripId: String): Result<NextJobPrivacyProjection> = runCatching {
        val params = buildJsonObject {
            put("p_next_trip_id", nextTripId)
        }
        val response = SupabaseManager.client.postgrest.rpc("ride_get_next_job_privacy_projection_v1", params).data
        json.decodeFromString<NextJobPrivacyProjection>(response)
    }
}
