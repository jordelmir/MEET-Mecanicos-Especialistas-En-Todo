package com.elysium369.meet.safety.science.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AssertionStateMachineTest {

    private val machine = AssertionStateMachine()

    // ── Valid transitions ────────────────────────────────────

    @Test
    fun `OBSERVED to DOCUMENTED is allowed`() {
        assertTrue(machine.canTransition(
            EvidenceAssertionState.OBSERVED,
            EvidenceAssertionState.DOCUMENTED,
        ))
    }

    @Test
    fun `DOCUMENTED to CORROBORATED is allowed`() {
        assertTrue(machine.canTransition(
            EvidenceAssertionState.DOCUMENTED,
            EvidenceAssertionState.CORROBORATED,
        ))
    }

    @Test
    fun `CORROBORATED to PEER_REVIEWED is allowed`() {
        assertTrue(machine.canTransition(
            EvidenceAssertionState.CORROBORATED,
            EvidenceAssertionState.PEER_REVIEWED,
        ))
    }

    @Test
    fun `PEER_REVIEWED to INDEPENDENTLY_REPLICATED is allowed`() {
        assertTrue(machine.canTransition(
            EvidenceAssertionState.PEER_REVIEWED,
            EvidenceAssertionState.INDEPENDENTLY_REPLICATED,
        ))
    }

    @Test
    fun `any state can transition to DISPUTED`() {
        val disputeStates = listOf(
            EvidenceAssertionState.OBSERVED,
            EvidenceAssertionState.DOCUMENTED,
            EvidenceAssertionState.CORROBORATED,
            EvidenceAssertionState.PEER_REVIEWED,
            EvidenceAssertionState.DERIVED,
            EvidenceAssertionState.STATISTICALLY_SUPPORTED,
        )
        disputeStates.forEach { state ->
            assertTrue(
                "$state → DISPUTED should be allowed",
                machine.canTransition(state, EvidenceAssertionState.DISPUTED),
            )
        }
    }

    // ── Invalid transitions (skip-level) ────────────────────

    @Test
    fun `OBSERVED cannot skip to CORROBORATED`() {
        assertFalse(machine.canTransition(
            EvidenceAssertionState.OBSERVED,
            EvidenceAssertionState.CORROBORATED,
        ))
    }

    @Test
    fun `OBSERVED cannot skip to INDEPENDENTLY_REPLICATED`() {
        assertFalse(machine.canTransition(
            EvidenceAssertionState.OBSERVED,
            EvidenceAssertionState.INDEPENDENTLY_REPLICATED,
        ))
    }

    @Test
    fun `DOCUMENTED cannot skip to PEER_REVIEWED`() {
        assertFalse(machine.canTransition(
            EvidenceAssertionState.DOCUMENTED,
            EvidenceAssertionState.PEER_REVIEWED,
        ))
    }

    // ── AI cannot elevate ───────────────────────────────────

    @Test
    fun `ai cannot elevate claim truth state`() {
        val subject = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val evidence = UUID.randomUUID()

        assertThrows(IllegalStateException::class.java) {
            machine.transition(
                subjectId = subject,
                from = EvidenceAssertionState.OBSERVED,
                to = EvidenceAssertionState.DOCUMENTED,
                reason = "AI extracted this",
                evidenceIds = listOf(evidence),
                actorId = actor,
                methodologyVersion = "safety-science-v1",
                actorIsAi = true,
            )
        }
    }

    @Test
    fun `ai CAN transition to DISPUTED`() {
        val subject = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val evidence = UUID.randomUUID()

        val transition = machine.transition(
            subjectId = subject,
            from = EvidenceAssertionState.DOCUMENTED,
            to = EvidenceAssertionState.DISPUTED,
            reason = "Contradicting evidence found by AI analysis",
            evidenceIds = listOf(evidence),
            actorId = actor,
            methodologyVersion = "safety-science-v1",
            actorIsAi = true,
        )

        assertEquals(EvidenceAssertionState.DISPUTED, transition.to)
    }

    @Test
    fun `ai CAN transition to INSUFFICIENT_EVIDENCE`() {
        val subject = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val evidence = UUID.randomUUID()

        val transition = machine.transition(
            subjectId = subject,
            from = EvidenceAssertionState.UNKNOWN,
            to = EvidenceAssertionState.INSUFFICIENT_EVIDENCE,
            reason = "Not enough data to classify",
            evidenceIds = listOf(evidence),
            actorId = actor,
            methodologyVersion = "safety-science-v1",
            actorIsAi = true,
        )

        assertEquals(EvidenceAssertionState.INSUFFICIENT_EVIDENCE, transition.to)
    }

    // ── Transition requires evidence ────────────────────────

    @Test
    fun `transition without evidence throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            machine.transition(
                subjectId = UUID.randomUUID(),
                from = EvidenceAssertionState.OBSERVED,
                to = EvidenceAssertionState.DOCUMENTED,
                reason = "no evidence attached",
                evidenceIds = emptyList(),
                actorId = UUID.randomUUID(),
                methodologyVersion = "safety-science-v1",
            )
        }
    }

    @Test
    fun `transition without reason throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            machine.transition(
                subjectId = UUID.randomUUID(),
                from = EvidenceAssertionState.OBSERVED,
                to = EvidenceAssertionState.DOCUMENTED,
                reason = "",
                evidenceIds = listOf(UUID.randomUUID()),
                actorId = UUID.randomUUID(),
                methodologyVersion = "safety-science-v1",
            )
        }
    }

    // ── Audit record correctness ────────────────────────────

    @Test
    fun `successful transition produces audit record with all fields`() {
        val subject = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val evidence = listOf(UUID.randomUUID(), UUID.randomUUID())

        val transition = machine.transition(
            subjectId = subject,
            from = EvidenceAssertionState.OBSERVED,
            to = EvidenceAssertionState.DOCUMENTED,
            reason = "Notarized document attached",
            evidenceIds = evidence,
            actorId = actor,
            methodologyVersion = "safety-science-v1",
        )

        assertEquals(subject, transition.subjectId)
        assertEquals(EvidenceAssertionState.OBSERVED, transition.from)
        assertEquals(EvidenceAssertionState.DOCUMENTED, transition.to)
        assertEquals("Notarized document attached", transition.reason)
        assertEquals(evidence, transition.evidenceIds)
        assertEquals(actor, transition.actorId)
        assertEquals("safety-science-v1", transition.methodologyVersion)
        assertNotNull(transition.id)
        assertNotNull(transition.occurredAt)
    }
}
