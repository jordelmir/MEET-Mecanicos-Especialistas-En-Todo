package com.elysium369.meet.automotive.parts

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

class ProcedureKnowledgeBase(private val context: Context) {

    companion object {
        private const val TAG = "ProcedureKB"
        
        private val jsonParser = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    private var partsList: List<AutomotivePart> = emptyList()

    init {
        loadKnowledgeBase()
    }

    private fun loadKnowledgeBase() {
        try {
            val parts = mutableListOf<AutomotivePart>()

            // Load parts ontology
            val ontologyStream = context.assets.open("knowledge/parts/parts_ontology_es.json")
            val ontologyText = InputStreamReader(ontologyStream).use { it.readText() }
            val loadedParts = jsonParser.decodeFromString<List<AutomotivePart>>(ontologyText)
            parts.addAll(loadedParts)

            // Load suspension procedures and combine them with ontology parts
            try {
                val suspensionStream = context.assets.open("knowledge/procedures/suspension_procedures.json")
                val suspensionText = InputStreamReader(suspensionStream).use { it.readText() }
                val loadedProcedures = jsonParser.decodeFromString<List<AutomotivePart>>(suspensionText)
                
                // Merge procedures into the ontology parts list
                loadedProcedures.forEach { procPart ->
                    val index = parts.indexOfFirst { it.id == procPart.id }
                    if (index != -1) {
                        val existing = parts[index]
                        parts[index] = existing.copy(
                            procedures = procPart.procedures,
                            diagnosticRules = procPart.diagnosticRules
                        )
                    } else {
                        parts.add(procPart)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading suspension procedures", e)
            }

            partsList = parts
            Log.d(TAG, "Successfully loaded ${partsList.size} parts into knowledge base")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error loading procedure knowledge base", e)
            partsList = emptyList()
        }
    }

    fun getPart(partId: String): AutomotivePart? {
        return partsList.find { it.id == partId }
    }

    fun searchParts(query: String): List<AutomotivePart> {
        if (query.isBlank()) return partsList
        val queryLower = query.lowercase().trim()
        return partsList.filter { part ->
            part.canonicalNameEs.lowercase().contains(queryLower) ||
                    part.canonicalNameEn.lowercase().contains(queryLower) ||
                    part.aliases.any { it.lowercase().contains(queryLower) } ||
                    part.description.lowercase().contains(queryLower)
        }
    }

    fun getAllParts(): List<AutomotivePart> {
        return partsList
    }

    fun getProcedure(partId: String): PartProcedure? {
        return getPart(partId)?.procedures?.firstOrNull()
    }
}
