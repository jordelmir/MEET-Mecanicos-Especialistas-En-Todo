package com.elysium369.meet.education

import com.elysium369.meet.education.domain.PisaCognitiveProcess
import com.elysium369.meet.education.domain.PisaDomain
import com.elysium369.meet.education.domain.PisaProficiencyLevel
import com.elysium369.meet.education.engine.PisaAssessmentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PisaAssessmentEngineTest {

    @Test
    fun `pisa score calculation correctly maps low composite to below level 1`() {
        val score = PisaAssessmentEngine.computePisaScore(
            fundamentalMastery = 0.15,
            transferSuccessRate = 0.10,
            multiStepReasoningRate = 0.05,
        )

        val level = PisaProficiencyLevel.fromScore(score)
        assertTrue("Score should be below 358 for low mastery", score < 358)
        assertEquals(PisaProficiencyLevel.BELOW_LEVEL_1, level)
        assertFalse(level.isOecdBaseline)
    }

    @Test
    fun `pisa score calculation correctly maps solid average to level 2 or 3 baseline`() {
        // Average 50% performance across tasks
        val score = PisaAssessmentEngine.computePisaScore(
            fundamentalMastery = 0.60,
            transferSuccessRate = 0.50,
            multiStepReasoningRate = 0.45,
        )

        val level = PisaProficiencyLevel.fromScore(score)
        assertTrue("Score should be at or near OECD average 500", score in 482..606)
        assertTrue("Should meet OECD Level 2 or higher", level.levelNumber >= 3)
    }

    @Test
    fun `pisa score calculation correctly maps world-class mastery to level 6`() {
        val score = PisaAssessmentEngine.computePisaScore(
            fundamentalMastery = 0.98,
            transferSuccessRate = 0.95,
            multiStepReasoningRate = 0.92,
        )

        val level = PisaProficiencyLevel.fromScore(score)
        assertTrue("Score should exceed 730 points for top performers", score >= 730)
        assertEquals(PisaProficiencyLevel.LEVEL_6, level)
    }

    @Test
    fun `evaluating pisa profile identifies weakest cognitive process and triggers diagnostic intervention`() {
        // Student can calculate (EMPLOY = 8/10), but cannot translate word problems (FORMULATE = 2/10)
        val stats = mapOf(
            PisaCognitiveProcess.MATH_FORMULATE to (2 to 10), // 20%
            PisaCognitiveProcess.MATH_EMPLOY to (8 to 10),    // 80%
            PisaCognitiveProcess.MATH_INTERPRET_EVALUATE to (4 to 10), // 40%
        )

        val report = PisaAssessmentEngine.evaluatePisaProfile(
            domain = PisaDomain.MATHEMATICAL_LITERACY,
            processDemonstrations = stats,
        )

        assertNotNull(report)
        assertEquals("MATH_FORMULATE", report.weakestProcess)
        assertTrue("Report must indicate weakness in formulation", report.recommendedIntervention.contains("MATH_FORMULATE") || report.processMastery["MATH_FORMULATE"] == 0.2)
        assertEquals(0.200, report.processMastery["MATH_FORMULATE"]!!, 0.001)
        assertEquals(0.800, report.processMastery["MATH_EMPLOY"]!!, 0.001)
    }

    @Test
    fun `oecd baseline level 2 is strictly enforced at 482 points`() {
        assertEquals(PisaProficiencyLevel.LEVEL_1A, PisaProficiencyLevel.fromScore(481))
        assertEquals(PisaProficiencyLevel.LEVEL_2, PisaProficiencyLevel.fromScore(482))
        assertEquals(PisaProficiencyLevel.LEVEL_2, PisaProficiencyLevel.fromScore(544))
        assertEquals(PisaProficiencyLevel.LEVEL_3, PisaProficiencyLevel.fromScore(545))
    }
}
