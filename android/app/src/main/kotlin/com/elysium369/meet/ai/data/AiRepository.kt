package com.elysium369.meet.ai.data

import com.elysium369.meet.ai.domain.AiRequest
import com.elysium369.meet.ai.domain.AiResponse

interface AiRepository {
    suspend fun complete(request: AiRequest): Result<AiResponse>
    suspend fun stream(request: AiRequest, onToken: suspend (String) -> Unit): Result<AiResponse>
}
