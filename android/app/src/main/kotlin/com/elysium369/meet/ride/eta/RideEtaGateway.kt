package com.elysium369.meet.ride.eta

import com.elysium369.meet.data.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

interface RideEtaGateway {
    suspend fun recordEtaObservation(
        requestId: String,
        driverId: String,
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        estimate: RideEtaEstimate,
    ): Result<Unit>

    suspend fun getLatestTripEta(requestId: String): Result<RideEtaEstimate?>
}

class SupabaseRideEtaGateway @Inject constructor() : RideEtaGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun recordEtaObservation(
        requestId: String,
        driverId: String,
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        estimate: RideEtaEstimate,
    ): Result<Unit> = runCatching {
        val params = buildJsonObject {
            put("p_request_id", requestId)
            put("p_driver_id", driverId)
            put("p_origin_lat", originLat)
            put("p_origin_lon", originLon)
            put("p_dest_lat", destLat)
            put("p_dest_lon", destLon)
            put("p_eta_seconds", estimate.etaSeconds)
            put("p_distance_meters", estimate.distanceMeters)
            put("p_provider", estimate.sourceRaw)
            put("p_confidence", estimate.confidence)
            put("p_traffic_condition", estimate.trafficCondition)
        }
        SupabaseManager.client.postgrest.rpc("ride_record_eta_observation_v1", params)
    }

    override suspend fun getLatestTripEta(requestId: String): Result<RideEtaEstimate?> = runCatching {
        val params = buildJsonObject {
            put("p_request_id", requestId)
        }
        val response = SupabaseManager.client.postgrest.rpc("ride_get_latest_trip_eta_v1", params).data
        val isFound = response.contains("\"found\":true")
        if (isFound) {
            json.decodeFromString<RideEtaEstimate>(response)
        } else {
            null
        }
    }
}
