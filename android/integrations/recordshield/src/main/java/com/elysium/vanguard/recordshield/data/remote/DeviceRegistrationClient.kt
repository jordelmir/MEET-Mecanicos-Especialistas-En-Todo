package com.elysium.vanguard.recordshield.data.remote

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ============================================================================
 * DeviceRegistrationClient — Device Registration API
 * ============================================================================
 *
 * Handles one-time device registration with the Vercel backend.
 * The flow is:
 *   1. App generates a device fingerprint (ANDROID_ID based)
 *   2. Sends POST to /api/register-device
 *   3. Server returns { deviceId, apiToken }
 *   4. App stores both in EncryptedSharedPreferences
 *   5. The apiToken is used for all subsequent API calls
 *
 * This token is returned ONCE and never stored in plaintext on the server.
 * ============================================================================
 */
class DeviceRegistrationClient(
    private val baseUrl: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            config {
                connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    /**
     * Register this device with the Vercel backend.
     *
     * @param fingerprint Unique device identifier (ANDROID_ID)
     * @param alias User-friendly device name
     * @return DeviceRegistrationResult with deviceId and apiToken
     * @throws DeviceRegistrationException on failure
     */
    suspend fun registerDevice(
        fingerprint: String,
        alias: String
    ): DeviceRegistrationResult {
        val response: HttpResponse = client.post("$baseUrl/api/register-device") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "device_fingerprint": "$fingerprint",
                    "device_alias": "$alias",
                    "platform": "android",
                    "os_version": "${android.os.Build.VERSION.RELEASE}",
                    "app_version": "1.0.0-alpha"
                }
            """.trimIndent())
        }

        val bodyText = response.bodyAsText()

        return when (response.status) {
            HttpStatusCode.Created -> {
                json.decodeFromString<DeviceRegistrationResult>(bodyText)
            }
            HttpStatusCode.Conflict -> {
                // Device already registered — parse the response for deviceId
                val conflict = json.decodeFromString<DeviceConflictResponse>(bodyText)
                throw DeviceAlreadyRegisteredException(conflict.deviceId)
            }
            else -> {
                throw DeviceRegistrationException(
                    "Registration failed (${response.status.value}): $bodyText"
                )
            }
        }
    }

    fun close() {
        client.close()
    }
}

@Serializable
data class DeviceRegistrationResult(
    val deviceId: String,
    val apiToken: String,
    val message: String = "",
    val warning: String = ""
)

@Serializable
data class DeviceConflictResponse(
    val error: String = "",
    val deviceId: String = "",
    val hint: String = ""
)

class DeviceRegistrationException(message: String) : Exception(message)

class DeviceAlreadyRegisteredException(val existingDeviceId: String) :
    Exception("Device already registered with ID: $existingDeviceId")
