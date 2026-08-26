package com.elysium369.meet.ride.presence

import com.elysium369.meet.data.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface RidePresenceGateway {
    suspend fun setAvailability(availability: RideDriverAvailability, vehicleId: String? = null): Result<Unit>
    suspend fun updateLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        heading: Int? = null,
        speed: Float? = null,
        seq: Long,
        h3R8: String? = null,
        h3R9: String? = null
    ): Result<Long>
}

class SupabaseRidePresenceGateway @javax.inject.Inject constructor() : RidePresenceGateway {
    override suspend fun setAvailability(
        availability: RideDriverAvailability,
        vehicleId: String?
    ): Result<Unit> = runCatching {
        val params = buildJsonObject {
            put("p_availability", availability.name)
            if (vehicleId != null) {
                put("p_vehicle_id", vehicleId)
            }
        }
        SupabaseManager.client.postgrest.rpc("ride_set_driver_availability_v1", params)
    }

    override suspend fun updateLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        heading: Int?,
        speed: Float?,
        seq: Long,
        h3R8: String?,
        h3R9: String?
    ): Result<Long> = runCatching {
        val params = buildJsonObject {
            put("p_latitude", latitude)
            put("p_longitude", longitude)
            put("p_accuracy", accuracy)
            if (heading != null) put("p_heading", heading)
            if (speed != null) put("p_speed", speed)
            put("p_seq", seq)
            if (h3R8 != null) put("p_h3_r8", h3R8)
            if (h3R9 != null) put("p_h3_r9", h3R9)
        }
        val response = SupabaseManager.client.postgrest.rpc("ride_update_driver_location_v1", params)
        response.data.toLong()
    }
}
