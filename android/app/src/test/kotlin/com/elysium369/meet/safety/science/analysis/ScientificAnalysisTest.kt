package com.elysium369.meet.safety.science.analysis

import com.elysium369.meet.safety.science.domain.CausalStatus
import com.elysium369.meet.safety.science.domain.EvidenceAssertionState
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Tests for §49 (Source Independence), §50 (Causal Analysis),
 * and §51 (Accountability / NonAction ≠ Criminal Liability).
 */
class ScientificAnalysisTest {

    // ═══════════════════════════════════════════════════════════════
    // §49 — Source Independence
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `copied articles do not count as independent sources`() {
        val sources = listOf(
            ScientificSource(UUID.randomUUID(), "Author A", "UniX", "D1", "web", "survey"),
            ScientificSource(UUID.randomUUID(), "Author B", "UniX", "D1", "web", "survey"),
            ScientificSource(UUID.randomUUID(), "Author C", "UniX", "D1", "web", "survey"),
        )
        val groups = SourceIndependenceAnalyzer().group(sources)
        assertEquals("3 authors sharing same dataset+institution = 1 group", 1, groups.size)
    }

    @Test
    fun `different datasets are truly independent`() {
        val sources = listOf(
            ScientificSource(UUID.randomUUID(), "Author A", "UniX", "D1", "web", "survey"),
            ScientificSource(UUID.randomUUID(), "Author B", "UniY", "D2", "field", "interview"),
        )
        val groups = SourceIndependenceAnalyzer().group(sources)
        assertEquals("Different dataset+institution = independent", 2, groups.size)
    }

    @Test
    fun `same dataset different institution is separate group`() {
        val sources = listOf(
            ScientificSource(UUID.randomUUID(), "A", "UniX", "D1", "web", "survey"),
            ScientificSource(UUID.randomUUID(), "B", "UniY", "D1", "web", "survey"),
        )
        val groups = SourceIndependenceAnalyzer().group(sources)
        // Different institution = different provenance = separate groups
        assertEquals(2, groups.size)
    }

    @Test
    fun `ungrouped sources without dataset are separate`() {
        val sources = listOf(
            ScientificSource(UUID.randomUUID(), "A", null, null, null, null),
            ScientificSource(UUID.randomUUID(), "B", null, null, null, null),
        )
        val groups = SourceIndependenceAnalyzer().group(sources)
        // No provenance info — each gets its own ungrouped key
        assertEquals(2, groups.size)
    }

    // ═══════════════════════════════════════════════════════════════
    // §50 — Causal Analysis
    // ═══════════════════════════════════════════════════════════════

    private val causalEngine = CausalEngine()

    @Test
    fun `temporal sequence alone cannot establish causality`() {
        val assessment = causalEngine.evaluate(
            temporalOrdering = true,
            mechanismEvidence = emptyList(),
            alternativeCauses = listOf("C1", "C2"),
            confounders = listOf("C3"),
        )
        assertEquals(
            "Temporal + confounders = only TEMPORAL_ASSOCIATION",
            CausalStatus.TEMPORAL_ASSOCIATION,
            assessment.causalStatus,
        )
    }

    @Test
    fun `no temporal ordering yields UNKNOWN`() {
        val assessment = causalEngine.evaluate(
            temporalOrdering = false,
            mechanismEvidence = listOf(UUID.randomUUID()),
            alternativeCauses = emptyList(),
            confounders = emptyList(),
        )
        assertEquals(CausalStatus.UNKNOWN, assessment.causalStatus)
    }

    @Test
    fun `mechanism evidence with alternatives is CORRELATIONAL`() {
        val assessment = causalEngine.evaluate(
            temporalOrdering = true,
            mechanismEvidence = listOf(UUID.randomUUID()),
            alternativeCauses = listOf("alternative explanation"),
            confounders = emptyList(),
        )
        assertEquals(CausalStatus.CORRELATIONAL, assessment.causalStatus)
    }

    @Test
    fun `mechanism evidence without alternatives is MECHANISTIC_SUPPORT`() {
        val assessment = causalEngine.evaluate(
            temporalOrdering = true,
            mechanismEvidence = listOf(UUID.randomUUID()),
            alternativeCauses = emptyList(),
            confounders = emptyList(),
        )
        assertEquals(CausalStatus.MECHANISTIC_SUPPORT, assessment.causalStatus)
    }

    @Test
    fun `confounders prevent MECHANISTIC_SUPPORT`() {
        val assessment = causalEngine.evaluate(
            temporalOrdering = true,
            mechanismEvidence = listOf(UUID.randomUUID()),
            alternativeCauses = emptyList(),
            confounders = listOf("selection bias"),
        )
        assertEquals(CausalStatus.CORRELATIONAL, assessment.causalStatus)
    }

    // ═══════════════════════════════════════════════════════════════
    // §51 — Accountability: NonAction ≠ Criminal Liability
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `non action is sealed as NON_ACTION not criminal liability`() {
        val actor = UUID.randomUUID()
        val evidence = UUID.randomUUID()
        val instant = Instant.now()

        val action = com.elysium369.meet.safety.science.domain.AccountabilityAction.NonAction(
            actorEntityId = actor,
            occurredAt = instant,
            evidenceIds = listOf(evidence),
            expectedAction = "INVESTIGATE",
        )

        // The type system separates Action from NonAction
        assertTrue(
            "NonAction is AccountabilityAction",
            action is com.elysium369.meet.safety.science.domain.AccountabilityAction,
        )

        // There is no CriminalLiability subtype — by design
        assertTrue(
            "NonAction records expectedAction, not criminal charge",
            action.expectedAction == "INVESTIGATE",
        )
    }

    @Test
    fun `action and non-action are distinct sealed types`() {
        val actor = UUID.randomUUID()
        val instant = Instant.now()

        val action = com.elysium369.meet.safety.science.domain.AccountabilityAction.Action(
            actorEntityId = actor,
            occurredAt = instant,
            evidenceIds = emptyList(),
            actionType = "FILED_REPORT",
        )

        val nonAction = com.elysium369.meet.safety.science.domain.AccountabilityAction.NonAction(
            actorEntityId = actor,
            occurredAt = instant,
            evidenceIds = emptyList(),
            expectedAction = "INVESTIGATE",
        )

        // They share the sealed interface but are never conflated
        assertNotEquals(action::class, nonAction::class)
    }

    // ═══════════════════════════════════════════════════════════════
    // Falsification Engine (§21)
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `falsification engine detects contradictions`() {
        val hypothesis = ResearchHypothesis(
            id = UUID.randomUUID(),
            proposition = "X received document D",
            nullHypothesis = "X did not receive document D",
            supportingEvidenceIds = emptyList(),
            contradictingEvidenceIds = emptyList(),
            alternativeHypothesisIds = emptyList(),
            falsificationCriteria = listOf("delivery_receipt"),
            status = HypothesisStatus.PROPOSED,
            methodologyVersion = "v1",
        )

        val evidence = listOf(
            EvidenceAssertion(
                id = UUID.randomUUID(),
                claimId = null,
                hypothesisId = hypothesis.id,
                assertionState = EvidenceAssertionState.CONTRADICTED,
                tags = setOf("delivery_receipt"),
            ),
        )

        val result = FalsificationEngine().evaluate(hypothesis, evidence)
        assertEquals(HypothesisStatus.DISPUTED, result.status)
        assertEquals(1, result.contradictingEvidence.size)
    }

    @Test
    fun `supported hypothesis with all criteria met`() {
        val hypothesis = ResearchHypothesis(
            id = UUID.randomUUID(),
            proposition = "Entity held position P during period T",
            nullHypothesis = null,
            supportingEvidenceIds = emptyList(),
            contradictingEvidenceIds = emptyList(),
            alternativeHypothesisIds = emptyList(),
            falsificationCriteria = listOf("appointment_letter", "payroll_record"),
            status = HypothesisStatus.TESTING,
            methodologyVersion = "v1",
        )

        val evidence = listOf(
            EvidenceAssertion(
                id = UUID.randomUUID(),
                claimId = null,
                hypothesisId = hypothesis.id,
                assertionState = EvidenceAssertionState.DOCUMENTED,
                tags = setOf("appointment_letter"),
            ),
            EvidenceAssertion(
                id = UUID.randomUUID(),
                claimId = null,
                hypothesisId = hypothesis.id,
                assertionState = EvidenceAssertionState.CORROBORATED,
                tags = setOf("payroll_record"),
            ),
        )

        val result = FalsificationEngine().evaluate(hypothesis, evidence)
        assertEquals(HypothesisStatus.SUPPORTED, result.status)
        assertTrue(result.unresolvedCriteria.isEmpty())
    }

    @Test
    fun `missing criteria yields WEAKLY_SUPPORTED`() {
        val hypothesis = ResearchHypothesis(
            id = UUID.randomUUID(),
            proposition = "Test",
            nullHypothesis = null,
            supportingEvidenceIds = emptyList(),
            contradictingEvidenceIds = emptyList(),
            alternativeHypothesisIds = emptyList(),
            falsificationCriteria = listOf("criterion_a", "criterion_b"),
            status = HypothesisStatus.TESTING,
            methodologyVersion = "v1",
        )

        val evidence = listOf(
            EvidenceAssertion(
                id = UUID.randomUUID(),
                claimId = null,
                hypothesisId = hypothesis.id,
                assertionState = EvidenceAssertionState.DOCUMENTED,
                tags = setOf("criterion_a"),
                // criterion_b NOT present
            ),
        )

        val result = FalsificationEngine().evaluate(hypothesis, evidence)
        assertEquals(HypothesisStatus.WEAKLY_SUPPORTED, result.status)
        assertEquals(listOf("criterion_b"), result.unresolvedCriteria)
    }
}
