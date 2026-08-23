package com.elysium369.meet.core.education.theory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TheoryExamKnowledgeTest {
    @Test
    fun `each 2026 track has enough original questions for a full simulation`() {
        TheoryLicenseTrack.entries.forEach { track ->
            val questions = TheoryExamKnowledge.questionsFor(track)
            assertTrue("${track.name} only has ${questions.size} questions", questions.size >= 40)
            assertEquals(questions.size, questions.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `all content is traceable to declared sources`() {
        val sourceIds = TheoryExamKnowledge.sources.map { it.id }.toSet()
        assertTrue(TheoryExamKnowledge.questions.all { it.sourceIds.isNotEmpty() && it.sourceIds.all(sourceIds::contains) })
        assertTrue(TheoryExamKnowledge.lessons.all { it.sourceIds.isNotEmpty() && it.sourceIds.all(sourceIds::contains) })
    }

    @Test
    fun `official and independent resources are never conflated`() {
        val independent = TheoryExamKnowledge.sources.filter { it.kind == TheorySourceKind.INDEPENDENT_PRACTICE }
        assertTrue(independent.isNotEmpty())
        assertTrue(independent.all { "independiente" in it.authority.lowercase() })
        assertFalse(independent.any { it.authority.contains("MOPT") || it.authority.contains("COSEVI") })
    }

    @Test
    fun `exam uses forty questions and official published threshold`() {
        val exam = TheoryLearningEngine.exam(TheoryLicenseTrack.AUTOMOBILE, seed = 42)
        assertEquals(40, exam.size)
        val answers = exam.associate { it.id to it.correctIndex }
        val result = TheoryLearningEngine.evaluate(exam, answers)
        assertEquals(100, result.score)
        assertTrue(result.passed)

        val exactlyThirtyTwo = exam.take(32).associate { it.id to it.correctIndex }
        val threshold = TheoryLearningEngine.evaluate(exam, exactlyThirtyTwo)
        assertEquals(80, threshold.score)
        assertTrue(threshold.passed)
    }

    @Test
    fun `incorrect recall is due tomorrow and lowers ease without going below floor`() {
        var memory = TheoryQuestionMemory(questionId = "c01", ease = 1.35, repetitions = 5, intervalDays = 20)
        memory = TheoryLearningEngine.schedule(memory, ReviewGrade.AGAIN, todayEpochDay = 100)
        assertEquals(0, memory.repetitions)
        assertEquals(101, memory.dueEpochDay)
        assertEquals(1.3, memory.ease, 0.0001)
        assertEquals(1, memory.incorrectCount)
    }

    @Test
    fun `practice prioritizes due and lapsed questions`() {
        val bank = TheoryExamKnowledge.questionsFor(TheoryLicenseTrack.AUTOMOBILE)
        val due = bank.first()
        val future = bank.drop(1).first()
        val memories = mapOf(
            due.id to TheoryQuestionMemory(due.id, dueEpochDay = 10, incorrectCount = 3),
            future.id to TheoryQuestionMemory(future.id, dueEpochDay = 999),
        )
        val practice = TheoryLearningEngine.practiceSet(
            TheoryLicenseTrack.AUTOMOBILE,
            memories,
            todayEpochDay = 100,
            size = 1,
            seed = 1,
        )
        assertEquals(due.id, practice.single().id)
    }
}
