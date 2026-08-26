package com.elysium369.meet.ride.payment

import com.elysium369.meet.data.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

interface RidePaymentGateway {
    suspend fun attestPaymentEvent(
        tripId: String,
        newStatus: RidePaymentStatus,
        referenceNumber: String? = null,
    ): Result<String>
}

class SupabaseRidePaymentGateway @Inject constructor() : RidePaymentGateway {
    override suspend fun attestPaymentEvent(
        tripId: String,
        newStatus: RidePaymentStatus,
        referenceNumber: String?,
    ): Result<String> = runCatching {
        val params = buildJsonObject {
            put("p_trip_id", tripId)
            put("p_new_status", newStatus.name)
            referenceNumber?.let { put("p_reference_number", it) }
        }
        SupabaseManager.client.postgrest.rpc("ride_attest_payment_event_v1", params).data
    }
}
