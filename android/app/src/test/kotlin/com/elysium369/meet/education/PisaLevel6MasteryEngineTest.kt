package com.elysium369.meet.education

import com.elysium369.meet.education.domain.PisaCognitiveProcess
import com.elysium369.meet.education.domain.PisaDomain
import com.elysium369.meet.education.engine.PisaLevel6MasteryEngine
import com.elysium369.meet.education.engine.PisaSubjectStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PisaLevel6MasteryEngineTest {

    @Test
    fun `mathematics progression advances to level 6 perfection only when all criteria are met`() {
        // High fundamental score but missing MATH_REASON and insufficient transfers
        val partialSpec = PisaLevel6MasteryEngine.evaluateSubjectProgression(
            domain = PisaDomain.MATHEMATICAL_LITERACY,
            fundamentalMastery = 0.98,
            transferDemonstrations = 2,
            activeProcesses = setOf(
                PisaCognitiveProcess.MATH_FORMULATE,
                PisaCognitiveProcess.MATH_EMPLOY,
                PisaCognitiveProcess.MATH_INTERPRET_EVALUATE,
            ),
        )

        assertFalse("Cannot achieve Level 6 without full transfers and all processes", partialSpec.isLevel6Achieved)
        assertEquals(PisaSubjectStage.STAGE_4_RELATIONAL_MODELING, partialSpec.currentStage)
        assertTrue(partialSpec.missingCognitiveProcessesForLevel6.isNotEmpty())

        // Full mastery with 5 transfer tasks and all 4 mathematical processes
        val perfectSpec = PisaLevel6MasteryEngine.evaluateSubjectProgression(
            domain = PisaDomain.MATHEMATICAL_LITERACY,
            fundamentalMastery = 0.98,
            transferDemonstrations = 5,
            activeProcesses = setOf(
                PisaCognitiveProcess.MATH_FORMULATE,
                PisaCognitiveProcess.MATH_EMPLOY,
                PisaCognitiveProcess.MATH_INTERPRET_EVALUATE,
                PisaCognitiveProcess.MATH_REASON,
            ),
        )

        assertTrue("Level 6 perfection must be achieved", perfectSpec.isLevel6Achieved)
        assertEquals(PisaSubjectStage.STAGE_6_PERFECT_MASTERY, perfectSpec.currentStage)
        assertEquals(800, perfectSpec.targetScore)
        assertTrue(perfectSpec.missingCognitiveProcessesForLevel6.isEmpty())
    }

    @Test
    fun `reading literacy progression evaluates all three reading processes up to level 6`() {
        val readingSpec = PisaLevel6MasteryEngine.evaluateSubjectProgression(
            domain = PisaDomain.READING_LITERACY,
            fundamentalMastery = 0.96,
            transferDemonstrations = 6,
            activeProcesses = setOf(
                PisaCognitiveProcess.READING_LOCATE_INFORMATION,
                PisaCognitiveProcess.READING_UNDERSTAND_INTEGRATE,
                PisaCognitiveProcess.READING_EVALUATE_REFLECT,
            ),
        )

        assertTrue("Reading literacy reaches Level 6", readingSpec.isLevel6Achieved)
        assertEquals(PisaSubjectStage.STAGE_6_PERFECT_MASTERY, readingSpec.currentStage)
    }

    @Test
    fun `scientific literacy progression requires experimental enquiry and data interpretation`() {
        val scienceSpec = PisaLevel6MasteryEngine.evaluateSubjectProgression(
            domain = PisaDomain.SCIENTIFIC_LITERACY,
            fundamentalMastery = 0.95,
            transferDemonstrations = 5,
            activeProcesses = setOf(
                PisaCognitiveProcess.SCIENCE_EXPLAIN_PHENOMENA,
                PisaCognitiveProcess.SCIENCE_EVALUATE_DESIGN_ENQUIRY,
                PisaCognitiveProcess.SCIENCE_INTERPRET_DATA_EVIDENCE,
            ),
        )

        assertTrue("Scientific literacy reaches Level 6", scienceSpec.isLevel6Achieved)
        assertEquals(PisaSubjectStage.STAGE_6_PERFECT_MASTERY, scienceSpec.currentStage)
    }
}
