package com.elysium369.meet.ai.domain

interface AiProvider {
    val id: String
    val displayName: String
    val defaultBaseUrl: String
    val defaultModels: List<AiModel>
    val capabilities: Set<AiCapability>

    suspend fun complete(request: AiRequest): Result<AiResponse>

    suspend fun stream(
        request: AiRequest,
        onToken: suspend (String) -> Unit
    ): Result<AiResponse>

    suspend fun testConnection(config: AiProviderConfig): Result<Unit>
}
