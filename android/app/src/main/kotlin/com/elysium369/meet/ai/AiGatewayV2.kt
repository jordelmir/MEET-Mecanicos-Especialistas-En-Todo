package com.elysium369.meet.ai

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

enum class AiTaskClass(
    val defaultDeadlineMs: Long,
    val maxTokenBudget: Int,
    val allowsCloudFallback: Boolean,
) {
    LEGAL_TRIAGE(defaultDeadlineMs = 2500L, maxTokenBudget = 800, allowsCloudFallback = true),
    DTC_SUMMARY(defaultDeadlineMs = 1500L, maxTokenBudget = 500, allowsCloudFallback = true),
    DEEP_REPAIR_REASONING(defaultDeadlineMs = 12000L, maxTokenBudget = 3000, allowsCloudFallback = true),
    VOICE_INTENT(defaultDeadlineMs = 800L, maxTokenBudget = 100, allowsCloudFallback = false),
    CATALOG_MAPPING(defaultDeadlineMs = 3000L, maxTokenBudget = 1000, allowsCloudFallback = true),
}

enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN,
}

data class CircuitBreakerMetrics(
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastFailureTimestampMs: Long = 0L,
    val state: CircuitState = CircuitState.CLOSED,
)

sealed interface AiExecutionOutcome {
    data class Success(val responseText: String, val latencyMs: Long, val providerUsed: String) : AiExecutionOutcome
    data class CircuitOpen(val reason: String) : AiExecutionOutcome
    data class DeadlineExceeded(val timeoutMs: Long) : AiExecutionOutcome
    data class ProviderFailure(val errorCode: Int, val message: String) : AiExecutionOutcome
}

/**
 * AiGatewayV2 — High-resilience AI execution plane with bulkheads and task-specific circuit breakers.
 * Eliminates universal 3-second timeouts and isolates providers against cascading failures.
 */
object AiGatewayV2 {

    private const val TAG = "AiGatewayV2"
    private val circuitStates = ConcurrentHashMap<AiTaskClass, CircuitBreakerMetrics>()
    private const val FAILURE_THRESHOLD = 3
    private const val COOLDOWN_PERIOD_MS = 15_000L

    fun getMetrics(taskClass: AiTaskClass): CircuitBreakerMetrics {
        return circuitStates.getOrPut(taskClass) { CircuitBreakerMetrics() }
    }

    fun canExecute(taskClass: AiTaskClass): Boolean {
        val metrics = getMetrics(taskClass)
        val now = System.currentTimeMillis()

        return when (metrics.state) {
            CircuitState.CLOSED -> true
            CircuitState.OPEN -> {
                if (now - metrics.lastFailureTimestampMs > COOLDOWN_PERIOD_MS) {
                    circuitStates[taskClass] = metrics.copy(state = CircuitState.HALF_OPEN)
                    true
                } else {
                    false
                }
            }
            CircuitState.HALF_OPEN -> true
        }
    }

    fun recordSuccess(taskClass: AiTaskClass, latencyMs: Long) {
        val current = getMetrics(taskClass)
        circuitStates[taskClass] = current.copy(
            totalRequests = current.totalRequests + 1,
            successfulRequests = current.successfulRequests + 1,
            consecutiveFailures = 0,
            state = CircuitState.CLOSED,
        )
        Log.d(TAG, "Task $taskClass completed successfully in ${latencyMs}ms")
    }

    fun recordFailure(taskClass: AiTaskClass, isTimeoutOr5xx: Boolean) {
        val current = getMetrics(taskClass)
        val nextFailures = current.consecutiveFailures + 1
        val now = System.currentTimeMillis()

        val nextState = if (nextFailures >= FAILURE_THRESHOLD && isTimeoutOr5xx) {
            Log.w(TAG, "Circuit breaker OPENED for task $taskClass after $nextFailures consecutive failures")
            CircuitState.OPEN
        } else {
            current.state
        }

        circuitStates[taskClass] = current.copy(
            totalRequests = current.totalRequests + 1,
            consecutiveFailures = nextFailures,
            lastFailureTimestampMs = now,
            state = nextState,
        )
    }

    fun resetAll() {
        circuitStates.clear()
    }
}
