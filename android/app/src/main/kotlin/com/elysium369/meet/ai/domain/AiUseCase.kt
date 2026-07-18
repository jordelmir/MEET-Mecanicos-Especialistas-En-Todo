package com.elysium369.meet.ai.domain

import com.elysium369.meet.ai.data.AiRepository
import javax.inject.Inject

class AiUseCase @Inject constructor(
    private val repository: AiRepository
) {
    suspend fun complete(request: AiRequest): Result<AiResponse> {
        return repository.complete(request)
    }

    suspend fun stream(request: AiRequest, onToken: suspend (String) -> Unit): Result<AiResponse> {
        return repository.stream(request, onToken)
    }
}
