package com.elysium369.meet.ride.meter

import com.elysium369.meet.data.supabase.SupabaseManager
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class SharedRideMeterSnapshot(
    val trip_id: String,
    val server_version: Long,
    val server_as_of_ms: Long,
    val started_at_ms: Long? = null,
    val elapsed_seconds: Long? = null,
    val validated_distance_meters: Long = 0,
    val last_capture_ms: Long? = null,
    val rejected_segments: Long = 0,
    val accepted_segments: Long = 0,
    val measured_fare_minor: Long? = null,
    val currency: String,
    val rate_card_version: Long,
    val is_final: Boolean = false,
    val charge_authorized: Boolean = false,
)

object SharedRideMeterGateway {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun fetch(tripId: String): SharedRideMeterSnapshot {
        val owner = SupabaseManager.client.auth.currentUserOrNull()?.id ?: error("AUTH_REQUIRED")
        val result = SupabaseManager.client.postgrest.rpc("ride_shared_meter_snapshot_v1", buildJsonObject { put("p_trip_id", tripId) })
        check(SupabaseManager.client.auth.currentUserOrNull()?.id == owner) { "ACCOUNT_CHANGED" }
        return json.decodeFromString<SharedRideMeterSnapshot>(result.data).also {
            check(it.trip_id == tripId && it.server_version > 0 && it.validated_distance_meters >= 0)
        }
    }
}
