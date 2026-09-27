package com.elysium369.meet.safety.data.remote

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Preserve private provenance and nullable reported counts across the outbox/RPC boundary. */
internal fun safetyCreateReportParameters(reportId: String, idempotencyKey: String, clientDigest: String, payload: JsonObject): JsonObject =
    buildJsonObject {
        put("p_report_id", reportId)
        put("p_idempotency_key", idempotencyKey)
        put("p_client_payload_sha256", clientDigest)
        put("p_category", payload["category"]?.jsonPrimitive?.contentOrNull ?: "OTHER")
        put("p_narrative", payload["narrative"]?.jsonPrimitive?.contentOrNull ?: "")
        put("p_source_relation", payload["sourceRelation"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN")
        put("p_occurred_at", payload["occurredAtIso"] ?: JsonNull)
        put("p_latitude", payload["latitude"] ?: JsonNull)
        put("p_longitude", payload["longitude"] ?: JsonNull)
        put("p_accuracy_meters", payload["accuracyMeters"] ?: JsonNull)
        put("p_location_source", payload["locationSource"]?.jsonPrimitive?.contentOrNull ?: "NONE")
        put("p_reported_victim_count", payload["reportedVictimCount"] ?: JsonNull)
        put("p_reported_victim_female", payload["reportedVictimFemale"] ?: JsonNull)
        put("p_reported_victim_male", payload["reportedVictimMale"] ?: JsonNull)
    }
