package com.elysium369.meet.ai.data

import com.elysium369.meet.ai.domain.AiCommercialPlan
import com.elysium369.meet.ai.domain.AiDiagnosticCase
import com.elysium369.meet.ai.domain.AiQuotaSnapshot
import com.elysium369.meet.ai.domain.AiCaseTurnResponse
import com.elysium369.meet.ai.domain.AiCaseStatus
import com.elysium369.meet.ai.domain.DiagnosticAiAnswer
import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiManagedRepository @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun openDiagnosticCase(
        vehicleId: String,
        diagnosticCaseId: String? = null
    ): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = SupabaseModule.client
            val params = buildJsonObject {
                put("p_vehicle_id", vehicleId)
                diagnosticCaseId?.let { put("p_diagnostic_case_id", it) }
            }

            val response = client.postgrest.rpc("ai_open_diagnostic_case", params)
            val obj = json.parseToJsonElement(response.data).jsonObject

            val success = obj["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
            if (!success) {
                throw IllegalStateException("Failed to open AI case: ${response.data}")
            }

            val caseId = obj["case_id"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing case_id in response")
            val remaining = obj["remaining_cases"]?.jsonPrimitive?.int ?: 0

            Pair(caseId, remaining)
        }
    }

    suspend fun submitCaseTurn(
        caseId: String,
        role: String,
        message: String,
        evidenceRefs: List<String> = emptyList(),
        promptTokens: Long = 0L,
        completionTokens: Long = 0L,
        providerId: String = "openai",
        modelId: String = "gpt-5.4-nano",
        latencyMs: Long = 0L
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val client = SupabaseModule.client
            val params = buildJsonObject {
                put("p_case_id", caseId)
                put("p_role", role)
                put("p_message", message)
                put("p_prompt_tokens", promptTokens)
                put("p_completion_tokens", completionTokens)
                put("p_provider_id", providerId)
                put("p_model_id", modelId)
                put("p_latency_ms", latencyMs)
            }

            val response = client.postgrest.rpc("ai_submit_case_turn", params)
            val obj = json.parseToJsonElement(response.data).jsonObject

            val success = obj["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
            if (!success) {
                throw IllegalStateException("Failed to submit case turn: ${response.data}")
            }

            obj["accumulated_cost_micros_usd"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        }
    }

    suspend fun linkCaseToRepairIntent(
        caseId: String,
        repairIntentId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = SupabaseModule.client
            val params = buildJsonObject {
                put("p_case_id", caseId)
                put("p_repair_intent_id", repairIntentId)
            }
            client.postgrest.rpc("ai_link_case_to_repair_intent", params)
            Unit
        }
    }
}
