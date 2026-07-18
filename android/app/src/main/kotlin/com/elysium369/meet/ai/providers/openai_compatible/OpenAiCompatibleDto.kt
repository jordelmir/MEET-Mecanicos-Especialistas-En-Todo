package com.elysium369.meet.ai.providers.openai_compatible

import kotlinx.serialization.Serializable

@Serializable
data class OpenAiCompatibleChatRequestDto(
    val model: String,
    val messages: List<OpenAiCompatibleMessageDto>,
    val temperature: Double? = null,
    val max_tokens: Int? = null,
    val stream: Boolean = false,
    val response_format: ResponseFormat? = null
) {
    @Serializable
    data class ResponseFormat(
        val type: String
    )
}

@Serializable
data class OpenAiCompatibleChatResponseDto(
    val id: String? = null,
    val model: String? = null,
    val choices: List<OpenAiCompatibleChoiceDto> = emptyList(),
    val usage: OpenAiCompatibleUsageDto? = null
)

@Serializable
data class OpenAiCompatibleChoiceDto(
    val index: Int? = null,
    val message: OpenAiCompatibleMessageDto? = null,
    val delta: OpenAiCompatibleDeltaDto? = null,
    val finish_reason: String? = null
)

@Serializable
data class OpenAiCompatibleMessageDto(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class OpenAiCompatibleDeltaDto(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class OpenAiCompatibleUsageDto(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null
)
