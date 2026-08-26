package com.elysium369.meet.ride.reputation

import com.elysium369.meet.data.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

interface RideReputationGateway {
    suspend fun getDriverPublicProfile(driverId: String): Result<DriverPublicProfile?>
    suspend fun recordTripFeedback(
        tripId: String,
        rating: Int,
        compliments: List<RideCompliment> = emptyList(),
    ): Result<Boolean>
}

class SupabaseRideReputationGateway @Inject constructor() : RideReputationGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getDriverPublicProfile(driverId: String): Result<DriverPublicProfile?> = runCatching {
        val params = buildJsonObject {
            put("p_driver_id", driverId)
        }
        val response = SupabaseManager.client.postgrest.rpc("ride_get_driver_public_profile_v1", params).data
        val element = json.parseToJsonElement(response)
        val isFound = element.toString().contains("\"found\":true")
        if (isFound) {
            json.decodeFromString<DriverPublicProfile>(response)
        } else {
            null
        }
    }

    override suspend fun recordTripFeedback(
        tripId: String,
        rating: Int,
        compliments: List<RideCompliment>,
    ): Result<Boolean> = runCatching {
        val params = buildJsonObject {
            put("p_trip_id", tripId)
            put("p_rating", rating)
            put("p_compliments", buildJsonArray {
                compliments.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.code)) }
            })
        }
        SupabaseManager.client.postgrest.rpc("ride_record_trip_feedback_v1", params)
        true
    }
}
