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

        if (client.auth.currentUserOrNull() == null) {
            return SafetyGatewayResult.Rejected(
                code = "UNAUTHENTICATED",
                message = "Autenticación requerida",
                correlationId = null,
                retryable = false,
            )
        }

        if (command.commandType != SafetyCommandType.CREATE_REPORT.name) {
            return SafetyGatewayResult.Rejected(
                code = "UNSUPPORTED_COMMAND",
                message = "Comando no soportado",
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

        return try {
            val response = client.postgrest
                .rpc(
                    "safety_create_report_v2",
                    buildJsonObject {
                        put("p_report_id", command.aggregateId)
                        put("p_idempotency_key", command.idempotencyKey)
                        put("p_category", payload["category"]?.jsonPrimitive?.contentOrNull ?: "OTHER")
                        put("p_narrative", payload["narrative"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("p_source_relation", payload["sourceRelation"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN")
                        payload["occurredAtIso"]?.jsonPrimitive?.contentOrNull?.let {
                            put("p_occurred_at", it)
                        }
                        payload["latitude"]?.jsonPrimitive?.contentOrNull?.let {
                            put("p_latitude", it.toDoubleOrNull())
                        }
                        payload["longitude"]?.jsonPrimitive?.contentOrNull?.let {
                            put("p_longitude", it.toDoubleOrNull())
                        }
                        payload["accuracyMeters"]?.jsonPrimitive?.contentOrNull?.let {
                            put("p_accuracy_meters", it.toFloatOrNull())
                        }
                        put("p_client_payload_sha256", command.clientPayloadSha256)
                    },
                )
                .decodeAs<JsonObject>()

            // RPC returns: { report_id, state, server_version, server_payload_sha256, correlation_id }
            val reportId = response["report_id"]?.jsonPrimitive?.contentOrNull
            val state = response["state"]?.jsonPrimitive?.contentOrNull
            val serverVersion = response["server_version"]?.jsonPrimitive?.longOrNull
            val correlationId = response["correlation_id"]?.jsonPrimitive?.contentOrNull

            if (reportId == null || state == null) {
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
                serverVersion = serverVersion ?: 1L,
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
    }
}
