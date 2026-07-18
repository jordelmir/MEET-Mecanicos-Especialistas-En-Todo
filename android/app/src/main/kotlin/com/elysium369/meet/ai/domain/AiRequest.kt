package com.elysium369.meet.ai.domain

data class AiRequest(
    val feature: AiFeature,
    val providerId: String,
    val model: String,
    val messages: List<AiMessage>,
    val temperature: Double = 0.2,
    val maxTokens: Int = 1600,
    val jsonMode: Boolean = false,
    val stream: Boolean = false,
    val context: AiContext,
    val timeoutMs: Long = 60_000L
)
