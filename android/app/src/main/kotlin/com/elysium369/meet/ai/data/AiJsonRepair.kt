package com.elysium369.meet.ai.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object AiJsonRepair {
    private val json = Json { ignoreUnknownKeys = true }

    fun repairAndParse(rawText: String): JsonObject? {
        val extracted = extractJson(rawText) ?: return null
        return try {
            json.parseToJsonElement(extracted) as? JsonObject
        } catch (e: Exception) {
            val repaired = attemptRepair(extracted)
            try {
                json.parseToJsonElement(repaired) as? JsonObject
            } catch (ex: Exception) {
                android.util.Log.e("AiJsonRepair", "JSON repair failed: ${ex.message}")
                null
            }
        }
    }

    private fun extractJson(text: String): String? {
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1)
        }
        return null
    }

    private fun attemptRepair(jsonStr: String): String {
        var result = jsonStr.trim()
        result = result.replace(Regex(",\\s*\\}"), "}")
        result = result.replace(Regex(",\\s*\\]"), "]")
        return result
    }
}
