package com.elysium369.meet.safety.science.domain

import com.elysium369.meet.core.truth.TruthState
import org.junit.Assert.*
import org.junit.Test

class TruthStateMappingTest {

    @Test
    fun `OBSERVED maps to OBSERVED`() {
        assertEquals(EvidenceAssertionState.OBSERVED, TruthState.OBSERVED.toScientificState())
    }

    @Test
    fun `AUTHORITATIVE maps to AUTHORITATIVE`() {
        assertEquals(EvidenceAssertionState.AUTHORITATIVE, TruthState.AUTHORITATIVE.toScientificState())
    }

    @Test
    fun `DERIVED maps to DERIVED`() {
        assertEquals(EvidenceAssertionState.DERIVED, TruthState.DERIVED.toScientificState())
    }

    @Test
    fun `ESTIMATED never elevates — maps to INSUFFICIENT_EVIDENCE`() {
        assertEquals(EvidenceAssertionState.INSUFFICIENT_EVIDENCE, TruthState.ESTIMATED.toScientificState())
    }

    @Test
    fun `SIMULATED never elevates — maps to INSUFFICIENT_EVIDENCE`() {
        assertEquals(EvidenceAssertionState.INSUFFICIENT_EVIDENCE, TruthState.SIMULATED.toScientificState())
    }

    @Test
    fun `UNKNOWN maps to UNKNOWN`() {
        assertEquals(EvidenceAssertionState.UNKNOWN, TruthState.UNKNOWN.toScientificState())
    }

    @Test
    fun `NOT_INTEGRATED maps to INSUFFICIENT_EVIDENCE`() {
        assertEquals(EvidenceAssertionState.INSUFFICIENT_EVIDENCE, TruthState.NOT_INTEGRATED.toScientificState())
    }

    @Test
    fun `NOT_EXECUTED maps to INSUFFICIENT_EVIDENCE`() {
        assertEquals(EvidenceAssertionState.INSUFFICIENT_EVIDENCE, TruthState.NOT_EXECUTED.toScientificState())
    }

    @Test
    fun `mapping is exhaustive — all TruthState values are covered`() {
        // If this compiles, the when() in toScientificState is exhaustive.
        // But let's also verify at runtime that none throw.
        TruthState.entries.forEach { state ->
            assertNotNull("$state should map to a valid assertion state", state.toScientificState())
        }
    }

    @Test
    fun `no TruthState maps to CORROBORATED — automatic elevation prohibited`() {
        TruthState.entries.forEach { state ->
            assertNotEquals(
                "TruthState.$state must not auto-elevate to CORROBORATED",
                EvidenceAssertionState.CORROBORATED,
                state.toScientificState(),
            )
        }
    }

    @Test
    fun `no TruthState maps to PEER_REVIEWED — automatic elevation prohibited`() {
        TruthState.entries.forEach { state ->
            assertNotEquals(
                "TruthState.$state must not auto-elevate to PEER_REVIEWED",
                EvidenceAssertionState.PEER_REVIEWED,
                state.toScientificState(),
            )
        }
    }

    @Test
    fun `no TruthState maps to INDEPENDENTLY_REPLICATED — automatic elevation prohibited`() {
        TruthState.entries.forEach { state ->
            assertNotEquals(
                "TruthState.$state must not auto-elevate to INDEPENDENTLY_REPLICATED",
                EvidenceAssertionState.INDEPENDENTLY_REPLICATED,
                state.toScientificState(),
            )
        }
    }
    @Test
    fun assessmentPreservesOriginalStatesThatShareAConservativeProjection() {
        val estimated = TruthState.ESTIMATED.toScientificAssessment()
        val simulated = TruthState.SIMULATED.toScientificAssessment()
        val notIntegrated = TruthState.NOT_INTEGRATED.toScientificAssessment()
        val notExecuted = TruthState.NOT_EXECUTED.toScientificAssessment()

        listOf(estimated, simulated, notIntegrated, notExecuted).forEach { assessment ->
            assertEquals(EvidenceAssertionState.INSUFFICIENT_EVIDENCE, assessment.scientificState)
            assertTrue(assessment.explanation.isNotBlank())
        }
        assertEquals(TruthState.ESTIMATED, estimated.sourceState)
        assertEquals(TruthState.SIMULATED, simulated.sourceState)
        assertEquals(TruthState.NOT_INTEGRATED, notIntegrated.sourceState)
        assertEquals(TruthState.NOT_EXECUTED, notExecuted.sourceState)
        assertNotEquals(simulated.explanation, notIntegrated.explanation)
        assertNotEquals(notIntegrated.explanation, notExecuted.explanation)
    }

    @Test
    fun assessmentNeverChangesOriginalStateAndKeepsExistingProjection() {
        TruthState.entries.forEach { source ->
            val assessment = source.toScientificAssessment()
            assertEquals(source, assessment.sourceState)
            assertEquals(source.toScientificState(), assessment.scientificState)
        }
    }

}
