package com.elysium369.meet.core.error

/**
 * MEET / ELYSIUM Domain Failure Taxonomy.
 * Enforces Master Order Section 46: Zero raw Exception.message exposure.
 * Distinguishes retryable dependencies from terminal failures.
 */
sealed interface DomainFailure {
    val category: String

    data object Unauthenticated : DomainFailure {
        override val category: String = "AUTHENTICATION"
    }

    data object Forbidden : DomainFailure {
        override val category: String = "AUTHORIZATION"
    }

    data class Validation(
        val field: String,
        val reason: String
    ) : DomainFailure {
        override val category: String = "VALIDATION"
    }

    data class Conflict(
        val code: String,
        val details: String? = null
    ) : DomainFailure {
        override val category: String = "CONFLICT"
    }

    data class RateLimit(
        val retryAfterSeconds: Long? = null
    ) : DomainFailure {
        override val category: String = "RATE_LIMIT"
    }

    data class RetryableDependency(
        val dependency: String,
        val correlationId: String? = null
    ) : DomainFailure {
        override val category: String = "DEPENDENCY_RETRYABLE"
    }

    data class TerminalDependency(
        val dependency: String,
        val code: String,
        val correlationId: String? = null
    ) : DomainFailure {
        override val category: String = "DEPENDENCY_TERMINAL"
    }

    data class Protocol(
        val code: String,
        val message: String
    ) : DomainFailure {
        override val category: String = "PROTOCOL"
    }

    data class Integrity(
        val violation: String,
        val correlationId: String? = null
    ) : DomainFailure {
        override val category: String = "INTEGRITY"
    }

    data class Internal(
        val correlationId: String
    ) : DomainFailure {
        override val category: String = "INTERNAL"
    }
}
