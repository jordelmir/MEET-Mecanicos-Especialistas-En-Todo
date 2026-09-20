package com.elysium369.meet.safety.data

import com.elysium369.meet.safety.data.local.SafetyPublicTimelineEntity
import com.elysium369.meet.safety.data.local.SafetyPublicClaimEntity
import kotlinx.serialization.Serializable
import java.time.Instant

/** Wire names mirror only the sanitized publication projections. */
@Serializable
internal data class SafetyPublicTimelineWire(
    val case_id: String,
    val milestone_id: String,
    val event_type: String,
    val public_summary: String,
    val occurred_at: String? = null,
    val recorded_at: String,
    val source_count: Int,
    val evidence_count: Int,
    val server_version: Long,
) {
    fun toEntity(): SafetyPublicTimelineEntity {
        require(server_version > 0)
        return SafetyPublicTimelineEntity(case_id, milestone_id, event_type, public_summary,
            occurred_at?.let { Instant.parse(it).toEpochMilli() }, Instant.parse(recorded_at).toEpochMilli(),
            source_count, evidence_count, server_version)
    }
}

@Serializable
internal data class SafetyPublicClaimWire(
    val case_id: String,
    val claim_id: String,
    val predicate: String,
    val claim_state: String,
    val independent_source_count: Int,
    val evidence_count: Int,
    val civil_source_count: Int,
    val journalistic_source_count: Int,
    val public_record_source_count: Int,
    val documentary_source_count: Int,
    val institutional_source_count: Int,
    val server_version: Long,
) {
    fun toEntity(): SafetyPublicClaimEntity {
        require(server_version > 0)
        return SafetyPublicClaimEntity(case_id, claim_id, predicate, claim_state,
            independent_source_count, evidence_count, civil_source_count, journalistic_source_count,
            public_record_source_count, documentary_source_count, institutional_source_count, server_version)
    }
}
