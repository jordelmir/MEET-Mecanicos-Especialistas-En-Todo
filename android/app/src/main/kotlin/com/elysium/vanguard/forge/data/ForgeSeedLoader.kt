package com.elysium.vanguard.forge.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Lector de bundles JSON desde assets/forge/. Carga y parsea de forma defensiva:
 * - JSON malformado → Result.failure (no propaga excepción)
 * - Archivo ausente → Result.failure (no crashea)
 * - Bundle vacío → Result.failure
 * - Tamaño máximo 16 MB (anti resource exhaustion)
 */
class ForgeSeedLoader(
    private val context: Context? = null,
    private val maxBytes: Long = 16L * 1024L * 1024L
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = false
    }

    /**
     * Carga un bundle desde assets. Devuelve Result<List<ForgeArtifactDocument>>.
     * Ejecuta en Dispatchers.IO.
     */
    suspend fun loadBundle(assetPath: String): Result<List<ForgeArtifactDocument>> =
        withContext(Dispatchers.IO) {
            try {
                if (!assetPath.startsWith("forge/")) {
                    return@withContext Result.failure(SecurityException(
                        "Asset path must be under forge/ namespace — refusing path traversal"
                    ))
                }
                val ctx = context ?: return@withContext Result.failure(IllegalStateException(
                    "ForgeSeedLoader.loadBundle requiere Context; use parseBundleText para tests sin Context"
                ))
                val assetManager = ctx.assets
                val descriptor = assetManager.openFd(assetPath)
                val size = descriptor.length
                descriptor.close()
                if (size > maxBytes) {
                    return@withContext Result.failure(IOException(
                        "Asset $assetPath exceeds max size ${maxBytes} bytes (got $size)"
                    ))
                }
                val text = assetManager.open(assetPath).use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                }
                if (text.isBlank()) {
                    return@withContext Result.failure(IOException("Empty asset: $assetPath"))
                }
                val bundle = json.decodeFromString(ForgeArtifactBundle.serializer(), text)
                Result.success(bundle.documents)
            } catch (e: SecurityException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(IOException("Failed to parse $assetPath: ${e.message}", e))
            }
        }

    /**
     * Parsea un texto JSON ya en memoria. Útil para tests o artefactos del usuario.
     */
    suspend fun parseBundleText(text: String): Result<List<ForgeArtifactDocument>> =
        withContext(Dispatchers.IO) {
            try {
                if (text.length > maxBytes) {
                    return@withContext Result.failure(IOException("Bundle exceeds max size"))
                }
                if (text.isBlank()) {
                    return@withContext Result.failure(IOException("Empty bundle text"))
                }
                val bundle = json.decodeFromString(ForgeArtifactBundle.serializer(), text)
                Result.success(bundle.documents)
            } catch (e: Exception) {
                Result.failure(IOException("Malformed JSON: ${e.message}", e))
            }
        }
}