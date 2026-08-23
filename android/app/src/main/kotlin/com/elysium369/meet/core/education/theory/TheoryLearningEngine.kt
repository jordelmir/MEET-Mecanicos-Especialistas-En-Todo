package com.elysium369.meet.core.education.theory

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

enum class ReviewGrade { AGAIN, HARD, GOOD, EASY }

data class TheoryQuestionMemory(
    val questionId: String,
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val ease: Double = 2.5,
    val dueEpochDay: Long = 0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
)

data class TheoryProgressSnapshot(
    val track: TheoryLicenseTrack = TheoryLicenseTrack.AUTOMOBILE,
    val memories: Map<String, TheoryQuestionMemory> = emptyMap(),
    val bestExamScore: Map<TheoryLicenseTrack, Int> = emptyMap(),
    val completedLessons: Set<String> = emptySet(),
    val totalAnswered: Int = 0,
    val totalCorrect: Int = 0,
) {
    val accuracyPercent: Int
        get() = if (totalAnswered == 0) 0 else (totalCorrect * 100.0 / totalAnswered).roundToInt()
}

data class TheoryExamResult(
    val score: Int,
    val correct: Int,
    val total: Int,
    val passed: Boolean,
    val weakestTopics: List<TheoryTopic>,
)

object TheoryLearningEngine {
    fun schedule(
        current: TheoryQuestionMemory,
        grade: ReviewGrade,
        todayEpochDay: Long,
    ): TheoryQuestionMemory {
        val correct = grade != ReviewGrade.AGAIN
        if (!correct) {
            return current.copy(
                repetitions = 0,
                intervalDays = 1,
                ease = max(1.3, current.ease - 0.2),
                dueEpochDay = todayEpochDay + 1,
                incorrectCount = current.incorrectCount + 1,
            )
        }

        val nextRepetitions = current.repetitions + 1
        val baseInterval = when (nextRepetitions) {
            1 -> 1
            2 -> 3
            else -> max(4, (max(1, current.intervalDays) * current.ease).roundToInt())
        }
        val multiplier = when (grade) {
            ReviewGrade.HARD -> 0.75
            ReviewGrade.GOOD -> 1.0
            ReviewGrade.EASY -> 1.35
            ReviewGrade.AGAIN -> error("handled above")
        }
        val nextInterval = max(1, (baseInterval * multiplier).roundToInt())
        val easeDelta = when (grade) {
            ReviewGrade.HARD -> -0.15
            ReviewGrade.GOOD -> 0.0
            ReviewGrade.EASY -> 0.15
            ReviewGrade.AGAIN -> 0.0
        }
        return current.copy(
            repetitions = nextRepetitions,
            intervalDays = nextInterval,
            ease = (current.ease + easeDelta).coerceIn(1.3, 3.0),
            dueEpochDay = todayEpochDay + nextInterval,
            correctCount = current.correctCount + 1,
        )
    }

    fun practiceSet(
        track: TheoryLicenseTrack,
        memories: Map<String, TheoryQuestionMemory>,
        todayEpochDay: Long,
        size: Int = 10,
        seed: Int = (todayEpochDay xor track.ordinal.toLong()).toInt(),
    ): List<TheoryQuestion> {
        val questions = TheoryExamKnowledge.questionsFor(track)
        require(size in 1..questions.size) { "Practice size must fit the available bank" }
        val random = Random(seed)
        return questions
            .sortedWith(
                compareBy<TheoryQuestion> {
                    val memory = memories[it.id]
                    when {
                        memory == null -> 1
                        memory.dueEpochDay <= todayEpochDay -> 0
                        else -> 2
                    }
                }.thenByDescending {
                    memories[it.id]?.incorrectCount ?: 0
                }.thenBy { random.nextInt() },
            )
            .take(size)
    }

    fun exam(track: TheoryLicenseTrack, seed: Int): List<TheoryQuestion> {
        val available = TheoryExamKnowledge.questionsFor(track)
        require(available.size >= TheoryExamKnowledge.SIMULATION_SIZE) {
            "The ${track.name} bank cannot produce a full simulation"
        }
        return available.shuffled(Random(seed)).take(TheoryExamKnowledge.SIMULATION_SIZE)
    }

    fun evaluate(
        questions: List<TheoryQuestion>,
        answers: Map<String, Int>,
    ): TheoryExamResult {
        require(questions.isNotEmpty()) { "Cannot evaluate an empty exam" }
        val correct = questions.count { answers[it.id] == it.correctIndex }
        val score = (correct * 100.0 / questions.size).roundToInt()
        val topicAccuracy = questions.groupBy { it.topic }.mapValues { (_, topicQuestions) ->
            topicQuestions.count { answers[it.id] == it.correctIndex }.toDouble() / topicQuestions.size
        }
        return TheoryExamResult(
            score = score,
            correct = correct,
            total = questions.size,
            passed = score >= TheoryExamKnowledge.PASSING_SCORE,
            weakestTopics = topicAccuracy.entries.sortedBy { it.value }.take(3).map { it.key },
        )
    }

    fun masteryPercent(
        track: TheoryLicenseTrack,
        memories: Map<String, TheoryQuestionMemory>,
    ): Int {
        val bank = TheoryExamKnowledge.questionsFor(track)
        if (bank.isEmpty()) return 0
        val mastered = bank.count { question ->
            val memory = memories[question.id]
            memory != null && memory.correctCount >= 2 && memory.correctCount > memory.incorrectCount
        }
        return (mastered * 100.0 / bank.size).roundToInt()
    }
}
