package com.elysium369.meet.ride.data.remote

import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class TrustedRideDriver(
    @SerialName("driver_id") val driverId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("completed_trips") val completedTrips: Int,
    @SerialName("last_completed_at") val lastCompletedAt: String? = null,
    @SerialName("vehicle_name") val vehicleName: String? = null,
    @SerialName("is_available") val isAvailable: Boolean = false,
)

@Serializable
data class RideDriverRequestDecision(
    @SerialName("trip_id") val tripId: String,
    val action: String,
)

@Serializable
data class RideTrustedDriverInvite(
    @SerialName("trip_id") val tripId: String,
    val state: String,
)

@Serializable
data class RideDriverPerformance(
    @SerialName("offers_submitted") val offersSubmitted: Int = 0,
    @SerialName("offers_accepted") val offersAccepted: Int = 0,
    @SerialName("acceptance_rate_percent") val acceptanceRatePercent: Double? = null,
    @SerialName("trips_completed") val tripsCompleted: Int = 0,
    @SerialName("accepted_then_cancelled") val acceptedThenCancelled: Int = 0,
    @SerialName("completion_rate_percent") val completionRatePercent: Double? = null,
)

@Serializable
private data class RideRequestRejectionSummary(
    @SerialName("driver_rejection_count") val driverRejectionCount: Int = 0,
)

object RideDispatchGateway {
    suspend fun expireStaleRequests() {
        SupabaseModule.client.postgrest.rpc("ride_expire_stale_requests_v1")
    }

    suspend fun decideRequest(
        tripId: String,
        action: String,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) {
        require(action in setOf("DISMISS", "REJECT"))
        SupabaseModule.client.postgrest.rpc(
            "ride_driver_decide_request_v1",
            buildJsonObject {
                put("p_trip_id", tripId)
                put("p_action", action)
                put("p_idempotency_key", idempotencyKey)
            },
        )
    }

    suspend fun decisions(): List<RideDriverRequestDecision> =
        SupabaseModule.client.postgrest["ride_driver_request_decisions"]
            .select { order("updated_at", Order.DESCENDING) }
            .decodeList()

    suspend fun trustedInvites(): List<RideTrustedDriverInvite> =
        SupabaseModule.client.postgrest["ride_trusted_driver_invites"]
            .select { order("updated_at", Order.DESCENDING) }
            .decodeList()

    suspend fun performance(): RideDriverPerformance? =
        SupabaseModule.client.postgrest["ride_driver_performance_v1"]
            .select()
            .decodeList<RideDriverPerformance>()
            .firstOrNull()

    suspend fun rejectionCount(tripId: String): Int =
        SupabaseModule.client.postgrest["ride_requests"]
            .select {
                filter { eq("id", tripId) }
                limit(1)
            }
            .decodeList<RideRequestRejectionSummary>()
            .firstOrNull()
            ?.driverRejectionCount
            ?: 0

    suspend fun trustedDrivers(): List<TrustedRideDriver> =
        SupabaseModule.client.postgrest.rpc("ride_list_trusted_drivers_v1").decodeList()

    suspend fun inviteTrustedDriver(tripId: String, driverId: String) {
        SupabaseModule.client.postgrest.rpc(
            "ride_invite_trusted_driver_v1",
            buildJsonObject {
                put("p_trip_id", tripId)
                put("p_driver_id", driverId)
                put("p_idempotency_key", UUID.randomUUID().toString())
            },
        )
    }
}
