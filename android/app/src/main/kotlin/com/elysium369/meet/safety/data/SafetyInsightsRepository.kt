package com.elysium369.meet.safety.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.json.JsonNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

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
    val privacy_suppressed: Boolean = false,
    val sensitive_metrics_available: Boolean = true,
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
        "🛡️ Civil" to civil_source_count,
        "📰 Periodístico" to journalistic_source_count,
        "🏛️ Reg. Público" to public_record_source_count,
        "📄 Documental" to documentary_source_count,
        "🏢 Institucional" to institutional_source_count,
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
        return try {
            client.postgrest["safety_public_accountability_projection"].select {
                order("occurred_at", Order.DESCENDING)
            }.decodeList<PublicAccountabilityEvent>().filter { it.server_version > 0 }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Public V3 cells only. Retired V1/V2 authority is never used as fallback. */
    suspend fun observatory(filters: SafetyObservatoryFilters): SafetyObservatoryMetrics {
        gates.requireEnabled("safety_observatory")
        return try {
            val projection = client.postgrest.rpc("safety_observatory_query_v3", filters.v3Parameters()).decodeAs<SafetyObservatoryProjectionV3>()
            require(projection.policy_version == "SAFETY-OBSERVATORY-V3")
            projection.toMetrics()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SafetyObservatoryMetrics(
                public_point_count = 0,
                privacy_suppressed = true,
                sensitive_metrics_available = false,
                homicide_count = 0,
                violence_count = 0,
                drugs_count = 0,
                threat_count = 0,
                missing_count = 0,
                institutional_count = 0,
            )
        }
    }

    /** Kept for call-site compatibility while all results originate from V3. */
    suspend fun observatoryV2(filters: SafetyObservatoryFilters): SafetyObservatoryMetrics = observatory(filters)

    suspend fun counternarcotics(filters: SafetyObservatoryFilters): IllicitMarketPatternsProjection {
        gates.requireEnabled("safety_observatory")
        val range = filters.v3Parameters()
        return client.postgrest.rpc("safety_counternarcotics_patterns_v1", buildJsonObject {
            put("p_from", range.getValue("p_from"))
            put("p_until", range.getValue("p_until"))
            put("p_country_code", range.getValue("p_country_code"))
            put("p_admin1_code", range.getValue("p_admin1_code"))
            put("p_admin2_code", range.getValue("p_admin2_code"))
        }).decodeAs<IllicitMarketPatternsProjection>()
    }
}

@Serializable
data class SafetyObservatoryCellV3(
    val public_cell_id: String, val category: String, val period_start: String,
    val documented_claim_count: Long,
)

@Serializable
data class SafetyObservatoryProjectionV3(
    val policy_version: String, val cells: List<SafetyObservatoryCellV3>,
    val suppression: String, val missing_records_imply_inaction: Boolean = false,
) {
    fun toMetrics(): SafetyObservatoryMetrics {
        fun count(category: String) = cells.filter { it.category == category }.sumOf { it.documented_claim_count }
        return SafetyObservatoryMetrics(
            public_point_count = cells.sumOf { it.documented_claim_count },
            privacy_suppressed = true, sensitive_metrics_available = false,
            homicide_count = count("HOMICIDE"), violence_count = count("VIOLENT_INCIDENT"),
            drugs_count = count("DRUG_SALE_ACTIVITY"), threat_count = count("THREAT"),
            missing_count = count("MISSING_PERSON"), institutional_count = count("INSTITUTIONAL_CONDUCT"),
        )
    }
}

/** UTC whole weeks provide stable query buckets; no fine time slicing is requested. */
fun SafetyObservatoryFilters.v3Parameters(now: Instant = Instant.now()) = buildJsonObject {
    fun weekFloor(value: Instant): Instant = value.atZone(ZoneOffset.UTC)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
    val safeEnd = weekFloor(now.minusSeconds(7 * 86400L))
    val requestedEnd = to.trim().takeIf { it.isNotEmpty() }?.let(Instant::parse)
    val end = minOf(requestedEnd?.let(::weekFloor) ?: safeEnd, safeEnd)
    val requestedStart = from.trim().takeIf { it.isNotEmpty() }?.let(Instant::parse)
    val start = requestedStart?.let { val floor = weekFloor(it); if (floor == it) floor else floor.plusSeconds(7 * 86400L) }
        ?: end.minusSeconds(26 * 7 * 86400L)
    require(start < end && java.time.Duration.between(start, end).toDays() <= 366) { "Selecciona semanas completas anteriores al retraso de privacidad." }
    put("p_from", start.toString()); put("p_until", end.toString())
    listOf("p_category" to category, "p_country_code" to country, "p_admin1_code" to admin1, "p_admin2_code" to admin2).forEach { (key, value) ->
        put(key, value.trim().takeIf { it.isNotEmpty() }?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: JsonNull)
    }
}

@Serializable
data class IllicitMarketPattern(
    val public_cell_id: String, val period_start: String, val documented_claim_count: Long,
    val independent_source_clusters: Long, val active_weeks: Long,
    val journalistic_sources: Long, val public_record_sources: Long, val institutional_sources: Long,
    val institutional_response_events: Long = 0,
    val truth_state: com.elysium369.meet.safety.analytics.counternarcotics.PatternTruthState, val policy_version: String,
)

@Serializable
data class IllicitMarketPatternsProjection(
    val patterns: List<IllicitMarketPattern>, val interpretation: String,
    val missing_records_imply_inaction: Boolean = false,
)
