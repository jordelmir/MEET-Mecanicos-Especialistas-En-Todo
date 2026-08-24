package com.elysium369.meet.core.humanity.engine

import kotlin.math.max
import kotlin.math.roundToInt

enum class LearningReviewGrade {
    AGAIN,
    HARD,
    GOOD,
    EASY,
}

data class ItemMemoryState(
    val itemId: String,
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val dueEpochDay: Long = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
)

data class AssessmentScore(
    val totalItems: Int,
    val correctItems: Int,
    val scorePercent: Int,
    val passed: Boolean,
    val passingThresholdPercent: Int = 80,
)

object SpacedRepetitionScheduler {

    fun schedule(
        current: ItemMemoryState,
        grade: LearningReviewGrade,
        todayEpochDay: Long,
    ): ItemMemoryState {
        val isCorrect = grade != LearningReviewGrade.AGAIN
        if (!isCorrect) {
            return current.copy(
                repetitions = 0,
                intervalDays = 1,
                easeFactor = max(1.3, current.easeFactor - 0.2),
                dueEpochDay = todayEpochDay + 1,
                incorrectCount = current.incorrectCount + 1,
            )
        }

        val nextRepetitions = current.repetitions + 1
        val baseInterval = when (nextRepetitions) {
            1 -> 1
            2 -> 3
            else -> max(4, (max(1, current.intervalDays) * current.easeFactor).roundToInt())
        }
        val multiplier = when (grade) {
            LearningReviewGrade.HARD -> 0.75
            LearningReviewGrade.GOOD -> 1.0
            LearningReviewGrade.EASY -> 1.35
            LearningReviewGrade.AGAIN -> 1.0
        }
        val nextInterval = max(1, (baseInterval * multiplier).roundToInt())
        val easeDelta = when (grade) {
            LearningReviewGrade.HARD -> -0.15
            LearningReviewGrade.GOOD -> 0.0
            LearningReviewGrade.EASY -> 0.15
            LearningReviewGrade.AGAIN -> 0.0
        }

        return current.copy(
            repetitions = nextRepetitions,
            intervalDays = nextInterval,
            easeFactor = (current.easeFactor + easeDelta).coerceIn(1.3, 3.0),
            dueEpochDay = todayEpochDay + nextInterval,
            correctCount = current.correctCount + 1,
        )
    }
}
