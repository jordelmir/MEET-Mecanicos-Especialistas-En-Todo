package com.elysium369.meet.ai.domain

data class AiProviderConfig(
    val providerId: String,
    val displayName: String,
    val apiKeyAlias: String?,
    val baseUrl: String,
    val model: String,
    val temperature: Double = 0.2,
    val maxTokens: Int = 1600,
    val streamingEnabled: Boolean = true,
    val jsonModeEnabled: Boolean = false,
    val enabled: Boolean = true,
    val useBackendProxy: Boolean = false,
    val updatedAtMillis: Long
)
