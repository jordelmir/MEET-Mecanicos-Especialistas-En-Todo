package com.elysium369.meet.ai.data

import com.elysium369.meet.ai.domain.AiProvider
import com.elysium369.meet.ai.providers.*
import com.elysium369.meet.ai.providers.minimax.MiniMaxProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProviderRegistry @Inject constructor(
    private val keyStore: AiSecureKeyStore
) {
    private val providers = mutableMapOf<String, AiProvider>()

    init {
        registerProvider(MiniMaxProvider(
            apiKeyProvider = { getApiKeyOrDebug("minimax", com.elysium369.meet.BuildConfig.MINIMAX_API_KEY_DEBUG) }
        ))
        registerProvider(OpenAiProvider(
            apiKeyProvider = { getApiKeyOrBlank("openai") }
        ))
        registerProvider(GeminiProvider(
            apiKeyProvider = { getApiKeyOrBlank("gemini") }
        ))
        registerProvider(OpenRouterProvider(
            apiKeyProvider = { getApiKeyOrBlank("openrouter") }
        ))
        registerProvider(AnthropicProvider(
            apiKeyProvider = { getApiKeyOrBlank("anthropic") }
        ))
        registerProvider(MistralProvider(
            apiKeyProvider = { getApiKeyOrBlank("mistral") }
        ))
        registerProvider(GroqProvider(
            apiKeyProvider = { getApiKeyOrBlank("groq") }
        ))
        registerProvider(DeepSeekProvider(
            apiKeyProvider = { getApiKeyOrBlank("deepseek") }
        ))
        registerProvider(OllamaProvider(
            apiKeyProvider = { getApiKeyOrBlank("ollama") }
        ))
        registerProvider(LocalHttpProvider(
            apiKeyProvider = { getApiKeyOrBlank("local_http") }
        ))
        registerProvider(CustomOpenAiProvider(
            apiKeyProvider = { getApiKeyOrBlank("custom") }
        ))
    }

    private fun registerProvider(provider: AiProvider) {
        providers[provider.id] = provider
    }

    fun getProvider(id: String): AiProvider? {
        val normalized = when (id.trim().lowercase()) {
            "openai", "gpt" -> "openai"
            "gemini" -> "gemini"
            "minimax" -> "minimax"
            "openrouter" -> "openrouter"
            "anthropic", "claude" -> "anthropic"
            "mistral" -> "mistral"
            "groq" -> "groq"
            "deepseek" -> "deepseek"
            "ollama" -> "ollama"
            "local", "local_http" -> "local_http"
            "custom" -> "custom"
            else -> id
        }
        return providers[normalized]
    }

    fun getAllProviders(): List<AiProvider> {
        return providers.values.toList()
    }

    private suspend fun getApiKeyOrDebug(providerId: String, debugKey: String): String {
        val userSavedKeyResult = keyStore.getApiKey(providerId)
        if (userSavedKeyResult.isSuccess) {
            val savedKey = userSavedKeyResult.getOrThrow()
            if (savedKey.isNotBlank()) {
                return savedKey
            }
        }
        return debugKey
    }

    private suspend fun getApiKeyOrBlank(providerId: String): String {
        return keyStore.getApiKey(providerId).getOrElse { "" }
    }
}
