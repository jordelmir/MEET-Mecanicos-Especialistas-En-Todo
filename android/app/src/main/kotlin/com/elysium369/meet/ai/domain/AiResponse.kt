package com.elysium369.meet.ai.domain

data class AiResponse(
    val text: String,
    val usage: Usage?,
    val providerId: String,
    val model: String
) {
    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )
}
