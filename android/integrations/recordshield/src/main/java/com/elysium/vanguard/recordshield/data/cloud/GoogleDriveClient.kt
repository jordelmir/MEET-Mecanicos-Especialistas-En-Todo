package com.elysium.vanguard.recordshield.data.cloud

import android.content.Context
import com.elysium.vanguard.recordshield.util.SafeLog as Log
import com.elysium.vanguard.recordshield.data.local.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ============================================================================
 * GoogleDriveClient — Google Drive REST API with OAuth2
 * ============================================================================
 *
 * Why REST API over deprecated Android SDK:
 *   - Google officially deprecated google-play-services-drive
 *   - REST API is maintained, documented, and future-proof
 *   - Uses standard OAuth2 token-based auth
 *   - Supports resumable uploads for large files
 *
 * Upload Flow:
 *   1. Use OAuth2 access token from Google Sign-In
 *   2. Create folder structure: RecordShield/{recordingId}/
 *   3. Upload chunk via multipart POST
 *   4. Return Google Drive file ID as storage path
 *
 * Folder Structure on Drive:
 *   RecordShield/
 *     └── {recording_id}/
 *           ├── chunk_00000.mp4
 *           ├── chunk_00001.mp4
 *           └── ...
 * ============================================================================
 */
@Singleton
class GoogleDriveClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    companion object {
        private const val TAG = "GoogleDriveClient"
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_API_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val ROOT_FOLDER_NAME = "RecordShield"
        private const val MIME_FOLDER = "application/vnd.google-apps.folder"
        private const val FIELDS = "id,name,mimeType,size,createdTime"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            config {
                connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    // ========================================================================
    // TOKEN MANAGEMENT
    // ========================================================================

    private fun getAccessToken(): String {
        return secureStorage.googleDriveAccessToken
            ?: throw CloudUploadException("Google Drive not authenticated", "google_drive", false)
    }

    private fun getRefreshToken(): String? = secureStorage.googleDriveRefreshToken

    fun saveTokens(accessToken: String, refreshToken: String?) {
        secureStorage.googleDriveAccessToken = accessToken
        refreshToken?.let { secureStorage.googleDriveRefreshToken = it }
        Log.i(TAG, "Google Drive tokens saved")
    }

    fun clearTokens() {
        secureStorage.googleDriveAccessToken = null
        secureStorage.googleDriveRefreshToken = null
    }

    /**
     * Refresh the access token using the refresh token.
     * Returns true if refresh was successful.
     */
    suspend fun refreshAccessToken(): Boolean {
        val refreshToken = getRefreshToken()
        Log.i(TAG, "Attempting token refresh, hasRefreshToken=${refreshToken != null}")
        if (refreshToken == null) return false
        return try {
            val response: HttpResponse = client.post("https://oauth2.googleapis.com/token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(FormDataContent(Parameters.build {
                    append("client_id", getOAuthClientId())
                    append("client_secret", getOAuthClientSecret())
                    append("refresh_token", refreshToken)
                    append("grant_type", "refresh_token")
                }))
            }

            if (response.status == HttpStatusCode.OK) {
                val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
                val newAccessToken = body["access_token"]?.jsonPrimitive?.content ?: return false
                secureStorage.googleDriveAccessToken = newAccessToken
                Log.i(TAG, "Access token refreshed successfully")
                true
            } else {
                Log.e(TAG, "Token refresh failed: ${response.status}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error", e)
            false
        }
    }

    private fun getOAuthClientId(): String {
        return com.elysium.vanguard.recordshield.BuildConfig.OAUTH_CLIENT_ID.ifEmpty { "YOUR_CLIENT_ID.apps.googleusercontent.com" }
    }

    private fun getOAuthClientSecret(): String {
        return com.elysium.vanguard.recordshield.BuildConfig.OAUTH_CLIENT_SECRET
    }

    // ========================================================================
    // FOLDER MANAGEMENT
    // ========================================================================

    /**
     * Get or create the root RecordShield folder on Drive.
     */
    suspend fun getOrCreateRootFolder(): String {
        Log.i(TAG, "Searching for root folder: $ROOT_FOLDER_NAME")
        // Search for existing root folder
        val searchQuery = "name='$ROOT_FOLDER_NAME' and mimeType='$MIME_FOLDER' and trashed=false"
        val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
        val searchResponse: HttpResponse = client.get(
            "$DRIVE_API_BASE/files?q=$encodedQuery&fields=files(id,name)"
        ) {
            header("Authorization", "Bearer ${getAccessToken()}")
        }

        if (searchResponse.status == HttpStatusCode.OK) {
            val body = json.parseToJsonElement(searchResponse.bodyAsText()) as JsonObject
            val files = body["files"]?.toString() ?: "[]"
            if (files.contains("id")) {
                // Parse the first file ID
                val fileArray = json.parseToJsonElement(files) as kotlinx.serialization.json.JsonArray
                if (fileArray.isNotEmpty()) {
                    val firstFile = fileArray[0] as JsonObject
                    return firstFile["id"]?.jsonPrimitive?.content ?: createRootFolder()
                }
            }
        }

        return createRootFolder()
    }

    private suspend fun createRootFolder(): String {
        val metadata = JsonObject(mapOf(
            "name" to kotlinx.serialization.json.JsonPrimitive(ROOT_FOLDER_NAME),
            "mimeType" to kotlinx.serialization.json.JsonPrimitive(MIME_FOLDER)
        ))

        val response: HttpResponse = client.post("$DRIVE_API_BASE/files") {
            header("Authorization", "Bearer ${getAccessToken()}")
            contentType(ContentType.Application.Json)
            setBody(metadata.toString())
        }

        if (response.status == HttpStatusCode.OK) {
            val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
            val folderId = body["id"]?.jsonPrimitive?.content
                ?: throw CloudUploadException("Failed to create root folder", "google_drive")
            Log.i(TAG, "Root folder created: $folderId")
            return folderId
        } else {
            throw CloudUploadException(
                "Failed to create root folder: ${response.status}",
                "google_drive"
            )
        }
    }

    /**
     * Get or create a recording subfolder inside the root folder.
     */
    suspend fun getOrCreateRecordingFolder(rootFolderId: String, recordingId: String): String {
        val searchQuery = "name='$recordingId' and mimeType='$MIME_FOLDER' and '$rootFolderId' in parents and trashed=false"
        val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
        val searchResponse: HttpResponse = client.get(
            "$DRIVE_API_BASE/files?q=$encodedQuery&fields=files(id,name)"
        ) {
            header("Authorization", "Bearer ${getAccessToken()}")
        }

        if (searchResponse.status == HttpStatusCode.OK) {
            val body = json.parseToJsonElement(searchResponse.bodyAsText()) as JsonObject
            val filesStr = body["files"]?.toString() ?: "[]"
            if (filesStr.contains("id")) {
                val fileArray = json.parseToJsonElement(filesStr) as kotlinx.serialization.json.JsonArray
                if (fileArray.isNotEmpty()) {
                    val firstFile = fileArray[0] as JsonObject
                    return firstFile["id"]?.jsonPrimitive?.content
                        ?: createRecordingFolder(rootFolderId, recordingId)
                }
            }
        }

        return createRecordingFolder(rootFolderId, recordingId)
    }

    private suspend fun createRecordingFolder(rootFolderId: String, recordingId: String): String {
        val metadata = JsonObject(mapOf(
            "name" to kotlinx.serialization.json.JsonPrimitive(recordingId),
            "mimeType" to kotlinx.serialization.json.JsonPrimitive(MIME_FOLDER),
            "parents" to kotlinx.serialization.json.JsonPrimitive(rootFolderId)
        ))

        val response: HttpResponse = client.post("$DRIVE_API_BASE/files") {
            header("Authorization", "Bearer ${getAccessToken()}")
            contentType(ContentType.Application.Json)
            setBody(metadata.toString())
        }

        if (response.status == HttpStatusCode.OK) {
            val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
            return body["id"]?.jsonPrimitive?.content
                ?: throw CloudUploadException("Failed to create recording folder", "google_drive")
        } else {
            throw CloudUploadException(
                "Failed to create recording folder: ${response.status}",
                "google_drive"
            )
        }
    }

    /**
     * Get or create a subfolder within a parent folder.
     * Used for chunk folders (chunk_00001/) in dual camera mode.
     */
    suspend fun getOrCreateSubfolder(parentFolderId: String, folderName: String): String {
        val searchQuery = "name='$folderName' and mimeType='$MIME_FOLDER' and '$parentFolderId' in parents and trashed=false"
        val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
        val searchResponse: HttpResponse = client.get(
            "$DRIVE_API_BASE/files?q=$encodedQuery&fields=files(id,name)"
        ) {
            header("Authorization", "Bearer ${getAccessToken()}")
        }

        if (searchResponse.status == HttpStatusCode.OK) {
            val body = json.parseToJsonElement(searchResponse.bodyAsText()) as JsonObject
            val filesStr = body["files"]?.toString() ?: "[]"
            if (filesStr.contains("id")) {
                val fileArray = json.parseToJsonElement(filesStr) as kotlinx.serialization.json.JsonArray
                if (fileArray.isNotEmpty()) {
                    val firstFile = fileArray[0] as JsonObject
                    return firstFile["id"]?.jsonPrimitive?.content
                        ?: createSubfolder(parentFolderId, folderName)
                }
            }
        }

        return createSubfolder(parentFolderId, folderName)
    }

    private suspend fun createSubfolder(parentFolderId: String, folderName: String): String {
        val metadata = JsonObject(mapOf(
            "name" to kotlinx.serialization.json.JsonPrimitive(folderName),
            "mimeType" to kotlinx.serialization.json.JsonPrimitive(MIME_FOLDER),
            "parents" to kotlinx.serialization.json.JsonPrimitive(parentFolderId)
        ))

        val response: HttpResponse = client.post("$DRIVE_API_BASE/files") {
            header("Authorization", "Bearer ${getAccessToken()}")
            contentType(ContentType.Application.Json)
            setBody(metadata.toString())
        }

        if (response.status == HttpStatusCode.OK) {
            val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
            return body["id"]?.jsonPrimitive?.content
                ?: throw CloudUploadException("Failed to create subfolder", "google_drive")
        } else {
            throw CloudUploadException(
                "Failed to create subfolder: ${response.status}",
                "google_drive"
            )
        }
    }

    // ========================================================================
    // FILE UPLOAD
    // ========================================================================

    /**
     * Upload a chunk file to Google Drive using Java HttpURLConnection.
     * Bypasses Ktor entirely to avoid ContentNegotiation corrupting the multipart body.
     * Returns the Google Drive file ID.
     */
    suspend fun uploadChunkFile(
        folderId: String,
        chunkIndex: Int,
        chunkData: ByteArray,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        val fileName = "chunk_${String.format("%05d", chunkIndex)}.${mimeTypeToExt(mimeType)}"
        com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== uploadChunkFile: $fileName (${chunkData.size} bytes) → folder $folderId")
        Log.e(TAG, "=== uploadChunkFile: $fileName (${chunkData.size} bytes) → folder $folderId")

        val token = getAccessToken()
        val boundary = "----RecordShield${System.currentTimeMillis()}"
        val metadataJson = """{"name":"$fileName","parents":["$folderId"]}"""

        val uploadUrl = java.net.URL(
            "$UPLOAD_API_BASE/files?uploadType=multipart&addParents=$folderId&fields=id,name,size,parents"
        )

        val conn = uploadUrl.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000

            conn.outputStream.use { os ->
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
                os.write(metadataJson.toByteArray(Charsets.UTF_8))
                os.write("\r\n".toByteArray())
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
                os.write(chunkData)
                os.write("\r\n--$boundary--\r\n".toByteArray())
                os.flush()
            }

            val status = conn.responseCode
            com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== uploadChunkFile HTTP: $status")
            Log.e(TAG, "=== uploadChunkFile HTTP: $status")

            val responseBody = if (status in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "empty"
            }

            if (status in 200..299) {
                val jsonBody = json.parseToJsonElement(responseBody) as JsonObject
                val fileId = jsonBody["id"]?.jsonPrimitive?.content
                    ?: throw CloudUploadException("Upload succeeded but no file ID", "google_drive")
                val parents = jsonBody["parents"]?.toString() ?: "unknown"
                com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== uploadChunkFile SUCCESS: $fileName → fileID=$fileId, parents=$parents")
                Log.e(TAG, "=== uploadChunkFile SUCCESS: $fileName → fileID=$fileId, parents=$parents")
                fileId
            } else {
                Log.e(TAG, "=== uploadChunkFile FAILED: HTTP $status — $responseBody")
                com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== uploadChunkFile FAILED: HTTP $status — $responseBody")
                if (status == 401) {
                    if (refreshAccessToken()) {
                        Log.e(TAG, "Token refreshed, retrying upload...")
                        return@withContext uploadChunkFile(folderId, chunkIndex, chunkData, mimeType)
                    }
                }
                throw CloudUploadException(
                    "Upload failed: HTTP $status — $responseBody",
                    "google_drive",
                    isRetryable = status != 401
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Upload a file to a Drive folder with a custom filename.
     * Used for dual camera uploads (rear.mp4, front.mp4).
     */
    suspend fun uploadFileToFolder(
        folderId: String,
        fileName: String,
        fileData: ByteArray,
        mimeType: String
    ): String = withContext(Dispatchers.IO) {
        com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== uploadFileToFolder: $fileName (${fileData.size} bytes) → folder $folderId")
        Log.e(TAG, "=== uploadFileToFolder: $fileName (${fileData.size} bytes) → folder $folderId")

        val token = getAccessToken()
        val boundary = "----RecordShield${System.currentTimeMillis()}"
        val metadataJson = """{"name":"$fileName","parents":["$folderId"]}"""

        val uploadUrl = java.net.URL(
            "$UPLOAD_API_BASE/files?uploadType=multipart&addParents=$folderId&fields=id,name,size,parents"
        )

        val conn = uploadUrl.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000

            conn.outputStream.use { os ->
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
                os.write(metadataJson.toByteArray(Charsets.UTF_8))
                os.write("\r\n".toByteArray())
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
                os.write(fileData)
                os.write("\r\n--$boundary--\r\n".toByteArray())
                os.flush()
            }

            val status = conn.responseCode
            com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== uploadFileToFolder HTTP: $status")
            Log.e(TAG, "=== uploadFileToFolder HTTP: $status")

            val responseBody = if (status in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "empty"
            }

            if (status in 200..299) {
                val jsonBody = json.parseToJsonElement(responseBody) as JsonObject
                val fileId = jsonBody["id"]?.jsonPrimitive?.content
                    ?: throw CloudUploadException("Upload succeeded but no file ID", "google_drive")
                val parents = jsonBody["parents"]?.toString() ?: "unknown"
                com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== uploadFileToFolder SUCCESS: $fileName → fileID=$fileId, parents=$parents")
                Log.e(TAG, "=== uploadFileToFolder SUCCESS: $fileName → fileID=$fileId, parents=$parents")
                fileId
            } else {
                Log.e(TAG, "=== uploadFileToFolder FAILED: HTTP $status — $responseBody")
                com.elysium.vanguard.recordshield.util.LogFile.log(TAG, "=== uploadFileToFolder FAILED: HTTP $status — $responseBody")
                if (status == 401) {
                    if (refreshAccessToken()) {
                        Log.e(TAG, "Token refreshed, retrying upload...")
                        return@withContext uploadFileToFolder(folderId, fileName, fileData, mimeType)
                    }
                }
                throw CloudUploadException(
                    "Upload failed: HTTP $status — $responseBody",
                    "google_drive",
                    isRetryable = status != 401
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Generate a direct download URL for a Drive file.
     * Returns a temporary download link.
     */
    suspend fun getDownloadUrl(fileId: String): String {
        // For direct download, we use the webContentLink
        return "https://drive.google.com/uc?export=download&id=$fileId"
    }

    /**
     * Verify a file exists on Drive.
     */
    suspend fun verifyFileExists(fileId: String): Boolean {
        return try {
            val response: HttpResponse = client.get("$DRIVE_API_BASE/files/$fileId?fields=id") {
                header("Authorization", "Bearer ${getAccessToken()}")
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete a file from Drive.
     */
    suspend fun deleteFile(fileId: String): Boolean {
        return try {
            val response: HttpResponse = client.delete("$DRIVE_API_BASE/files/$fileId") {
                header("Authorization", "Bearer ${getAccessToken()}")
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed for $fileId", e)
            false
        }
    }

    /**
     * List all files in a folder.
     */
    suspend fun listFilesInFolder(folderId: String): List<CloudChunkInfo> {
        val query = "'$folderId' in parents and trashed=false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val response: HttpResponse = client.get(
            "$DRIVE_API_BASE/files?q=$encodedQuery&fields=files(id,name,size,mimeType,createdTime)&orderBy=name"
        ) {
            header("Authorization", "Bearer ${getAccessToken()}")
        }

        if (response.status == HttpStatusCode.OK) {
            val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
            val filesStr = body["files"]?.toString() ?: "[]"
            val fileArray = json.parseToJsonElement(filesStr) as kotlinx.serialization.json.JsonArray

            return fileArray.mapNotNull { element ->
                val file = element as? JsonObject ?: return@mapNotNull null
                val name = file["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (!name.startsWith("chunk_")) return@mapNotNull null

                val chunkIndex = name.removePrefix("chunk_").split(".")[0].toIntOrNull() ?: 0
                CloudChunkInfo(
                    storagePath = file["id"]?.jsonPrimitive?.content ?: "",
                    sizeBytes = file["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    mimeType = "video/mp4",
                    sha256Hash = "", // Drive doesn't provide hash directly
                    uploadedAt = System.currentTimeMillis()
                )
            }
        }
        return emptyList()
    }

    /**
     * Get total storage usage for RecordShield folder.
     */
    suspend fun getStorageUsage(): Long {
        return try {
            val rootId = getOrCreateRootFolder()
            val query = "'$rootId' in parents and trashed=false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val response: HttpResponse = client.get(
                "$DRIVE_API_BASE/files?q=$encodedQuery&fields=files(size)"
            ) {
                header("Authorization", "Bearer ${getAccessToken()}")
            }

            if (response.status == HttpStatusCode.OK) {
                val body = json.parseToJsonElement(response.bodyAsText()) as JsonObject
                val filesStr = body["files"]?.toString() ?: "[]"
                val fileArray = json.parseToJsonElement(filesStr) as kotlinx.serialization.json.JsonArray
                fileArray.sumOf { element ->
                    val file = element as? JsonObject ?: return@sumOf 0L
                    file["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                }
            } else 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get storage usage", e)
            0L
        }
    }

    fun close() {
        client.close()
    }

    private fun mimeTypeToExt(mimeType: String): String = when (mimeType) {
        "video/mp4" -> "mp4"
        "audio/mp4", "audio/aac" -> "m4a"
        "video/webm" -> "webm"
        "audio/webm" -> "weba"
        else -> "mp4"
    }
}
