package com.elysium369.meet.core.remote

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponseEnvelope<T>(
    val ok: Boolean,
    val data: T? = null,
    val error: ApiErrorDetail? = null,
    val correlationId: String? = null,
)

@Serializable
data class ApiErrorDetail(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val details: Map<String, String> = emptyMap(),
)
