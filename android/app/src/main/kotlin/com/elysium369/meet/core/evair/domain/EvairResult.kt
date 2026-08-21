package com.elysium369.meet.core.evair.domain

import kotlinx.serialization.Serializable

/**
 * EVAIR typed error model.
 *
 * UI must distinguish these error types and display appropriate messages.
 * Never confuse "AI unavailable" with "vehicle unhealthy".
 */
@Serializable
sealed interface EvairError {
    val message: String

    @Serializable
    data class VehicleDisconnected(
        override val message: String = "Vehículo desconectado",
        val reason: String = "",
    ) : EvairError

    @Serializable
    data class DataStale(
        override val message: String = "Datos obsoletos",
        val ageMs: Long,
        val pid: String? = null,
    ) : EvairError

    @Serializable
    data class ToolTimeout(
        override val message: String = "Timeout de herramienta",
        val tool: String,
        val timeoutMs: Long,
    ) : EvairError

    @Serializable
    data class AgentUnavailable(
        override val message: String = "Agente IA no disponible",
        val reason: String,
    ) : EvairError

    @Serializable
    data class InvalidAgentResponse(
        override val message: String = "Respuesta inválida del agente",
        val reason: String,
        val rawResponse: String? = null,
    ) : EvairError

    @Serializable
    data class SafetyDenied(
        override val message: String = "Operación denegada por política de seguridad",
        val command: String,
        val reason: String,
    ) : EvairError

    @Serializable
    data class InternalError(
        override val message: String = "Error interno de EVAIR",
        val cause: String,
    ) : EvairError
}

/**
 * EvairResult<T> — Typed result wrapper.
 *
 * No null-as-failure. Every failure has a typed error with context.
 */
@Serializable
sealed interface EvairResult<out T> {
    @Serializable
    data class Success<T>(val value: T) : EvairResult<T>

    @Serializable
    data class Failure(val error: EvairError) : EvairResult<Nothing>
}

/** Extension to map over successful results */
inline fun <T, R> EvairResult<T>.map(transform: (T) -> R): EvairResult<R> = when (this) {
    is EvairResult.Success -> EvairResult.Success(transform(value))
    is EvairResult.Failure -> this
}

/** Extension to flatMap over successful results */
inline fun <T, R> EvairResult<T>.flatMap(transform: (T) -> EvairResult<R>): EvairResult<R> = when (this) {
    is EvairResult.Success -> transform(value)
    is EvairResult.Failure -> this
}

/** Extension to get value or null */
fun <T> EvairResult<T>.getOrNull(): T? = when (this) {
    is EvairResult.Success -> value
    is EvairResult.Failure -> null
}

/** Extension to get value or default */
fun <T> EvairResult<T>.getOrDefault(default: T): T = when (this) {
    is EvairResult.Success -> value
    is EvairResult.Failure -> default
}
