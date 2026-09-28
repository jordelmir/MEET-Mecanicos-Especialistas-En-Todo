package dragoncore.persistence

import cr.dragon.calc.ui.document.Cell
import cr.dragon.calc.ui.document.DragonDocument
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.content.Context
import java.io.File

/**
 * 🐉 DragonPersistence: El Archivista Real.
 * Gestiona la serialización y el almacenamiento físico de los DragonBooks (.drbk).
 */
object DragonPersistence {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Guarda un documento en el almacenamiento interno.
     */
    fun saveDocument(context: Context, document: DragonDocument) {
        try {
            val fileName = "${document.id}.drbk"
            val jsonString = json.encodeToString(document)
            context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                it.write(jsonString.toByteArray())
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Carga un documento por ID.
     */
    fun loadDocument(context: Context, id: String): DragonDocument? {
        return try {
            val fileName = "$id.drbk"
            val jsonString = context.openFileInput(fileName).bufferedReader().use { it.readText() }
            json.decodeFromString<DragonDocument>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Lista todos los libros disponibles (.drbk).
     */
    fun listBooks(context: Context): List<DragonDocument> {
        val files = context.fileList() ?: return emptyList()
        return files.filter { it.endsWith(".drbk") }.mapNotNull { fileName ->
            val id = fileName.removeSuffix(".drbk")
            loadDocument(context, id)
        }.sortedByDescending { it.lastModified }
    }

    /**
     * Elimina un libro.
     */
    fun deleteBook(context: Context, id: String) {
        context.deleteFile("$id.drbk")
    }
}
