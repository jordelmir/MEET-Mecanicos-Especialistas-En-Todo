package com.elysium369.meet.core.agent.laya

import kotlinx.serialization.Serializable

/**
 * Standard protocol specifications for Laya AI / System 1 decision engine.
 * Compatible with `POST /v1/systemone` protocol (Apache 2.0).
 */
@Serializable
data class LayaSystemOneRequest(
    val state: String,
    val questions: Map<String, LayaQuestionDef>,
    val temperature: Double = 0.0,
)

@Serializable
data class LayaQuestionDef(
    val type: String, // "choice", "score", "noul"
    val options: List<String>? = null,
    val levels: Int? = null,
)

@Serializable
data class LayaSystemOneResponse(
    val answers: Map<String, LayaAnswerDef>,
    val latencyMs: Long? = null,
)

@Serializable
data class LayaAnswerDef(
    val value: String? = null,
    val level: Int? = null,
    val pTrue: Double? = null,
    val confidence: Double? = null,
    val probabilities: Map<String, Double>? = null,
)

/**
 * Strongly typed representation of questions asked to Laya.
 */
sealed interface LayaQuestion {
    val name: String

    data class Choice(
        override val name: String,
        val options: List<String>,
    ) : LayaQuestion

    data class Score(
        override val name: String,
        val levels: Int = 5,
    ) : LayaQuestion

    data class Noul(
        override val name: String,
    ) : LayaQuestion
}

/**
 * Strongly typed answers returned by Laya.
 */
sealed interface LayaAnswer {
    data class Choice(
        val value: String,
        val confidence: Double,
        val probabilities: Map<String, Double>,
    ) : LayaAnswer

    data class Score(
        val level: Int,
        val normalizedScore: Double,
    ) : LayaAnswer

    data class Noul(
        val value: Boolean,
        val pTrue: Double,
    ) : LayaAnswer
}

/**
 * Decoded result of a multi-question Laya batch query.
 */
data class LayaDecisionBatch(
    val answers: Map<String, LayaAnswer>,
    val latencyMs: Long,
) {
    fun choice(name: String): LayaAnswer.Choice? = answers[name] as? LayaAnswer.Choice
    fun score(name: String): LayaAnswer.Score? = answers[name] as? LayaAnswer.Score
    fun noul(name: String): LayaAnswer.Noul? = answers[name] as? LayaAnswer.Noul
}
