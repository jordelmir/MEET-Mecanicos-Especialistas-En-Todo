package com.elysium369.meet.ride.demand

import com.elysium369.meet.data.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

interface RideDemandGateway {
    suspend fun getDemandSnapshot(h3R8: String): Result<RideDemandSnapshot>
}

class SupabaseRideDemandGateway @Inject constructor() : RideDemandGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getDemandSnapshot(h3R8: String): Result<RideDemandSnapshot> = runCatching {
        val params = buildJsonObject { put("p_h3_r8", h3R8) }
        val response = SupabaseManager.client.postgrest.rpc("ride_get_demand_snapshot_v1", params).data
        json.decodeFromString<RideDemandSnapshot>(response)
    }
}
