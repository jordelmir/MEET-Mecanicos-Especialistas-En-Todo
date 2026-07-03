package com.elysium.vanguard.forge.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistencia atómica basada en JSON para mapas serializables.
 *
 * Guarda el snapshot completo en un único archivo (e.g. `forge_parts.json`).
 * Concurrencia: `Mutex` serializa lecturas/escrituras. Una sola escritura a la vez.
 *
 * Trade-offs:
 *  - **Simple**: un solo archivo, sin esquema DB, sin migraciones.
 *  - **Atómico**: usa `writeText` (no es atómico real, pero en JVM write-replace es
 *    suficientemente rápido para volúmenes chicos).
 *  - **No incremental**: cada save reescribe el archivo completo. Aceptable para
 *    catálogos pequeños-medianos (< 1MB). Para producción real, migrar a Room.
 *
 * Visibilidad `internal`: solo el módulo Forge lo consume.
 */
internal class JsonFileStore<T>(
    private val file: File,
    private val serializer: (T) -> String,
    private val deserializer: (String) -> T
) {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()

    /**
     * Carga el snapshot desde disco. Si el archivo no existe, retorna null
     * (el caller decide el estado inicial — memoria vacía vs error).
     */
    suspend fun load(): T? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withContext null
            try {
                val text = file.readText(Charsets.UTF_8)
                if (text.isBlank()) null else deserializer(text)
            } catch (e: Exception) {
                // Log via stderr para no contaminar el módulo con un logger formal.
                System.err.println("JsonFileStore.load failed for ${file.path}: ${e.message}")
                null
            }
        }
    }

    /**
     * Persiste el snapshot en disco. Si el directorio padre no existe,
     * lo crea. Reemplaza el archivo existente.
     */
    suspend fun persist(snapshot: T) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                file.parentFile?.mkdirs()
                val text = serializer(snapshot)
                file.writeText(text, Charsets.UTF_8)
            } catch (e: Exception) {
                System.err.println("JsonFileStore.persist failed for ${file.path}: ${e.message}")
            }
        }
    }

    /**
     * Elimina el archivo. Útil para tests o "factory reset".
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists()) file.delete()
        }
    }
}
