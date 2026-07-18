package com.elysium369.meet.ai.providers.minimax

import com.elysium369.meet.ai.domain.AiModel
import com.elysium369.meet.ai.providers.openai_compatible.OpenAiCompatibleProvider

class MiniMaxProvider(
    apiKeyProvider: suspend () -> String,
    baseUrl: String = "https://api.minimax.io/v1",
    customBaseUrlProvider: suspend () -> String? = { null }
) : OpenAiCompatibleProvider(
    id = "minimax",
    displayName = "MiniMax",
    defaultBaseUrl = baseUrl,
    defaultModels = listOf(
        AiModel("MiniMax-M1", "MiniMax-M1"),
        AiModel("MiniMax-Text-01", "MiniMax Text 01")
    ),
    apiKeyProvider = apiKeyProvider,
    customBaseUrlProvider = customBaseUrlProvider
)
