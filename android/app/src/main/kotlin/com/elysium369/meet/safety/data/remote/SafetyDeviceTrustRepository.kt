package com.elysium369.meet.safety.data.remote

import android.content.Context
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.data.remote.SupabaseModule
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A Play token is sent to the server decoder; only its durable VERIFIED receipt authorizes retry. */
@Singleton
class SafetyDeviceTrustRepository @Inject constructor(@ApplicationContext context: Context) {
    private val manager = IntegrityManagerFactory.createStandard(context.applicationContext)
    private val mutex = Mutex()
    private var provider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null
    private var preparedAt = 0L

    suspend fun ensureServerVerified(owner: String) = mutex.withLock {
        require(BuildConfig.SAFETY_PLAY_CLOUD_PROJECT_NUMBER > 0) { "DEVICE_TRUST_CONFIGURATION_REQUIRED" }
        requireOwner(owner)
        try {
            val challenge = exchange(buildJsonObject { put("action", "challenge") }, owner)
            val challengeId = challenge["challenge_id"]?.jsonPrimitive?.contentOrNull ?: error("INVALID_DEVICE_CHALLENGE")
            UUID.fromString(challengeId)
            val hash = challenge["request_hash"]?.jsonPrimitive?.contentOrNull ?: error("INVALID_DEVICE_CHALLENGE")
            require(hash.matches(Regex("[A-Za-z0-9_-]{43}"))) { "INVALID_DEVICE_CHALLENGE" }
            val expires = Instant.parse(challenge["expires_at"]?.jsonPrimitive?.contentOrNull)
            require(expires.isAfter(Instant.now())) { "DEVICE_CHALLENGE_EXPIRED" }
            val tokenProvider = provider?.takeIf { System.currentTimeMillis() - preparedAt < 3_600_000 }
                ?: withTimeout(60_000) {
                    manager.prepareIntegrityToken(StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                        .setCloudProjectNumber(BuildConfig.SAFETY_PLAY_CLOUD_PROJECT_NUMBER).build()).await()
                }.also { provider = it; preparedAt = System.currentTimeMillis() }
            requireOwner(owner)
            val token = withTimeout(30_000) {
                tokenProvider.request(StandardIntegrityManager.StandardIntegrityTokenRequest.builder().setRequestHash(hash).build()).await().token()
            }
            requireOwner(owner)
            val receipt = exchange(buildJsonObject {
                put("action", "verify"); put("challenge_id", challengeId); put("request_hash", hash); put("integrity_token", token)
            }, owner)
            require(receipt["result"]?.jsonPrimitive?.contentOrNull == "VERIFIED") { "SERVER_DEVICE_TRUST_REJECTED" }
            require(Instant.parse(receipt["valid_until"]?.jsonPrimitive?.contentOrNull).isAfter(Instant.now())) { "SERVER_DEVICE_TRUST_EXPIRED" }
            requireOwner(owner)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { provider = null; preparedAt = 0; throw error }
    }

    private fun requireOwner(owner: String) = check(SupabaseModule.client.auth.currentUserOrNull()?.id == owner) { "OWNER_CHANGED" }

    private suspend fun exchange(body: JsonObject, owner: String): JsonObject = withContext(Dispatchers.IO) {
        requireOwner(owner)
        val session = SupabaseModule.client.auth.currentSessionOrNull() ?: error("AUTHENTICATION_REQUIRED")
        val endpoint = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/safety-device-trust")
        check(endpoint.protocol == "https") { "SECURE_DEVICE_VERIFIER_REQUIRED" }
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 30_000
            instanceFollowRedirects = false; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode in 200..299) { "SERVER_DEVICE_VERIFICATION_UNAVAILABLE" }
            val raw = connection.inputStream.use { input ->
                val bytes = ByteArray(8193); var size = 0
                while (size < bytes.size) { val count = input.read(bytes, size, bytes.size - size); if (count < 0) break; size += count }
                check(size <= 8192) { "INVALID_DEVICE_VERIFICATION_RECEIPT" }
                String(bytes, 0, size, Charsets.UTF_8)
            }
            requireOwner(owner)
            Json.parseToJsonElement(raw).jsonObject
        } finally { connection.disconnect() }
    }
}
