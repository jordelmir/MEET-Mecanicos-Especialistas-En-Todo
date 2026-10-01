package com.elysium.vanguard.recordshield.data.remote

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ============================================================================
 * EvidenceApiClient — Ktor HTTP Client for Vercel API
 * ============================================================================
 *
 * Why Ktor over Retrofit:
 *   - Native coroutine support (no Call adapters needed)
 *   - Streaming binary uploads via ByteArray without multipart overhead
 *   - Kotlin-first API design
 *   - CIO engine is lightweight and doesn't depend on OkHttp
 *
 * This client communicates exclusively with the Vercel Edge endpoint
 * at /api/upload-evidence. It handles:
 *   - Binary chunk uploads with integrity headers
 *   - Recording session creation
 *   - Device registration (future)
 * ============================================================================
 */
class EvidenceApiClient(
    private val baseUrl: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.NONE
        }
        engine {
            config {
                connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    /**
     * Fetch a signed upload URL from the Vercel orchestrator.
     */
    suspend fun getUploadUrl(
        deviceId: String,
        deviceToken: String,
        recordingId: String,
        chunkIndex: Int,
        mimeType: String
    ): SignedUrlResponse {
        val response: HttpResponse = client.post("$baseUrl/api/get-upload-url") {
            header("X-Device-Token", deviceToken)
            header("X-Device-Id", deviceId)
            header("X-Recording-Id", recordingId)
            header("X-Chunk-Index", chunkIndex.toString())
            contentType(ContentType.parse(mimeType))
        }

        if (response.status != HttpStatusCode.OK) {
            throw ChunkUploadException("Failed to get signed URL: ${response.bodyAsText()}")
        }

        return json.decodeFromString<SignedUrlResponse>(response.bodyAsText())
    }

    /**
     * Upload binary data directly to Supabase using a signed URL.
     * Uses PUT as required by Supabase Storage.
     */
    suspend fun uploadToSignedUrl(
        signedUrl: String,
        chunkData: ByteArray,
        mimeType: String
    ) {
        // IMPORTANT: We use a fresh HttpClient or the existing one?
        // Supabase expects a direct PUT to the signed URL.
        val response: HttpResponse = client.put(signedUrl) {
            contentType(ContentType.parse(mimeType))
            setBody(chunkData)
        }

        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            throw ChunkUploadException("Direct upload to Supabase failed: ${response.status.value}")
        }
    }

    /**
     * Register chunk metadata in the database after successful storage upload.
     */
    suspend fun registerChunk(
        deviceId: String,
        deviceToken: String,
        metadata: ChunkMetadataRequest
    ): RegisterChunkResponse {
        val response: HttpResponse = client.post("$baseUrl/api/register-chunk") {
            header("X-Device-Token", deviceToken)
            header("X-Device-Id", deviceId)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ChunkMetadataRequest.serializer(), metadata))
        }

        if (response.status != HttpStatusCode.Created) {
            throw ChunkUploadException("Failed to register chunk: ${response.bodyAsText()}")
        }

        return json.decodeFromString<RegisterChunkResponse>(response.bodyAsText())
    }

    /**
     * Create a new recording session on the remote server.
     * The Vercel endpoint creates the recording in Supabase and returns the ID.
     */
    suspend fun createRemoteRecording(
        deviceId: String,
        deviceToken: String,
        recordingType: String
    ): String {
        val response: HttpResponse = client.post("$baseUrl/api/create-recording") {
            header("X-Device-Token", deviceToken)
            header("X-Device-Id", deviceId)
            contentType(ContentType.Application.Json)
            setBody("""{"recording_type": "$recordingType"}""")
        }

        if (response.status != HttpStatusCode.Created) {
            throw ChunkUploadException("Failed to create remote recording: ${response.bodyAsText()}")
        }

        val result = json.decodeFromString<CreateRecordingResponse>(response.bodyAsText())
        return result.recordingId
    }

    fun close() {
        client.close()
    }
}

// ============================================================================
// Response Models
// ============================================================================

@Serializable
data class ChunkUploadResponse(
    val success: Boolean,
    val warning: String? = null,
    val chunk: ChunkInfo? = null
)

@Serializable
data class ChunkInfo(
    val id: String? = null,
    val index: Int,
    val storagePath: String = "",
    val sizeBytes: Long = 0,
    val sha256Hash: String = "",
    val uploadedAt: String? = null
)

@Serializable
data class SignedUrlResponse(
    val signedUrl: String,
    val token: String? = null,
    val path: String
)

@Serializable
data class ChunkMetadataRequest(
    val recordingId: String,
    val chunkIndex: Int,
    val storagePath: String,
    val sizeBytes: Long,
    val durationMs: Int,
    val mimeType: String,
    val sha256Hash: String
)

@Serializable
data class RegisterChunkResponse(
    val success: Boolean,
    val chunkId: String,
    val message: String
)

@Serializable
data class CreateRecordingResponse(
    val recordingId: String
)

class ChunkUploadException(message: String) : Exception(message)
