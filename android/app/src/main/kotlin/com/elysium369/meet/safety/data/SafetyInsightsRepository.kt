package com.elysium369.meet.safety.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PublicAccountabilityEvent(
    val event_id: String,
    val case_id: String,
    val case_title: String? = null,
    val institution_ref: String,
    val event_type: String,
    val occurred_at: String,
    val server_version: Long,
)

@Serializable
data class SafetyObservatoryMetrics(
    val public_point_count: Long,
    val independent_source_count: Long,
    val civil_source_count: Long,
    val journalistic_source_count: Long,
    val public_record_source_count: Long,
    val documentary_source_count: Long,
    val institutional_source_count: Long,
)

data class SafetyObservatoryFilters(
    val from: String = "", val to: String = "", val category: String = "",
    val country: String = "", val admin1: String = "", val admin2: String = "",
) {
    fun parameters() = buildJsonObject {
        val start = from.trim().takeIf { it.isNotEmpty() }?.let { Instant.parse(it) }
        val end = to.trim().takeIf { it.isNotEmpty() }?.let { Instant.parse(it) }
        require(start == null || end == null || start < end) { "El inicio debe preceder al fin." }
        start?.let { put("p_from", it.toString()) }
        end?.let { put("p_to", it.toString()) }
        listOf("p_category" to category, "p_country_code" to country,
            "p_admin1_code" to admin1, "p_admin2_code" to admin2).forEach { (key, value) ->
            value.trim().takeIf { it.isNotEmpty() }?.let { put(key, it) }
        }
    }
}

@Singleton
class SafetyInsightsRepository @Inject constructor(
    private val client: SupabaseClient,
    private val gates: SafetyRuntimeFeatureGates,
) {
    suspend fun accountability(): List<PublicAccountabilityEvent> {
        gates.requireEnabled("safety_accountability")
        return client.postgrest["safety_public_accountability_projection"].select {
            order("occurred_at", Order.DESCENDING)
        }.decodeList<PublicAccountabilityEvent>().filter { it.server_version > 0 }
    }
    suspend fun observatory(filters: SafetyObservatoryFilters): SafetyObservatoryMetrics {
        val params = filters.parameters()
        gates.requireEnabled("safety_observatory")
        return client.postgrest.rpc("safety_observatory_query_v1", params).decodeAs<SafetyObservatoryMetrics>()
    }
}
