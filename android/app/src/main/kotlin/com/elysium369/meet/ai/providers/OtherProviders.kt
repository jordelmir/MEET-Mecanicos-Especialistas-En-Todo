package com.elysium369.meet.ai.providers

import com.elysium369.meet.ai.domain.AiModel
import com.elysium369.meet.ai.providers.openai_compatible.OpenAiCompatibleProvider

class OpenAiProvider(
    apiKeyProvider: suspend () -> String,
    customBaseUrlProvider: suspend () -> String? = { null }
) : OpenAiCompatibleProvider(
    id = "openai",
    displayName = "OpenAI",
    defaultBaseUrl = "https://api.openai.com/v1",
    defaultModels = listOf(
        AiModel("gpt-4o", "GPT-4o"),
        AiModel("gpt-4o-mini", "GPT-4o mini"),
        AiModel("gpt-4-turbo", "GPT-4 Turbo"),
        AiModel("gpt-3.5-turbo", "GPT-3.5 Turbo")
    ),
    apiKeyProvider = apiKeyProvider,
    customBaseUrlProvider = customBaseUrlProvider
)

class GeminiProvider(
    apiKeyProvider: suspend () -> String,
    customBaseUrlProvider: suspend () -> String? = { null }
) : OpenAiCompatibleProvider(
    id = "gemini",
    displayName = "Google Gemini",
    defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
    defaultModels = listOf(
        AiModel("gemini-1.5-pro", "Gemini 1.5 Pro"),
        AiModel("gemini-1.5-flash", "Gemini 1.5 Flash"),
        AiModel("gemini-2.0-flash-exp", "Gemini 2.0 Flash Exp")
    ),
    apiKeyProvider = apiKeyProvider,
    customBaseUrlProvider = customBaseUrlProvider
)

class OpenRouterProvider(
    apiKeyProvider: suspend () -> String,
    customBaseUrlProvider: suspend () -> String? = { null }
) : OpenAiCompatibleProvider(
    id = "openrouter",
    displayName = "OpenRouter",
    defaultBaseUrl = "https://openrouter.ai/api/v1",
    defaultModels = listOf(
        AiModel("meta-llama/llama-3-8b-instruct", "Llama 3 8B"),
        AiModel("google/gemini-flash-1.5", "Gemini Flash 1.5"),
        AiModel("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet")
    ),
    apiKeyProvider = apiKeyProvider,
    customBaseUrlProvider = customBaseUrlProvider
)

class AnthropicProvider(
    apiKeyProvider: suspend () -> String,
    customBaseUrlProvider: suspend () -> String? = { null }
) : OpenAiCompatibleProvider(
    id = "anthropic",
    displayName = "Anthropic",
    defaultBaseUrl = "https://api.anthropic.com/v1",
    defaultModels = listOf(
        AiModel("claude-3-5-sonnet-20240620", "Claude 3.5 Sonnet"),
        AiModel("claude-3-haiku-20240307", "Claude 3 Haiku")
    ),
    apiKeyProvider = apiKeyProvider,
    customBaseUrlProvider = customBaseUrlProvider
)

class MistralProvider(
    apiKeyProvider: suspend () -> String,
    customBaseUrlProvider: suspend () -> String? = { null }
) : OpenAiCompatibleProvider(
    id = "mistral",
    displayName = "Mistral AI",
    defaultBaseUrl = "https://api.mistral.ai/v1",
    defaultModels = listOf(
        AiModel("mistral-large-latest", "Mistral Large"),
        AiModel("open-mixtral-8x22b", "Mixtral 8x22B"),
        AiModel("mistral-small-latest", "Mistral Small")
    ),
    apiKeyProvider = apiKeyProvider,
    customBaseUrlProvider = customBaseUrlProvider
)

class GroqProvider(
    apiKeyProvider: suspend () -> String,
    customBaseUrlProvider: suspend () -> String? = { null }
) : OpenAiCompatibleProvider(
    id = "groq",
    displayName = "Groq",
    defaultBaseUrl = "https://api.groq.com/openai/v1",
    defaultModels = listOf(
        AiModel("llama3-8b-8192", "Llama 3 8B"),
        AiModel("llama3-70b-8192", "Llama 3 70B"),
        AiModel("mixtral-8x7b-32768", "Mixtral 8x7B")
    ),
    apiKeyProvider = apiKeyProvider,
    customBaseUrlProvider = customBaseUrlProvider
)

class DeepSeekProvider(
    apiKeyProvider: suspend () -> String,
    customBaseUrlProvider: suspend () -> String? = { null }
) : OpenAiCompatibleProvider(
    id = "deepseek",
    displayName = "DeepSeek",
    defaultBaseUrl = "https://api.deepseek.com/v1",
    defaultModels = listOf(
        AiModel("deepseek-chat", "DeepSeek Chat"),
        AiModel("deepseek-coder", "DeepSeek Coder")
    ),
    apiKeyProvider = apiKeyProvider,
    customBaseUrlProvider = customBaseUrlProvider
)

class OllamaProvider(
    apiKeyProvider: suspend () -> String = { "" },
    customBaseUrlProvider: suspend () -> String? = { null }
) : OpenAiCompatibleProvider(
    id = "ollama",
    displayName = "Ollama Local",
    defaultBaseUrl = "http://localhost:11434/v1",
    defaultModels = listOf(
        AiModel("llama3", "Llama 3"),
        AiModel("mistral", "Mistral"),
        AiModel("codellama", "Code Llama")
    ),
    apiKeyProvider = apiKeyProvider,
    customBaseUrlProvider = customBaseUrlProvider
)

class LocalHttpProvider(
    apiKeyProvider: suspend () -> String = { "" },
    customBaseUrlProvider: suspend () -> String? = { null }
) : OpenAiCompatibleProvider(
    id = "local_http",
    displayName = "Local HTTP",
    defaultBaseUrl = "http://localhost:8080/v1",
    defaultModels = listOf(
        AiModel("local-model", "Local Model")
    ),
    apiKeyProvider = apiKeyProvider,
    customBaseUrlProvider = customBaseUrlProvider
)

class CustomOpenAiProvider(
    apiKeyProvider: suspend () -> String,
    customBaseUrlProvider: suspend () -> String? = { null }
) : OpenAiCompatibleProvider(
    id = "custom",
    displayName = "Custom OpenAI-Compatible",
    defaultBaseUrl = "https://api.custom.com/v1",
    defaultModels = listOf(
        AiModel("custom-model", "Custom Model")
    ),
    apiKeyProvider = apiKeyProvider,
    customBaseUrlProvider = customBaseUrlProvider
)
