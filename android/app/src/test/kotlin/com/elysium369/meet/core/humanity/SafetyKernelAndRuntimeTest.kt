package com.elysium369.meet.core.humanity

import com.elysium369.meet.core.humanity.engine.ItemMemoryState
import com.elysium369.meet.core.humanity.engine.LearningReviewGrade
import com.elysium369.meet.core.humanity.engine.SpacedRepetitionScheduler
import com.elysium369.meet.core.humanity.safety.SafetyKernel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyKernelAndRuntimeTest {

    @Test
    fun testSafetyKernelVetoesHighVoltageAndAirbags() {
        val decisionAirbag = SafetyKernel.evaluateActionSafety(
            actionDescription = "Desmontar módulo de airbag del volante",
            nominalSafetyLevel = SafetyLevel.LOW_RISK_PRACTICE,
            userLevel = CapabilityLevel.L4_GUIDED_PRACTICE,
        )
        assertFalse(decisionAirbag.isAllowed)
        assertEquals(SafetyLevel.PROHIBITED_UNSUPERVISED, decisionAirbag.effectiveSafetyLevel)

        val decisionEvBattery = SafetyKernel.evaluateActionSafety(
            actionDescription = "Medición directa en terminales de tracción High Voltage 400V",
            nominalSafetyLevel = SafetyLevel.KNOWLEDGE_ONLY,
            userLevel = CapabilityLevel.L3_SIMULATED,
        )
        assertFalse(decisionEvBattery.isAllowed)
    }

    @Test
    fun testSafetyKernelAllowsLowRiskMultimeterTesting() {
        val decisionBattery12v = SafetyKernel.evaluateActionSafety(
            actionDescription = "Medir voltaje en bornes de batería de 12V con multímetro",
            nominalSafetyLevel = SafetyLevel.LOW_RISK_PRACTICE,
            userLevel = CapabilityLevel.L2_UNDERSTOOD,
        )
        assertTrue(decisionBattery12v.isAllowed)
        assertFalse(decisionBattery12v.requiresSupervision)
    }

    @Test
    fun testSpacedRepetitionSchedulerProgression() {
        val initial = ItemMemoryState("item_1", repetitions = 0, intervalDays = 0, easeFactor = 2.5, dueEpochDay = 100)
        val afterGood = SpacedRepetitionScheduler.schedule(initial, LearningReviewGrade.GOOD, 100)

        assertEquals(1, afterGood.repetitions)
        assertEquals(1, afterGood.intervalDays)
        assertEquals(101L, afterGood.dueEpochDay)

        val afterAgain = SpacedRepetitionScheduler.schedule(afterGood, LearningReviewGrade.AGAIN, 101)
        assertEquals(0, afterAgain.repetitions)
        assertEquals(1, afterAgain.intervalDays)
        assertEquals(1, afterAgain.incorrectCount)
    }
}
