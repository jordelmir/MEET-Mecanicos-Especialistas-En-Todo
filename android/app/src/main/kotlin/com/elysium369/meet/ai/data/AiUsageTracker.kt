package com.elysium369.meet.ai.data

import android.util.Log
import com.elysium369.meet.ai.domain.AiRequest
import com.elysium369.meet.ai.domain.AiResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiUsageTracker @Inject constructor() {
    fun trackUsage(request: AiRequest, response: AiResponse, latencyMs: Long) {
        val sanitizedModel = response.model
        val promptTokens = response.usage?.promptTokens ?: 0
        val completionTokens = response.usage?.completionTokens ?: 0
        val totalTokens = response.usage?.totalTokens ?: 0
        
        Log.i("AiUsageTracker", "AI call: feature=${request.feature}, provider=${request.providerId}, model=$sanitizedModel, promptTokens=$promptTokens, completionTokens=$completionTokens, totalTokens=$totalTokens, latency=${latencyMs}ms")
    }

    fun trackError(request: AiRequest, error: Throwable, latencyMs: Long) {
        val sanitizedError = SecretRedactor.redact(error.message ?: "Unknown Error")
        Log.e("AiUsageTracker", "AI call failed: feature=${request.feature}, provider=${request.providerId}, error=$sanitizedError, latency=${latencyMs}ms")
    }
}
