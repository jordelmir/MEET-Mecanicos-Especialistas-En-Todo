package com.elysium369.meet.ai.data

import com.elysium369.meet.ai.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val registry: AiProviderRegistry,
    private val usageTracker: AiUsageTracker,
    private val promptStore: AiPromptStore
) : AiRepository {

    override suspend fun complete(request: AiRequest): Result<AiResponse> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (request.context.safetyMode) {
            val safetyCheck = validateSafety(request.messages)
            if (safetyCheck.isFailure) {
                val error = safetyCheck.exceptionOrNull()!!
                usageTracker.trackError(request, error, 0L)
                return@withContext Result.failure(error)
            }
        }

        val provider = registry.getProvider(request.providerId)
            ?: return@withContext Result.failure(AiError.ProviderUnavailable("Proveedor ${request.providerId} no encontrado"))

        val messagesWithSystem = injectSystemPromptIfNeeded(request)
        val updatedRequest = request.copy(messages = messagesWithSystem)

        val result = provider.complete(updatedRequest)
        val latency = System.currentTimeMillis() - startTime

        result.fold(
            onSuccess = { response ->
                usageTracker.trackUsage(updatedRequest, response, latency)
                Result.success(response)
            },
            onFailure = { error ->
                usageTracker.trackError(updatedRequest, error, latency)
                Result.failure(error)
            }
        )
    }

    override suspend fun stream(
        request: AiRequest,
        onToken: suspend (String) -> Unit
    ): Result<AiResponse> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (request.context.safetyMode) {
            val safetyCheck = validateSafety(request.messages)
            if (safetyCheck.isFailure) {
                val error = safetyCheck.exceptionOrNull()!!
                usageTracker.trackError(request, error, 0L)
                return@withContext Result.failure(error)
            }
        }

        val provider = registry.getProvider(request.providerId)
            ?: return@withContext Result.failure(AiError.ProviderUnavailable("Proveedor ${request.providerId} no encontrado"))

        val messagesWithSystem = injectSystemPromptIfNeeded(request)
        val updatedRequest = request.copy(messages = messagesWithSystem)

        val result = provider.stream(updatedRequest, onToken)
        val latency = System.currentTimeMillis() - startTime

        result.fold(
            onSuccess = { response ->
                usageTracker.trackUsage(updatedRequest, response, latency)
                Result.success(response)
            },
            onFailure = { error ->
                usageTracker.trackError(updatedRequest, error, latency)
                Result.failure(error)
            }
        )
    }

    private fun injectSystemPromptIfNeeded(request: AiRequest): List<AiMessage> {
        val hasSystem = request.messages.any { it.role == AiRole.SYSTEM }
        return if (!hasSystem) {
            val systemPromptContent = promptStore.getSystemPrompt(request.feature)
            listOf(AiMessage(AiRole.SYSTEM, systemPromptContent)) + request.messages
        } else {
            request.messages
        }
    }

    private fun validateSafety(messages: List<AiMessage>): Result<Unit> {
        val destructivePatterns = listOf(
            Regex("""(?i)\b(rm\s+-rf|mkfs|dd\s+if|reboot|shutdown|init\s+0)\b"""),
            Regex("""(?i)\b(format\s+[a-zA-Z]:)\b""")
        )

        messages.forEach { msg ->
            if (msg.role == AiRole.USER) {
                destructivePatterns.forEach { pattern ->
                    if (pattern.containsMatchIn(msg.content)) {
                        return Result.failure(AiError.PolicyBlocked("Se detectó un comando potencialmente destructivo: ${pattern.find(msg.content)?.value}"))
                    }
                }
            }
        }
        return Result.success(Unit)
    }
}
