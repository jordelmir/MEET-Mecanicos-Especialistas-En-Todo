package com.elysium369.meet.ai.providers.openai_compatible

import kotlinx.serialization.json.Json

object SseStreamParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseLine(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data:")) return null
        val dataVal = trimmed.substring(5).trim()
        if (dataVal == "[DONE]") return null
        
        return try {
            val responseObj = json.decodeFromString<OpenAiCompatibleChatResponseDto>(dataVal)
            responseObj.choices.firstOrNull()?.delta?.content
        } catch (e: Exception) {
            null
        }
    }
}
