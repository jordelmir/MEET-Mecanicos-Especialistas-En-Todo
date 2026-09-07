package com.elysium369.meet.education

import com.elysium369.meet.education.domain.ConceptKnowledgeState
import com.elysium369.meet.education.domain.CurriculumConcept
import com.elysium369.meet.education.domain.CurriculumGranularity
import com.elysium369.meet.education.domain.CurriculumPrerequisite
import com.elysium369.meet.education.domain.CurriculumSourceAnchor
import com.elysium369.meet.education.domain.CurriculumSourceKind
import com.elysium369.meet.education.domain.EpistemicTruthState
import com.elysium369.meet.education.economic.SkillCredentialState
import com.elysium369.meet.education.economic.SkillToServiceBridge
import com.elysium369.meet.education.engine.ElysiumMasteryEngine
import com.elysium369.meet.education.forge.ForgeEducationBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ElysiumLearningOsTest {

    @Test
    fun `curriculum authority gate prevents unofficial source from claiming canonical status`() {
        try {
            CurriculumSourceAnchor(
                sourceId = "ai_generated_unanchored_math",
                authorityId = "AI_MODEL",
                canonicalTitle = "Invented Math Skill",
                sourceLocator = "https://ai.generated/math",
                documentHash = "fakehash123",
                effectiveYear = 2026,
                sourceKind = CurriculumSourceKind.CANONICAL_CURRICULUM, // Invalid for unofficial!
                sourceGranularity = CurriculumGranularity.OFFICIAL_MONTHLY,
                isOfficial = false,
            )
            fail("Expected IllegalArgumentException for unanchored unofficial source claiming CANONICAL_CURRICULUM")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unofficial source cannot claim") == true)
        }
    }

    @Test
    fun `mastery engine caps standard tasks at 0_75 and strictly requires transfer for mastery`() {
        var state = ConceptKnowledgeState(
            learnerId = "learner_001",
            conceptId = "cr_mat1_c_spatial_pos",
            masteryEstimate = 0.30,
            confidence = 0.30,
        )

        // Repeat 10 standard correct tasks (without transfer)
        for (i in 1..10) {
            state = ElysiumMasteryEngine.recordEvidence(
                current = state,
                isCorrect = true,
                isTransferTask = false,
                todayEpochDay = 100L,
            )
        }

        // Invariant: Non-transfer tasks must cap at 0.750 max!
        assertTrue("Mastery without transfer must not exceed 0.75", state.masteryEstimate <= 0.75)
        assertFalse("Concept cannot be marked mastered without transfer", state.isMastered)
        assertTrue("Concept should be transfer ready", state.isTransferReady)

        // Now demonstrate transfer in a new context
        state = ElysiumMasteryEngine.recordEvidence(
            current = state,
            isCorrect = true,
            isTransferTask = true,
            todayEpochDay = 110L,
        )

        // With transfer, mastery advances beyond 0.85
        assertTrue("Mastery with transfer advances beyond 0.85", state.masteryEstimate >= 0.85)
        assertTrue("Concept is now officially mastered", state.isMastered)
    }

    @Test
    fun `learning frontier calculation respects strict prerequisites`() {
        val conceptCounting = CurriculumConcept(
            id = "c_counting",
            unitId = "u_01",
            conceptCode = "COUNT_100",
            title = "Conteo hasta 100",
            description = "Conteo básico",
            sourceAnchor = "MEP_01",
        )
        val conceptAddition = CurriculumConcept(
            id = "c_addition",
            unitId = "u_02",
            conceptCode = "ADD_SUB",
            title = "Suma y Resta",
            description = "Operaciones básicas",
            sourceAnchor = "MEP_02",
        )
        val prerequisites = listOf(
            CurriculumPrerequisite(
                conceptId = "c_addition",
                prerequisiteConceptId = "c_counting",
                relationshipType = "STRICT_PREREQUISITE",
            )
        )

        // Case 1: Learner has not mastered counting (mastery = 0.40)
        val statesUnready = mapOf(
            "c_counting" to ConceptKnowledgeState(learnerId = "l1", conceptId = "c_counting", masteryEstimate = 0.40),
            "c_addition" to ConceptKnowledgeState(learnerId = "l1", conceptId = "c_addition", masteryEstimate = 0.00),
        )
        val frontier1 = ElysiumMasteryEngine.computeLearningFrontier(
            targetConcepts = listOf(conceptCounting, conceptAddition),
            prerequisites = prerequisites,
            states = statesUnready,
            conceptMonthMap = mapOf("c_counting" to 2, "c_addition" to 3),
        )

        // Addition must be blocked by unmastered prerequisite! Only counting is on the frontier.
        assertEquals(1, frontier1.size)
        assertEquals("c_counting", frontier1[0].conceptId)

        // Case 2: Learner masters counting (mastery = 0.90)
        val statesReady = mapOf(
            "c_counting" to ConceptKnowledgeState(learnerId = "l1", conceptId = "c_counting", masteryEstimate = 0.90, successfulTransferCount = 1),
            "c_addition" to ConceptKnowledgeState(learnerId = "l1", conceptId = "c_addition", masteryEstimate = 0.00),
        )
        val frontier2 = ElysiumMasteryEngine.computeLearningFrontier(
            targetConcepts = listOf(conceptCounting, conceptAddition),
            prerequisites = prerequisites,
            states = statesReady,
            conceptMonthMap = mapOf("c_counting" to 2, "c_addition" to 3),
        )

        // Counting is mastered, addition is now unlocked on the frontier!
        assertEquals(1, frontier2.size)
        assertEquals("c_addition", frontier2[0].conceptId)
    }

    @Test
    fun `constitutional invariant prevents simulation from granting external legal credentials`() {
        try {
            SkillToServiceBridge.validateCredentialPromotion(
                current = SkillCredentialState.PRACTICED,
                requested = SkillCredentialState.EXTERNALLY_CREDENTIALLED,
                hasExternalLicenseEvidence = false,
                isSimulationOutcomeOnly = true,
            )
            fail("Expected IllegalStateException for simulation granting external credential")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("CONSTITUTIONAL_VIOLATION") == true)
        }
    }

    @Test
    fun `forge education bridge generates deterministic sha256 evidence hash`() {
        val packet = ForgeEducationBridge.createEvidencePacket(
            learnerId = "learner_123",
            conceptId = "cr_mat1_c_spatial_pos",
            taskId = "task_place_behind_house",
            environmentContext = "FORGE_3D_ROOM",
            isCorrect = true,
            isTransferTask = true,
            interactionPayload = "{\"car_position\":[-5.0,0.0,12.0]}",
        )

        assertEquals(64, packet.rawEvidenceHash.length)
        assertTrue(packet.isTransferTask)
        assertTrue(packet.isCorrect)
    }
}
