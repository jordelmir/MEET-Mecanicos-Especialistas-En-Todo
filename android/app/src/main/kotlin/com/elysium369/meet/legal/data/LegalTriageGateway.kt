package com.elysium369.meet.legal.data

import com.elysium369.meet.BuildConfig
import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LegalTriageSuggestion(
    @SerialName("triage_id") val triageId: String,
    @SerialName("primary_category_code") val primaryCategoryCode: String,
    @SerialName("alternative_category_codes") val alternativeCategoryCodes: List<String> = emptyList(),
    val confidence: Double,
    val urgency: String,
    @SerialName("follow_up_questions") val followUpQuestions: List<String> = emptyList(),
    @SerialName("risk_flags") val riskFlags: List<String> = emptyList(),
    val state: String,
    val taxonomyVersion: Int,
    val disclaimer: String,
)

@Serializable
private data class LegalTriageRequest(
    val narrative: String,
    val consent: Boolean,
    val jurisdiction: String,
)

object LegalTriageGateway {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun triage(narrative: String, consent: Boolean): Result<LegalTriageSuggestion> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(consent) { "LEGAL_AI_CONSENT_REQUIRED" }
                require(narrative.trim().length in 12..4_000) { "INVALID_TRIAGE_INPUT" }
                val accessToken = SupabaseModule.client.auth.currentSessionOrNull()?.accessToken
                    ?: error("AUTHENTICATION_REQUIRED")
                val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
                require(baseUrl.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()) {
                    "LEGAL_TRIAGE_UNAVAILABLE"
                }
                val connection = (URL("$baseUrl/functions/v1/legal-triage").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
                val body = json.encodeToString(LegalTriageRequest(narrative.trim(), true, "CR"))
                OutputStreamWriter(connection.outputStream).use { it.write(body) }
                val responseBody = if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.close()
                    error("LEGAL_TRIAGE_REQUEST_FAILED_${connection.responseCode}")
                }
                json.decodeFromString<LegalTriageSuggestion>(responseBody)
            }
        }
}
