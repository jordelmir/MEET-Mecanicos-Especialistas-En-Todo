package com.elysium369.meet.ai.domain

data class AiMessage(
    val role: AiRole,
    val content: String
)

enum class AiRole {
    SYSTEM,
    USER,
    ASSISTANT;

    override fun toString(): String = name.lowercase()
}
