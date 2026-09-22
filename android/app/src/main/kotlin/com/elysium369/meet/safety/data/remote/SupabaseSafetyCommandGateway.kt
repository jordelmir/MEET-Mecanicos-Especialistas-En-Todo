package com.elysium369.meet.safety.data.remote

import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.safety.data.local.SafetyCommandOutboxEntity
import com.elysium369.meet.safety.domain.SafetyGatewayResult
import com.elysium369.meet.safety.domain.SafetyCommandType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSafetyCommandGateway @Inject constructor() : SafetyCommandGateway {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun execute(
        command: SafetyCommandOutboxEntity,
        payloadJson: String,
    ): SafetyGatewayResult {
        val client = SupabaseModule.client

        if (client.auth.currentUserOrNull()?.id != command.actorSessionUserId) {
            return SafetyGatewayResult.Rejected(
                code = "UNAUTHENTICATED",
                message = "Autenticación requerida",
                correlationId = null,
                retryable = false,
            )
        }

        val payload = try {
            json.decodeFromString<JsonObject>(payloadJson)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return SafetyGatewayResult.Rejected(
                code = "INVALID_LOCAL_PAYLOAD",
                message = error.message?.take(200),
                correlationId = null,
                retryable = false,
            )
        }

        return when (command.commandType) {
            SafetyCommandType.CREATE_REPORT.name -> executeCreateReport(command, payload)
            SafetyCommandType.SUBMIT_COUNTERCLAIM.name -> executeCounterclaim(command, payload)
            else -> SafetyGatewayResult.Rejected(
                code = "UNSUPPORTED_COMMAND",
                message = "Comando no soportado",
                correlationId = null,
                retryable = false,
            )
        }
    }

    private suspend fun executeCreateReport(
        command: SafetyCommandOutboxEntity,
        payload: JsonObject,
    ): SafetyGatewayResult = try {
            val response = SupabaseModule.client.postgrest
                .rpc(
                    "safety_create_report_v2",
                    buildJsonObject {
                        put("p_report_id", command.aggregateId)
                        put("p_idempotency_key", command.idempotencyKey)
                        put("p_category", payload["category"]?.jsonPrimitive?.contentOrNull ?: "OTHER")
                        put("p_narrative", payload["narrative"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("p_source_relation", payload["sourceRelation"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN")
                        put("p_occurred_at", payload["occurredAtIso"] ?: JsonNull)
                        put("p_latitude", payload["latitude"] ?: JsonNull)
                        put("p_longitude", payload["longitude"] ?: JsonNull)
                        put("p_accuracy_meters", payload["accuracyMeters"] ?: JsonNull)
                        put("p_client_payload_sha256", command.clientPayloadSha256)
                    },
                )
                .decodeAs<JsonObject>()

            // RPC returns: { report_id, state, server_version, server_payload_sha256, correlation_id }
            val reportId = response["report_id"]?.jsonPrimitive?.contentOrNull
            val state = response["state"]?.jsonPrimitive?.contentOrNull
            val serverVersion = response["server_version"]?.jsonPrimitive?.longOrNull
            val correlationId = response["correlation_id"]?.jsonPrimitive?.contentOrNull

            if (reportId != command.aggregateId || state == null || serverVersion == null || serverVersion <= 0) {
                // Error case: check for error object
                val error = response["error"]?.jsonObject
                return SafetyGatewayResult.Rejected(
                    code = error?.get("code")?.jsonPrimitive?.contentOrNull ?: "COMMAND_REJECTED",
                    message = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "Respuesta inválida del servidor",
                    correlationId = correlationId,
                    retryable = error?.get("retryable")?.jsonPrimitive?.booleanOrNull ?: false,
                )
            }

            SafetyGatewayResult.Accepted(
                state = state,
                serverVersion = serverVersion,
                correlationId = correlationId,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SafetyGatewayResult.TransportFailure(
                code = "REMOTE_TRANSPORT_FAILURE",
                message = error.message?.take(200) ?: "Transport error",
            )
        }

    private suspend fun executeCounterclaim(
        command: SafetyCommandOutboxEntity,
        payload: JsonObject,
    ): SafetyGatewayResult = try {
        val response = SupabaseModule.client.postgrest.rpc(
            "safety_submit_counterclaim_v1",
            buildJsonObject {
                put("p_idempotency_key", command.idempotencyKey)
                put("p_case_id", command.aggregateId)
                put("p_claim_id", payload["claimId"] ?: JsonNull)
                put("p_kind", payload["kind"]?.jsonPrimitive?.contentOrNull ?: "REPORT_ERROR")
                put("p_narrative", payload["narrative"]?.jsonPrimitive?.contentOrNull ?: "")
                put("p_source_url", payload["sourceUrl"] ?: JsonNull)
                put("p_client_payload_sha256", command.clientPayloadSha256)
            },
        ).decodeAs<JsonObject>()
        val receiptId = response["counterclaim_id"]?.jsonPrimitive?.contentOrNull
        val state = response["state"]?.jsonPrimitive?.contentOrNull
        val version = response["server_version"]?.jsonPrimitive?.longOrNull
        if (receiptId == null || state == null || version == null || version <= 0) {
            SafetyGatewayResult.Rejected("INVALID_COUNTERCLAIM_RECEIPT", "Respuesta inválida del servidor", null, false)
        } else {
            SafetyGatewayResult.Accepted(state, version, response["correlation_id"]?.jsonPrimitive?.contentOrNull)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        SafetyGatewayResult.TransportFailure("REMOTE_TRANSPORT_FAILURE", error.message?.take(200))
    }
}
