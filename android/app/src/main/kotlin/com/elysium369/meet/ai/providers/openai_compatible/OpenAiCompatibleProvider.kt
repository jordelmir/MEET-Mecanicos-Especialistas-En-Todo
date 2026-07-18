package com.elysium369.meet.ai.providers.openai_compatible

import android.util.Log
import com.elysium369.meet.ai.data.SecretRedactor
import com.elysium369.meet.ai.domain.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.plugins.timeout
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.pow
import kotlin.random.Random

open class OpenAiCompatibleProvider(
    override val id: String,
    override val displayName: String,
    override val defaultBaseUrl: String,
    override val defaultModels: List<AiModel>,
    protected val apiKeyProvider: suspend () -> String,
    protected val customBaseUrlProvider: suspend () -> String? = { null }
) : AiProvider {

    override val capabilities: Set<AiCapability> = setOf(
        AiCapability.CHAT,
        AiCapability.STREAMING
    )

    private suspend fun getEffectiveBaseUrl(): String {
        return customBaseUrlProvider() ?: defaultBaseUrl
    }

    private suspend fun <T> executeWithRetry(
        action: suspend () -> HttpResponse,
        processResponse: suspend (HttpResponse) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        var currentAttempt = 0
        val maxAttempts = 3
        val baseDelayMs = 1000.0

        while (true) {
            currentAttempt++
            val responseResult = runCatching { action() }
            val response = responseResult.getOrNull()

            if (responseResult.isFailure) {
                val exception = responseResult.exceptionOrNull()!!
                if (currentAttempt >= maxAttempts || exception is java.util.concurrent.CancellationException) {
                    return@withContext Result.failure(
                        AiError.Unknown("Error de red o conexión: ${exception.message}", exception)
                    )
                }
                val delayTime = (baseDelayMs * 2.0.pow(currentAttempt) + Random.nextInt(100)).toLong()
                delay(delayTime)
                continue
            }

            val statusCode = response!!.status.value
            if (statusCode in 200..299) {
                return@withContext runCatching { processResponse(response) }
            }

            val errorBody = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            val redactedError = SecretRedactor.redact(errorBody)

            val error = when (statusCode) {
                401 -> AiError.InvalidApiKey
                403 -> AiError.HttpFailure(403, redactedError)
                429 -> AiError.RateLimited(null)
                in 500..599 -> AiError.HttpFailure(statusCode, redactedError)
                else -> AiError.HttpFailure(statusCode, redactedError)
            }

            val shouldRetry = statusCode == 429 || statusCode in 500..599
            if (shouldRetry && currentAttempt < maxAttempts) {
                val delayTime = (baseDelayMs * 2.0.pow(currentAttempt) + Random.nextInt(100)).toLong()
                delay(delayTime)
                continue
            } else {
                return@withContext Result.failure(error)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        Result.failure(AiError.Unknown("Error inesperado en retry loop", null))
    }

    override suspend fun complete(request: AiRequest): Result<AiResponse> {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty() && id != "local") {
            return Result.failure(AiError.MissingApiKey)
        }

        val baseUrl = getEffectiveBaseUrl()
        val endpoint = "$baseUrl/chat/completions"

        val bodyObj = OpenAiCompatibleChatRequestDto(
            model = request.model,
            messages = request.messages.map { OpenAiCompatibleMessageDto(it.role.toString(), it.content) },
            temperature = request.temperature,
            max_tokens = request.maxTokens,
            stream = false,
            response_format = if (request.jsonMode) OpenAiCompatibleChatRequestDto.ResponseFormat("json_object") else null
        )

        return executeWithRetry(
            action = {
                OpenAiCompatibleClient.aiHttpClient.post(endpoint) {
                    if (apiKey.isNotEmpty()) {
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                    }
                    setBody(bodyObj)
                    timeout {
                        requestTimeoutMillis = request.timeoutMs
                    }
                }
            },
            processResponse = { response ->
                val bodyText = response.bodyAsText()
                val responseDto = OpenAiCompatibleClient.json.decodeFromString<OpenAiCompatibleChatResponseDto>(bodyText)
                val responseText = responseDto.choices.firstOrNull()?.message?.content
                    ?: throw AiError.MalformedResponse(SecretRedactor.redact(bodyText))
                
                AiResponse(
                    text = responseText,
                    usage = responseDto.usage?.let {
                        AiResponse.Usage(
                            promptTokens = it.prompt_tokens ?: 0,
                            completionTokens = it.completion_tokens ?: 0,
                            totalTokens = it.total_tokens ?: 0
                        )
                    },
                    providerId = id,
                    model = request.model
                )
            }
        )
    }

    override suspend fun stream(
        request: AiRequest,
        onToken: suspend (String) -> Unit
    ): Result<AiResponse> {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty() && id != "local") {
            return Result.failure(AiError.MissingApiKey)
        }

        val baseUrl = getEffectiveBaseUrl()
        val endpoint = "$baseUrl/chat/completions"

        val bodyObj = OpenAiCompatibleChatRequestDto(
            model = request.model,
            messages = request.messages.map { OpenAiCompatibleMessageDto(it.role.toString(), it.content) },
            temperature = request.temperature,
            max_tokens = request.maxTokens,
            stream = true,
            response_format = if (request.jsonMode) OpenAiCompatibleChatRequestDto.ResponseFormat("json_object") else null
        )

        return withContext(Dispatchers.IO) {
            val responseResult = runCatching {
                OpenAiCompatibleClient.aiHttpClient.post(endpoint) {
                    if (apiKey.isNotEmpty()) {
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                    }
                    setBody(bodyObj)
                    timeout {
                        requestTimeoutMillis = request.timeoutMs
                    }
                }
            }

            if (responseResult.isFailure) {
                return@withContext Result.failure(
                    AiError.NetworkUnavailable
                )
            }

            val response = responseResult.getOrThrow()
            val statusCode = response.status.value

            if (statusCode !in 200..299) {
                val errorBody = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
                val redactedError = SecretRedactor.redact(errorBody)
                val error = when (statusCode) {
                    401 -> AiError.InvalidApiKey
                    403 -> AiError.HttpFailure(403, redactedError)
                    429 -> AiError.RateLimited(null)
                    else -> AiError.HttpFailure(statusCode, redactedError)
                }
                return@withContext Result.failure(error)
            }

            val channel = response.bodyAsChannel()
            val fullText = StringBuilder()

            try {
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    val token = SseStreamParser.parseLine(line)
                    if (token != null) {
                        fullText.append(token)
                        onToken(token)
                    }
                }
            } catch (e: Exception) {
                return@withContext Result.failure(AiError.MalformedResponse(e.message))
            }

            Result.success(
                AiResponse(
                    text = fullText.toString(),
                    usage = null,
                    providerId = id,
                    model = request.model
                )
            )
        }
    }

    override suspend fun testConnection(config: AiProviderConfig): Result<Unit> {
        val apiKey = apiKeyProvider().trim()
        val endpoint = "${config.baseUrl}/chat/completions"

        val bodyObj = OpenAiCompatibleChatRequestDto(
            model = config.model,
            messages = listOf(
                OpenAiCompatibleMessageDto("user", "ping")
            ),
            max_tokens = 5,
            stream = false
        )

        return executeWithRetry(
            action = {
                OpenAiCompatibleClient.aiHttpClient.post(endpoint) {
                    if (apiKey.isNotEmpty()) {
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                    }
                    setBody(bodyObj)
                    timeout {
                        requestTimeoutMillis = 15_000L
                    }
                }
            },
            processResponse = { response ->
                if (response.status.value !in 200..299) {
                    throw AiError.HttpFailure(response.status.value, "Conexión fallida")
                }
            }
        )
    }
}
