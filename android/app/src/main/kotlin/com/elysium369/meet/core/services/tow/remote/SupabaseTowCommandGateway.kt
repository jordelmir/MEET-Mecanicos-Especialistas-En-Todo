package com.elysium369.meet.core.services.tow.remote

import com.elysium369.meet.core.services.tow.TowCapabilities
import com.elysium369.meet.core.services.tow.TowState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Authoritative Supabase implementation of TowCommandGateway.
 * Routes tow fulfillment requests, claims, and lifecycle transitions over the internet.
 */
@Singleton
class SupabaseTowCommandGateway @Inject constructor(
    private val supabase: SupabaseClient,
) : TowCommandGateway {

    override suspend fun requestTow(command: RequestTowRemoteCommand): TowServerResult {
        if (supabase.auth.currentUserOrNull() == null) {
            return TowServerResult.Rejected(
                errorCode = "UNAUTHENTICATED",
                message = "Se requiere una sesión de usuario autenticada para solicitar grúa."
            )
        }

        return try {
            val params = buildJsonObject {
                put("p_vehicle_summary", command.vehicleSummary)
                put("p_pickup_lat", command.pickupLat)
                put("p_pickup_lng", command.pickupLng)
                put("p_pickup_address", command.pickupAddress)
                command.pickupAccuracyMeters?.let { put("p_pickup_accuracy_meters", it) }
                command.destLat?.let { put("p_dest_lat", it) }
                command.destLng?.let { put("p_dest_lng", it) }
                command.destAddress?.let { put("p_dest_address", it) }
                put("p_required_capabilities", buildJsonArray {
                    command.requiredCapabilities.forEach { add(JsonPrimitive(it.name)) }
                })
                command.vehicleVin?.let { put("p_vehicle_vin", it) }
                command.notes?.let { put("p_notes", it) }
                command.quotedPriceMinor?.let { put("p_quoted_price_minor", it) }
                put("p_idempotency_key", command.idempotencyKey)
                put("p_request_hash", command.requestHash)
                put("p_correlation_id", command.correlationId)
            }

            val response = supabase.postgrest.rpc("tow_request_job", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val jobId = response["job_id"]?.jsonPrimitive?.content.orEmpty()
                val stateStr = response["state"]?.jsonPrimitive?.content.orEmpty()
                val state = runCatching { TowState.valueOf(stateStr) }.getOrDefault(TowState.REQUESTED)
                val version = response["version"]?.jsonPrimitive?.longOrNull ?: 1L
                TowServerResult.Accepted(
                    jobId = jobId,
                    state = state,
                    version = version,
                    rawResponse = response.toString()
                )
            } else {
                val errCode = response["error_code"]?.jsonPrimitive?.content ?: "REQUEST_REJECTED"
                val msg = response["message"]?.jsonPrimitive?.content ?: "La solicitud fue rechazada por el servidor."
                TowServerResult.Rejected(
                    errorCode = errCode,
                    message = msg
                )
            }
        } catch (t: Throwable) {
            TowServerResult.NetworkFailure(t)
        }
    }

    override suspend fun claimTow(command: ClaimTowRemoteCommand): TowServerResult {
        if (supabase.auth.currentUserOrNull() == null) {
            return TowServerResult.Rejected(
                errorCode = "UNAUTHENTICATED",
                message = "Se requiere una sesión de operador autenticada para tomar el servicio."
            )
        }

        return try {
            val params = buildJsonObject {
                put("p_job_id", command.jobId)
                put("p_tow_unit_id", command.towUnitId.toString())
                put("p_expected_version", command.expectedVersion)
                put("p_idempotency_key", command.idempotencyKey)
                put("p_request_hash", command.requestHash)
            }

            val response = supabase.postgrest.rpc("tow_claim_job", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val jobId = response["job_id"]?.jsonPrimitive?.content ?: command.jobId
                val stateStr = response["state"]?.jsonPrimitive?.content ?: "ASSIGNED"
                val state = runCatching { TowState.valueOf(stateStr) }.getOrDefault(TowState.ASSIGNED)
                val version = response["version"]?.jsonPrimitive?.longOrNull ?: (command.expectedVersion + 1)
                val opIdStr = response["operator_id"]?.jsonPrimitive?.content
                val opId = opIdStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val unitIdStr = response["tow_unit_id"]?.jsonPrimitive?.content
                val unitId = unitIdStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }

                TowServerResult.Accepted(
                    jobId = jobId,
                    state = state,
                    version = version,
                    assignedOperatorId = opId,
                    assignedTowUnitId = unitId,
                    rawResponse = response.toString()
                )
            } else {
                val errCode = response["error_code"]?.jsonPrimitive?.content ?: "CLAIM_FAILED"
                val msg = response["message"]?.jsonPrimitive?.content ?: "Reclamo rechazado."
                if (errCode in setOf("ALREADY_CLAIMED", "CONCURRENCY_CONFLICT")) {
                    val curVer = response["current_version"]?.jsonPrimitive?.longOrNull
                    val curStateStr = response["current_state"]?.jsonPrimitive?.content
                    val curState = curStateStr?.let { runCatching { TowState.valueOf(it) }.getOrNull() }
                    TowServerResult.Conflict(
                        jobId = command.jobId,
                        errorCode = errCode,
                        message = msg,
                        currentVersion = curVer,
                        currentState = curState
                    )
                } else {
                    TowServerResult.Rejected(
                        errorCode = errCode,
                        message = msg
                    )
                }
            }
        } catch (t: Throwable) {
            TowServerResult.NetworkFailure(t)
        }
    }

    override suspend fun transition(command: TransitionTowRemoteCommand): TowServerResult {
        if (supabase.auth.currentUserOrNull() == null) {
            return TowServerResult.Rejected(
                errorCode = "UNAUTHENTICATED",
                message = "Se requiere autenticación para avanzar el estado."
            )
        }

        return try {
            val params = buildJsonObject {
                put("p_job_id", command.jobId)
                put("p_expected_version", command.expectedVersion)
                put("p_command_type", command.commandType)
                put("p_target_state", command.targetState.name)
                put("p_idempotency_key", command.idempotencyKey)
                put("p_request_hash", command.requestHash)
                command.evidenceId?.let { put("p_evidence_id", it.toString()) }
                command.evidenceHash?.let { put("p_evidence_hash", it) }
                command.notes?.let { put("p_notes", it) }
            }

            val response = supabase.postgrest.rpc("tow_execute_transition", params).decodeAs<JsonObject>()
            val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (success) {
                val jobId = response["job_id"]?.jsonPrimitive?.content ?: command.jobId
                val stateStr = response["state"]?.jsonPrimitive?.content ?: command.targetState.name
                val state = runCatching { TowState.valueOf(stateStr) }.getOrDefault(command.targetState)
                val version = response["version"]?.jsonPrimitive?.longOrNull ?: (command.expectedVersion + 1)
                TowServerResult.Accepted(
                    jobId = jobId,
                    state = state,
                    version = version,
                    rawResponse = response.toString()
                )
            } else {
                val errCode = response["error_code"]?.jsonPrimitive?.content ?: "TRANSITION_FAILED"
                val msg = response["message"]?.jsonPrimitive?.content ?: "Transición rechazada."
                if (errCode == "CONCURRENCY_CONFLICT") {
                    val curVer = response["current_version"]?.jsonPrimitive?.longOrNull
                    val curStateStr = response["current_state"]?.jsonPrimitive?.content
                    val curState = curStateStr?.let { runCatching { TowState.valueOf(it) }.getOrNull() }
                    TowServerResult.Conflict(
                        jobId = command.jobId,
                        errorCode = errCode,
                        message = msg,
                        currentVersion = curVer,
                        currentState = curState
                    )
                } else {
                    TowServerResult.Rejected(
                        errorCode = errCode,
                        message = msg
                    )
                }
            }
        } catch (t: Throwable) {
            TowServerResult.NetworkFailure(t)
        }
    }

    override suspend fun discoverJobs(towUnitId: UUID, limit: Int): Result<List<TowDiscoveryItem>> = runCatching {
        val params = buildJsonObject {
            put("p_tow_unit_id", towUnitId.toString())
            put("p_limit", limit)
        }

        val response = supabase.postgrest.rpc("tow_discover_jobs", params).decodeAs<JsonArray>()
        response.mapNotNull { elem ->
            val obj = elem.jsonObject
            val jobId = obj["job_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val dist = obj["approximate_distance_m"]?.jsonPrimitive?.intOrNull
            val urgency = obj["urgency"]?.jsonPrimitive?.content
            val reqCaps = obj["required_capabilities"]?.jsonArray?.mapNotNull { cap ->
                runCatching { TowCapabilities.valueOf(cap.jsonPrimitive.content) }.getOrNull()
            }?.toSet() ?: emptySet()
            val ver = obj["version"]?.jsonPrimitive?.longOrNull ?: 1L

            TowDiscoveryItem(
                jobId = jobId,
                approximateDistanceMeters = dist,
                urgency = urgency,
                requiredCapabilities = reqCaps,
                version = ver
            )
        }
    }
}
