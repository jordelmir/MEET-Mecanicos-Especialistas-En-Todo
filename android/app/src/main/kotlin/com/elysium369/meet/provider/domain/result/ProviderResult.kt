package com.elysium369.meet.provider.domain.result

enum class ProviderErrorCode {
    UNAUTHORIZED,
    NOT_A_PROVIDER,
    MISSING_DOCUMENTS,
    SAFETY_SUSPENSION,
    ALREADY_ONLINE,
    ALREADY_OFFLINE,
    ACTIVE_WORK_IN_PROGRESS,
    INVALID_ARGUMENT,
    NETWORK_ERROR,
    PROTOCOL_VIOLATION,
    UNKNOWN_ERROR;

    companion object {
        fun fromString(value: String): ProviderErrorCode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: UNKNOWN_ERROR
        }
    }
}

sealed interface ProviderResult<out T> {
    data class Success<T>(val data: T) : ProviderResult<T>
    data class Failure(
        val code: ProviderErrorCode,
        val message: String? = null,
        val cause: Throwable? = null,
    ) : ProviderResult<Nothing>
}
