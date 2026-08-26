package com.elysium369.meet.ride.automatch

import com.elysium369.meet.data.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@Serializable
data class AutoMatchResult(
    @SerialName("matched") val matched: Boolean,
    @SerialName("reason") val reason: String? = null,
    @SerialName("assigned_driver_id") val assignedDriverId: String? = null,
    @SerialName("assigned_vehicle_id") val assignedVehicleId: String? = null,
    @SerialName("agreed_fare_minor") val agreedFareMinor: Long? = null,
    @SerialName("strategy_applied") val strategyApplied: String? = null,
    @SerialName("new_version") val newVersion: Long? = null,
)

interface RideAutoMatchGateway {
    suspend fun configurePolicy(policy: RideAutoMatchPolicy): Result<Boolean>
    suspend fun tryAutoMatch(requestId: String, expectedVersion: Long): Result<AutoMatchResult>
}

class SupabaseRideAutoMatchGateway @Inject constructor() : RideAutoMatchGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun configurePolicy(policy: RideAutoMatchPolicy): Result<Boolean> = runCatching {
        val params = buildJsonObject {
            put("p_request_id", policy.requestId)
            put("p_strategy", policy.strategyRaw)
            put("p_max_fare_minor", policy.maxFareMinor)
            put("p_minimum_trust_tier", policy.minimumTrustTierRaw)
            put("p_maximum_eta_seconds", policy.maximumEtaSeconds)
            put("p_allow_finishing_previous_trip", policy.allowFinishingPreviousTrip)
            put("p_enabled", policy.enabled)
        }
        SupabaseManager.client.postgrest.rpc("ride_configure_auto_match_v1", params)
        true
    }

    override suspend fun tryAutoMatch(
        requestId: String,
        expectedVersion: Long,
    ): Result<AutoMatchResult> = runCatching {
        val params = buildJsonObject {
            put("p_request_id", requestId)
            put("p_expected_version", expectedVersion)
        }
        val response = SupabaseManager.client.postgrest.rpc("ride_try_auto_match_v1", params).data
        json.decodeFromString<AutoMatchResult>(response)
    }
}
