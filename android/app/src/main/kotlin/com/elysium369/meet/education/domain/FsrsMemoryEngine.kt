package com.elysium369.meet.education.domain

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

enum class ReviewRating(val value: Int) {
    AGAIN(1), // Falló por completo (reinicia o degrada estabilidad)
    HARD(2),  // Respondió pero con alta vacilación / pistas
    GOOD(3),  // Respuesta correcta dentro del tiempo esperado
    EASY(4)   // Respuesta inmediata y con alta confianza
}

data class FsrsCardState(
    val conceptId: String,
    val stabilityDays: Double = 1.0,     // Días hasta que la probabilidad de recuerdo caiga al 90%
    val difficulty: Double = 5.0,        // Dificultad intrínseca (1.0 muy fácil, 10.0 muy difícil)
    val lastReviewTimestampMs: Long = System.currentTimeMillis(),
    val repetitionCount: Int = 0,
    val lapsesCount: Int = 0
)

/**
 * Motor Neuro-Adaptativo FSRS (Free Spaced Repetition Scheduler).
 * Modela la curva biológica del olvido humano (Ebbinghaus / DSR Model).
 * Predice con exactitud la retención sináptica R(t, S) = (1 + factor * t / S)^(-alpha).
 */
object FsrsMemoryEngine {

    private const val DECAY_ALPHA = 0.5
    private const val FACTOR = 19.0 / 81.0 // Asegura que R(S, S) = 0.90
    private const val TARGET_RETENTION = 0.90 // Re-consolidación sináptica óptima

    /**
     * Calcula la retención estimada R en el momento actual (0.0 a 1.0).
     */
    fun computeRetrievability(card: FsrsCardState, currentTimeMs: Long = System.currentTimeMillis()): Double {
        val elapsedDays = ((currentTimeMs - card.lastReviewTimestampMs).toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)).coerceAtLeast(0.0)
        if (card.stabilityDays <= 0.001) return 0.0
        return (1.0 + FACTOR * (elapsedDays / card.stabilityDays)).pow(-DECAY_ALPHA).coerceIn(0.0, 1.0)
    }

    /**
     * Evalúa si un concepto requiere repaso inmediato (retención cayó por debajo del umbral del 90%).
     */
    fun isDueForReview(card: FsrsCardState, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return computeRetrievability(card, currentTimeMs) < TARGET_RETENTION
    }

    /**
     * Actualiza el estado de memoria tras una interacción de aprendizaje.
     */
    fun review(
        card: FsrsCardState,
        rating: ReviewRating,
        reviewTimeMs: Long = System.currentTimeMillis()
    ): FsrsCardState {
        val currentRetrievability = computeRetrievability(card, reviewTimeMs)

        return when (rating) {
            ReviewRating.AGAIN -> {
                // Falló: se incrementa lapsus y la estabilidad se reinicia a nivel basal
                val newDiff = (card.difficulty + 0.8).coerceIn(1.0, 10.0)
                val newStab = (card.stabilityDays * 0.25).coerceAtLeast(0.4)
                card.copy(
                    stabilityDays = newStab,
                    difficulty = newDiff,
                    lastReviewTimestampMs = reviewTimeMs,
                    repetitionCount = card.repetitionCount + 1,
                    lapsesCount = card.lapsesCount + 1
                )
            }
            ReviewRating.HARD -> {
                val newDiff = (card.difficulty + 0.3).coerceIn(1.0, 10.0)
                val newStab = card.stabilityDays * (1.0 + 0.8 * (1.0 - currentRetrievability))
                card.copy(
                    stabilityDays = newStab.coerceAtLeast(0.8),
                    difficulty = newDiff,
                    lastReviewTimestampMs = reviewTimeMs,
                    repetitionCount = card.repetitionCount + 1
                )
            }
            ReviewRating.GOOD -> {
                val newDiff = (card.difficulty - 0.1).coerceIn(1.0, 10.0)
                val stabilityMultiplier = 1.0 + (11.0 - newDiff) * 0.25 * (1.0 + (1.0 - currentRetrievability))
                val newStab = card.stabilityDays * stabilityMultiplier
                card.copy(
                    stabilityDays = newStab.coerceAtLeast(1.5),
                    difficulty = newDiff,
                    lastReviewTimestampMs = reviewTimeMs,
                    repetitionCount = card.repetitionCount + 1
                )
            }
            ReviewRating.EASY -> {
                val newDiff = (card.difficulty - 0.5).coerceIn(1.0, 10.0)
                val stabilityMultiplier = 1.0 + (11.0 - newDiff) * 0.5 * (1.0 + (1.0 - currentRetrievability))
                val newStab = card.stabilityDays * stabilityMultiplier * 1.3
                card.copy(
                    stabilityDays = newStab.coerceAtLeast(3.0),
                    difficulty = newDiff,
                    lastReviewTimestampMs = reviewTimeMs,
                    repetitionCount = card.repetitionCount + 1
                )
            }
        }
    }

    /**
     * Calcula los días restantes recomendados hasta la próxima sesión de re-consolidación.
     */
    fun getRecommendedIntervalDays(card: FsrsCardState): Double {
        // Para R = 0.90, intervalo es exactamente la estabilidad S
        return card.stabilityDays
    }
}
