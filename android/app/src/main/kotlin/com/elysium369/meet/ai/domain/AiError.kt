package com.elysium369.meet.ai.domain

sealed class AiError : Throwable() {
    data object MissingApiKey : AiError()
    data object InvalidApiKey : AiError()
    data object NetworkUnavailable : AiError()
    data class HttpFailure(val code: Int, val safeBody: String?) : AiError()
    data class RateLimited(val retryAfterMs: Long?) : AiError()
    data class ProviderUnavailable(val reason: String) : AiError()
    data class MalformedResponse(val safeBody: String?) : AiError()
    data class Timeout(val timeoutMs: Long) : AiError()
    data class PolicyBlocked(val reason: String) : AiError()
    data class JsonSchemaViolation(val reason: String) : AiError()
    data class Unknown(val reason: String, val causeThrowable: Throwable?) : AiError()

    override val message: String?
        get() = when (this) {
            is MissingApiKey -> "API key no configurada."
            is InvalidApiKey -> "API key inválida o revocada. Verifica proveedor y clave."
            is NetworkUnavailable -> "Sin internet. Usa modo offline limitado o conecta red."
            is HttpFailure -> when (code) {
                401 -> "API key inválida o revocada. Verifica proveedor y clave."
                403 -> "Tu cuenta no tiene permiso para este modelo o endpoint."
                429 -> "Límite de uso alcanzado. Espera, cambia de proveedor o usa backend PRO."
                else -> "Error HTTP $code: ${safeBody ?: "Sin detalles"}"
            }
            is RateLimited -> "Límite de uso alcanzado. Espera, cambia de proveedor o usa backend PRO."
            is ProviderUnavailable -> "Proveedor no disponible: $reason"
            is MalformedResponse -> "Respuesta malformada: ${safeBody ?: "Sin detalles"}"
            is Timeout -> "El proveedor tardó demasiado. Baja tokens o revisa conexión."
            is PolicyBlocked -> "Operación bloqueada por políticas de seguridad: $reason"
            is JsonSchemaViolation -> "La respuesta no cumple con el esquema JSON: $reason"
            is Unknown -> "Error desconocido: $reason"
        }
}
