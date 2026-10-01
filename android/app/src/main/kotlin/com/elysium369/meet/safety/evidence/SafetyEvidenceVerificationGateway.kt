package com.elysium369.meet.safety.evidence

import com.elysium369.meet.BuildConfig
import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Calls the server byte verifier. Only a durable server MATCH becomes VERIFIED. */
object SafetyEvidenceVerificationGateway {
    suspend fun verify(evidenceId: String, owner: String): Pair<String, String> = withContext(Dispatchers.IO) {
        UUID.fromString(evidenceId)
        val session = SupabaseModule.client.auth.currentSessionOrNull() ?: error("AUTHENTICATION_REQUIRED")
        check(SupabaseModule.client.auth.currentUserOrNull()?.id == owner) { "OWNER_CHANGED" }
        val endpoint = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/safety-evidence-verify")
        check(endpoint.protocol == "https") { "SECURE_VERIFIER_REQUIRED" }
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
        }
        try {
            val request = buildJsonObject { put("evidence_id", evidenceId) }.toString()
            connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode in 200..299) { "SERVER_VERIFICATION_PENDING" }
            val raw = connection.inputStream.use { input ->
                val data = ByteArray(8193)
                var size = 0
                while (size < data.size) {
                    val count = input.read(data, size, data.size - size)
                    if (count < 0) break
                    size += count
                }
                check(size <= 8192) { "INVALID_VERIFICATION_RECEIPT" }
                String(data, 0, size, Charsets.UTF_8)
            }
            val receipt = Json.parseToJsonElement(raw).jsonObject
            check(receipt["ok"]?.jsonPrimitive?.booleanOrNull == true) { "INVALID_VERIFICATION_RECEIPT" }
            UUID.fromString(receipt["verification_id"]?.jsonPrimitive?.contentOrNull)
            val state = when (receipt["verification_state"]?.jsonPrimitive?.contentOrNull) {
                "MATCH" -> "VERIFIED"
                "MISMATCH", "QUARANTINED" -> "QUARANTINED"
                else -> error("SERVER_VERIFICATION_PENDING")
            }
            check(SupabaseModule.client.auth.currentUserOrNull()?.id == owner) { "OWNER_CHANGED" }
            state to raw
        } finally { connection.disconnect() }
    }
}
