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
    val public_point_count: Long = 0,
    val independent_source_count: Long = 0,
    val civil_source_count: Long = 0,
    val journalistic_source_count: Long = 0,
    val public_record_source_count: Long = 0,
    val documentary_source_count: Long = 0,
    val institutional_source_count: Long = 0,
    // V2 — Category breakdown
    val homicide_count: Long = 0,
    val violence_count: Long = 0,
    val drugs_count: Long = 0,
    val threat_count: Long = 0,
    val missing_count: Long = 0,
    val institutional_count: Long = 0,
    // V2 — Victim demographics (aggregates only, never PII)
    val total_victims_documented: Long = 0,
    val female_victims: Long = 0,
    val male_victims: Long = 0,
    val unknown_sex_victims: Long = 0,
    // V2 — Resolution time analytics (days, -1 = no data)
    val avg_resolution_days_all: Double = -1.0,
    val avg_resolution_days_female_victim: Double = -1.0,
    val avg_resolution_days_male_victim: Double = -1.0,
) {
    /** Category breakdown as label→count pairs for chart rendering. */
    fun categoryBreakdown(): List<Pair<String, Long>> = listOf(
        "Homicidio" to homicide_count,
        "Violencia" to violence_count,
        "Drogas" to drugs_count,
        "Amenazas" to threat_count,
        "Desaparecidos" to missing_count,
        "Institucional" to institutional_count,
    ).filter { it.second > 0 }

    /** Source breakdown as label→count pairs for chart rendering. */
    fun sourceBreakdown(): List<Pair<String, Long>> = listOf(
        "Civil" to civil_source_count,
        "Periodístico" to journalistic_source_count,
        "Registro público" to public_record_source_count,
        "Documental" to documentary_source_count,
        "Institucional" to institutional_source_count,
    ).filter { it.second > 0 }

    /** Victim breakdown by sex for bar chart. */
    fun victimsByGender(): List<Pair<String, Long>> = listOf(
        "Mujeres" to female_victims,
        "Hombres" to male_victims,
        "Sin dato" to unknown_sex_victims,
    ).filter { it.second > 0 }

    val hasResolutionData: Boolean get() = avg_resolution_days_all >= 0
}

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

    /** V1 — legacy basic metrics. */
    suspend fun observatory(filters: SafetyObservatoryFilters): SafetyObservatoryMetrics {
        val params = filters.parameters()
        gates.requireEnabled("safety_observatory")
        return client.postgrest.rpc("safety_observatory_query_v1", params).decodeAs<SafetyObservatoryMetrics>()
    }

    /** V2 — demographics + resolution times + category breakdown. */
    suspend fun observatoryV2(filters: SafetyObservatoryFilters): SafetyObservatoryMetrics {
        val params = filters.parameters()
        gates.requireEnabled("safety_observatory")
        return try {
            client.postgrest.rpc("safety_observatory_query_v2", params).decodeAs<SafetyObservatoryMetrics>()
        } catch (_: Exception) {
            // Graceful fallback to V1 if V2 RPC not yet deployed
            observatory(filters)
        }
    }
}
