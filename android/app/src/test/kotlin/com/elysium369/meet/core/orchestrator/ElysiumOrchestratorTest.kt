package com.elysium369.meet.core.orchestrator

import com.elysium369.meet.core.economic.EconomicPassport
import com.elysium369.meet.core.memory.MemoryClass
import com.elysium369.meet.core.memory.MemoryConfidence
import com.elysium369.meet.core.memory.MemoryEntry
import com.elysium369.meet.core.memory.MemoryProvenance
import com.elysium369.meet.core.services.kernel.UniversalServiceDomain
import com.elysium369.meet.education.economic.SkillCredentialState
import org.junit.Assert.*
import org.junit.Test

/**
 * ══════════════════════════════════════════════════════════════════════
 *  THE SUPREME TEST
 *  ─────────────────
 *  ASTRA V6 §85: "A human learns → demonstrates → offers service →
 *  someone hires → executes → verified → paid → reputation grows →
 *  more work arrives."
 *
 *  This test proves the FULL civilizational loop works.
 *  If this test passes, ELYSIUM's thesis is computationally proven.
 * ══════════════════════════════════════════════════════════════════════
 */
class ElysiumOrchestratorTest {

    private val orchestrator = ElysiumOrchestrator()

    // ═══════════════════════════════════════════════════════════
    // THE SUPREME TEST — Full Learning-to-Earning Pipeline
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `SUPREME TEST — learning to earning full pipeline completes all 7 phases`() {
        val passport = EconomicPassport(ownerId = "carlos-1", displayName = "Carlos Rodríguez")

        val result = orchestrator.orchestrate(
            userId = "carlos-1",
            input = "quiero aprender fontanería y trabajar de eso",
            passport = passport,
        )

        // ALL 7 phases must complete (or be intentionally skipped)
        assertTrue("Pipeline should be complete", result.isComplete)
        assertEquals(100, result.progressPercent)

        // UNDERSTAND phase completed
        val understand = result.phases[PipelinePhase.UNDERSTAND]!!
        assertEquals(PhaseStatus.COMPLETED, understand.status)
        // The orchestrator detects learning-to-earning via compound detection
        // (aprender + trabajar) even if the router classifies primary as LEARN
        assertEquals("true", understand.outputs["isLearningToEarning"])

        // LEARN phase completed (not skipped, because this IS learning-to-earning)
        val learn = result.phases[PipelinePhase.LEARN]!!
        assertEquals(PhaseStatus.COMPLETED, learn.status)

        // PROVE phase completed
        val prove = result.phases[PipelinePhase.PROVE]!!
        assertEquals(PhaseStatus.COMPLETED, prove.status)
        assertEquals("true", prove.outputs["isMarketplaceReady"])

        // CONNECT phase completed
        val connect = result.phases[PipelinePhase.CONNECT]!!
        assertEquals(PhaseStatus.COMPLETED, connect.status)

        // EXECUTE phase completed
        val execute = result.phases[PipelinePhase.EXECUTE]!!
        assertEquals(PhaseStatus.COMPLETED, execute.status)
        assertTrue(execute.outputs["milestones"]!!.contains("Diagnóstico"))

        // VERIFY phase completed with verification
        val verify = result.phases[PipelinePhase.VERIFY]!!
        assertEquals(PhaseStatus.COMPLETED, verify.status)
        assertEquals("true", verify.outputs["isVerified"])
        assertTrue(verify.outputs["integrityHash"]!!.startsWith("sha256-"))

        // GROW phase completed with reputation increase
        val grow = result.phases[PipelinePhase.GROW]!!
        assertEquals(PhaseStatus.COMPLETED, grow.status)
        assertEquals("1", grow.outputs["totalVerifiedJobs"])
    }

    // ═══════════════════════════════════════════════════════════
    // CONSTITUTIONAL INVARIANTS
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `INVARIANT 1 — KNOWLEDGE is not COMPETENCE — no evidence blocks proof`() {
        val (output, violations) = orchestrator.prove(
            skillName = "Plumbing",
            domain = UniversalServiceDomain.PLUMBING,
            evidenceRefs = emptyList(), // NO evidence!
            masteryLevel = 0.90,
        )

        // Must detect CRITICAL violation
        assertTrue(violations.any {
            it.invariantCode == ElysiumOrchestrator.INVARIANT_KNOWLEDGE_COMPETENCE &&
                it.severity == ViolationSeverity.CRITICAL
        })

        // Must NOT grant anything above LEARNED
        assertEquals(SkillCredentialState.LEARNED, output.credentialState)
        assertFalse(output.isMarketplaceReady)
    }

    @Test
    fun `INVARIANT 2 — DEMAND SIGNAL is not GUARANTEED INCOME — always disclosed`() {
        val (_, violations) = orchestrator.connect(
            domain = UniversalServiceDomain.PLUMBING,
            location = "Escazú",
            providerSkillLevel = SkillCredentialState.DEMONSTRATED,
        )

        // Must ALWAYS log the demand != income disclosure
        assertTrue(violations.any {
            it.invariantCode == ElysiumOrchestrator.INVARIANT_DEMAND_NOT_INCOME
        })
    }

    @Test
    fun `INVARIANT 3 — undemonstrated provider cannot be matched with customers`() {
        val (_, violations) = orchestrator.connect(
            domain = UniversalServiceDomain.ELECTRICAL,
            location = "San José",
            providerSkillLevel = SkillCredentialState.PRACTICED, // NOT enough
        )

        assertTrue(violations.any {
            it.invariantCode == ElysiumOrchestrator.INVARIANT_KNOWLEDGE_COMPETENCE &&
                it.severity == ViolationSeverity.CRITICAL
        })
    }

    @Test
    fun `INVARIANT 4 — verification requires BOTH customer confirmation AND evidence`() {
        val (output, violations) = orchestrator.verify(
            jobId = "job-1",
            customerConfirmed = false, // NOT confirmed
            evidenceRefs = listOf("photo://work.jpg"),
        )

        assertFalse(output.isVerified) // NOT verified
        assertTrue(violations.any {
            it.invariantCode == ElysiumOrchestrator.INVARIANT_TRUTH
        })
    }

    // ═══════════════════════════════════════════════════════════
    // PHASE 1: UNDERSTAND
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `understand classifies automotive intent`() {
        val (output, _) = orchestrator.understand("necesito un mecánico", "user-1")
        assertFalse(output.isLearningToEarning)
        assertTrue(output.confidence > 0)
    }

    @Test
    fun `understand detects learning-to-earning compound intent`() {
        val (output, _) = orchestrator.understand(
            "quiero aprender electricidad para trabajar", "user-1",
        )
        assertTrue(output.isLearningToEarning)
    }

    @Test
    fun `understand incorporates memory context`() {
        val memory = listOf(
            MemoryEntry(
                id = "mem-1", namespace = "learning",
                memoryClass = MemoryClass.LEARNING,
                key = "plumbing_progress", value = "Mastery 0.65",
                confidence = MemoryConfidence.HIGH,
                provenance = MemoryProvenance.LEARNING_ENGINE,
            ),
        )
        val (output, _) = orchestrator.understand("continuar aprendiendo", "user-1", memory)
        assertEquals(1, output.memoryContext.size)
    }

    // ═══════════════════════════════════════════════════════════
    // PHASE 2: LEARN
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `mastery starts at zero — never fabricated`() {
        val (output, _) = orchestrator.learn("user-1", listOf("fractions"))
        assertEquals(0.0, output.masteryLevel, 0.001)
        assertFalse(output.readyForDemonstration)
    }

    @Test
    fun `mastery capped at 0_75 without transfer evidence`() {
        var learn = orchestrator.learn("user-1", listOf("concept-1")).first
        learn = orchestrator.recordLearningProgress(learn, 0.80, false, "ev-1")
        assertEquals(0.75, learn.masteryLevel, 0.001)
    }

    @Test
    fun `mastery exceeds 0_75 WITH transfer evidence`() {
        var learn = orchestrator.learn("user-1", listOf("concept-1")).first
        learn = orchestrator.recordLearningProgress(learn, 0.90, true, "ev-1")
        assertEquals(0.90, learn.masteryLevel, 0.001)
    }

    @Test
    fun `ready for demonstration at mastery 0_70`() {
        var learn = orchestrator.learn("user-1", listOf("concept-1")).first
        learn = orchestrator.recordLearningProgress(learn, 0.72, true, "ev-1")
        assertTrue(learn.readyForDemonstration)
    }

    // ═══════════════════════════════════════════════════════════
    // PHASE 3: PROVE
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `mastery 0_50 grants PRACTICED`() {
        val (output, _) = orchestrator.prove(
            "PVC welding", UniversalServiceDomain.PLUMBING,
            listOf("ev-1"), 0.45,
        )
        assertEquals(SkillCredentialState.PRACTICED, output.credentialState)
    }

    @Test
    fun `mastery 0_72 grants DEMONSTRATED and is marketplace ready`() {
        val (output, _) = orchestrator.prove(
            "Pipe repair", UniversalServiceDomain.PLUMBING,
            listOf("ev-1", "ev-2"), 0.72,
        )
        assertEquals(SkillCredentialState.DEMONSTRATED, output.credentialState)
        assertTrue(output.isMarketplaceReady)
    }

    @Test
    fun `mastery 0_90 grants ELYSIUM_VERIFIED`() {
        val (output, _) = orchestrator.prove(
            "Master plumber", UniversalServiceDomain.PLUMBING,
            listOf("ev-1", "ev-2", "ev-3"), 0.90,
        )
        assertEquals(SkillCredentialState.ELYSIUM_VERIFIED, output.credentialState)
    }

    // ═══════════════════════════════════════════════════════════
    // PHASE 6: VERIFY
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `verified requires customer AND evidence`() {
        val (output, _) = orchestrator.verify(
            "job-1", true, listOf("photo://done.jpg"),
        )
        assertTrue(output.isVerified)
        assertTrue(output.integrityHash.startsWith("sha256-"))
    }

    @Test
    fun `not verified without customer confirmation`() {
        val (output, _) = orchestrator.verify("job-2", false, listOf("ev-1"))
        assertFalse(output.isVerified)
    }

    @Test
    fun `not verified without evidence`() {
        val (output, _) = orchestrator.verify("job-3", true, emptyList())
        assertFalse(output.isVerified)
    }

    // ═══════════════════════════════════════════════════════════
    // PHASE 7: GROW
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `reputation grows after verified job`() {
        val passport = EconomicPassport(ownerId = "p1", displayName = "Test")
        val verifyOut = VerifyOutput(true, listOf("ev-1"), "sha256-xxx", true)

        val (growOut, _) = orchestrator.grow(
            passport, UniversalServiceDomain.PLUMBING,
            verifyOut, "Fixed pipe", 45,
        )

        assertEquals(1, growOut.totalVerifiedJobs)
        assertTrue(growOut.newScore > 0.0)
    }

    // ═══════════════════════════════════════════════════════════
    // PIPELINE STATE
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `pipeline has 7 phases`() {
        assertEquals(7, PipelinePhase.entries.size)
    }

    @Test
    fun `pipeline progress starts at 0`() {
        val state = PipelineState(
            pipelineId = "test", userId = "u1", originalInput = "test",
        )
        assertEquals(0, state.progressPercent)
        assertFalse(state.isComplete)
    }

    @Test
    fun `service-only intent skips LEARN phase`() {
        val passport = EconomicPassport(ownerId = "p1", displayName = "Test")
        val result = orchestrator.orchestrate(
            userId = "p1",
            input = "necesito un plomero urgente",
            passport = passport,
        )

        val learn = result.phases[PipelinePhase.LEARN]!!
        assertEquals(PhaseStatus.SKIPPED, learn.status)
        assertTrue(result.isComplete)
    }

    // ═══════════════════════════════════════════════════════════
    // CROSS-DOMAIN PROOF
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `same pipeline works across automotive AND plumbing domains`() {
        val passport = EconomicPassport(ownerId = "multi", displayName = "Multi-skilled")

        // Automotive pipeline
        val autoResult = orchestrator.orchestrate(
            "multi", "aprender mecánica automotriz y trabajar", passport,
        )
        assertTrue(autoResult.isComplete)

        // Plumbing pipeline
        val plumbResult = orchestrator.orchestrate(
            "multi", "aprender fontanería y trabajar de eso", passport,
        )
        assertTrue(plumbResult.isComplete)

        // Both complete through the SAME orchestrator
        assertEquals(100, autoResult.progressPercent)
        assertEquals(100, plumbResult.progressPercent)
    }
}
