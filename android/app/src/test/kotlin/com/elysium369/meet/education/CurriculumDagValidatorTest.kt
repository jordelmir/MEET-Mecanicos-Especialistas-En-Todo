package com.elysium369.meet.education

import com.elysium369.meet.education.domain.EducationDomainEvent
import com.elysium369.meet.education.domain.EventEnvelope
import com.elysium369.meet.education.domain.OfflineEventReplayEngine
import com.elysium369.meet.education.domain.UniversalCognitiveLevel
import com.elysium369.meet.education.domain.UniversalCompetency
import com.elysium369.meet.education.domain.UniversalEducationalDomain
import com.elysium369.meet.education.domain.UniversalPrerequisite
import com.elysium369.meet.education.engine.AssessmentMasteryState
import com.elysium369.meet.education.engine.CurriculumDagValidator
import com.elysium369.meet.education.engine.UniversalAssessmentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumDagValidatorTest {

    private fun createCompetency(id: String, domain: UniversalEducationalDomain = UniversalEducationalDomain.DOMAIN_02_MATHEMATICS): UniversalCompetency {
        return UniversalCompetency(
            id = id,
            domain = domain,
            code = id.uppercase(),
            title = "Competency $id",
            description = "Description for $id",
            cognitiveLevel = UniversalCognitiveLevel.APPLY,
        )
    }

    @Test
    fun `valid linear curriculum dag validates and generates correct topological order`() {
        val c1 = createCompetency("c_counting")
        val c2 = createCompetency("c_addition")
        val c3 = createCompetency("c_multiplication")

        val p1 = UniversalPrerequisite(competencyId = "c_addition", prerequisiteId = "c_counting")
        val p2 = UniversalPrerequisite(competencyId = "c_multiplication", prerequisiteId = "c_addition")

        val report = CurriculumDagValidator.validate(listOf(c1, c2, c3), listOf(p1, p2))

        assertTrue(report.isValid)
        assertFalse(report.hasCycles)
        assertEquals(listOf("c_counting", "c_addition", "c_multiplication"), report.topologicalSort)
        assertEquals(3, report.maxGraphDepth)
    }

    @Test
    fun `direct circular dependency A-B-A is detected and rejected`() {
        val cA = createCompetency("c_algebra")
        val cB = createCompetency("c_calculus")

        val p1 = UniversalPrerequisite(competencyId = "c_algebra", prerequisiteId = "c_calculus")
        val p2 = UniversalPrerequisite(competencyId = "c_calculus", prerequisiteId = "c_algebra")

        val report = CurriculumDagValidator.validate(listOf(cA, cB), listOf(p1, p2))

        assertFalse(report.isValid)
        assertTrue(report.hasCycles)
        assertTrue(report.errors.any { it.contains("Circular prerequisite") })
    }

    @Test
    fun `multi-hop circular dependency A-B-C-D-A is detected and rejected`() {
        val cA = createCompetency("node_a")
        val cB = createCompetency("node_b")
        val cC = createCompetency("node_c")
        val cD = createCompetency("node_d")

        val p1 = UniversalPrerequisite(competencyId = "node_b", prerequisiteId = "node_a")
        val p2 = UniversalPrerequisite(competencyId = "node_c", prerequisiteId = "node_b")
        val p3 = UniversalPrerequisite(competencyId = "node_d", prerequisiteId = "node_c")
        val p4 = UniversalPrerequisite(competencyId = "node_a", prerequisiteId = "node_d") // Cycle back to A

        val report = CurriculumDagValidator.validate(listOf(cA, cB, cC, cD), listOf(p1, p2, p3, p4))

        assertFalse(report.isValid)
        assertTrue(report.hasCycles)
    }

    @Test
    fun `dangling prerequisite to missing competency is rejected`() {
        val c1 = createCompetency("c_physics")
        val p1 = UniversalPrerequisite(competencyId = "c_physics", prerequisiteId = "non_existent_math")

        val report = CurriculumDagValidator.validate(listOf(c1), listOf(p1))

        assertFalse(report.isValid)
        assertEquals(1, report.danglingPrerequisites.size)
        assertTrue(report.errors.any { it.contains("missing prerequisiteId") })
    }

    @Test
    fun `offline event replay engine reconciles two devices without evidence loss and deduplicates keys`() {
        val event1 = EducationDomainEvent.ExerciseAttempted(
            eventId = "evt_001",
            learnerId = "learner_ana",
            timestampMs = 1000L,
            taskId = "task_add_01",
            competencyId = "c_addition",
            isCorrect = true,
            selectedOptionIndex = 0,
            responseLatencyMs = 1200,
            misconceptionCode = null,
        )
        val event2 = EducationDomainEvent.ExerciseAttempted(
            eventId = "evt_002",
            learnerId = "learner_ana",
            timestampMs = 2000L,
            taskId = "task_add_02",
            competencyId = "c_addition",
            isCorrect = true,
            selectedOptionIndex = 1,
            responseLatencyMs = 950,
            misconceptionCode = null,
        )
        val event3 = EducationDomainEvent.AssessmentSubmitted(
            eventId = "evt_003",
            learnerId = "learner_ana",
            timestampMs = 3000L,
            assessmentId = "eval_add_final",
            competencyId = "c_addition",
            rawScore = 0.95,
            passed = true,
            isIndependentTransfer = true,
        )

        val env1 = EventEnvelope.create(event1, sequenceNumber = 1, previousEventHash = "GENESIS")
        val env2 = EventEnvelope.create(event2, sequenceNumber = 2, previousEventHash = env1.eventHash)
        val env3 = EventEnvelope.create(event3, sequenceNumber = 3, previousEventHash = env2.eventHash)

        // Device A has [env1, env2], Device B has [env1 (duplicate), env3]
        val deviceA = listOf(env1, env2)
        val deviceB = listOf(env1, env3)

        val reconciled = OfflineEventReplayEngine.reconcile(deviceA, deviceB)

        // Exactly 3 unique events must remain in chronological order
        assertEquals(3, reconciled.size)
        assertEquals("evt_001", reconciled[0].eventId)
        assertEquals("evt_002", reconciled[1].eventId)
        assertEquals("evt_003", reconciled[2].eventId)
    }

    @Test
    fun `assessment engine enforces non-transfer cap at 0_75 and requires transfer for mastery`() {
        // Attempt without transfer: score = 0.95 -> must be capped at 0.750 max!
        val nonTransferEval = UniversalAssessmentEngine.evaluateAttempt(
            attemptId = "att_01",
            learnerId = "student_01",
            competencyId = "c_fractions",
            rawScore = 0.95,
            isTransferTask = false,
            priorState = AssessmentMasteryState.ATTEMPTED,
            priorMastery = 0.50,
            consecutiveSuccessfulReviews = 0,
        )

        assertEquals(0.750, nonTransferEval.computedMastery, 0.001)
        assertEquals(AssessmentMasteryState.PASSED, nonTransferEval.newState)
        assertEquals(7, nonTransferEval.daysUntilNextRetentionCheck)

        // Attempt with transfer: score = 0.95 -> advances to MASTERED
        val transferEval = UniversalAssessmentEngine.evaluateAttempt(
            attemptId = "att_02",
            learnerId = "student_01",
            competencyId = "c_fractions",
            rawScore = 0.95,
            isTransferTask = true,
            priorState = AssessmentMasteryState.PASSED,
            priorMastery = 0.75,
            consecutiveSuccessfulReviews = 1,
        )

        assertTrue(transferEval.computedMastery >= 0.850)
        assertEquals(AssessmentMasteryState.MASTERED, transferEval.newState)
        assertEquals(30, transferEval.daysUntilNextRetentionCheck)
    }
}
