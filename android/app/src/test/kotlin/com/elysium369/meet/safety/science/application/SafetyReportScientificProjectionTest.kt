package com.elysium369.meet.safety.science.application

import com.elysium369.meet.safety.domain.SafetyReportCategory
import com.elysium369.meet.safety.domain.SourceRelation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyReportScientificProjectionTest {
    private val reportId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val evidenceId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"

    private fun input(
        includeHypothesis: Boolean = false,
        relation: SourceRelation = SourceRelation.SECOND_HAND,
        evidence: List<SafetyReportEvidenceInput> = emptyList(),
    ) = SafetyReportScientificProjectionInput(
        reportId = reportId,
        category = SafetyReportCategory.VIOLENT_INCIDENT,
        narrative = "La persona informante describe un incidente pendiente de corroboración.",
        factualClaim = "",
        sourceRelation = relation,
        scientificHypothesis = "Existe una repetición temporal del mismo tipo de incidente.",
        nullHypothesis = "Los incidentes son eventos aislados sin relación demostrada.",
        falsificationCriteria = "Una revisión de fechas y zonas no encuentra repetición.",
        enableScientificAnalysis = includeHypothesis,
        occurredAt = null,
        recordedAt = 1_800_000_000_000L,
        evidence = evidence,
    )

    @Test
    fun reportWithNoScientificOptInDoesNotInventAnInvestigativeHypothesis() {
        val projection = SafetyReportScientificProjectionFactory.build(input())
        assertNull(projection.hypothesis)
        assertEquals("NOT_ASSESSED", projection.claim.causalStatus)
        assertTrue(projection.claim.proposition.contains("no corroborada"))
        assertNull(projection.event.occurredAt)
        assertEquals(1_800_000_000_000L, projection.event.recordedAt)
    }

    @Test
    fun attachedEvidenceUsesItsRealIdsAndOnlyContextualizesTheClaim() {
        val evidenceHash = "a".repeat(64)
        val projection = SafetyReportScientificProjectionFactory.build(
            input(evidence = listOf(SafetyReportEvidenceInput(evidenceId, evidenceHash))),
        )
        assertEquals(1, projection.claimEvidenceLinks.size)
        assertEquals(evidenceId, projection.claimEvidenceLinks.single().evidenceId)
        assertEquals("CONTEXTUALIZES", projection.claimEvidenceLinks.single().relationType)
        assertTrue(projection.event.evidenceIdsJson.contains(evidenceId))
        assertTrue(projection.provenanceNodes.any { it.id == evidenceId && it.contentHash == evidenceHash })
        assertTrue(projection.provenanceEdges.any {
            it.fromId == evidenceId && it.toId == projection.claim.id && it.relation == "CONTEXTUALIZES_CLAIM"
        })
        assertFalse(projection.provenanceEdges.any { it.fromId == "report:$reportId" || it.toId == "report:$reportId" })
    }

    @Test
    fun directWitnessIsDistinguishedFromSecondHandReportsWithoutConfirmingOtherSources() {
        val direct = SafetyReportScientificProjectionFactory.build(input(relation = SourceRelation.DIRECT_WITNESS))
        val secondHand = SafetyReportScientificProjectionFactory.build(input(relation = SourceRelation.SECOND_HAND))
        assertEquals("OBSERVED", direct.event.assertionState)
        assertEquals("UNKNOWN", secondHand.event.assertionState)
        assertTrue(secondHand.claim.proposition.contains("SECOND_HAND"))
    }

    @Test
    fun explicitScientificOptInCreatesOnlyUserSpecifiedHypothesisWithNoAutomaticSupport() {
        val projection = SafetyReportScientificProjectionFactory.build(input(includeHypothesis = true))
        val hypothesis = projection.hypothesis!!
        assertTrue(hypothesis.proposition.contains("Existe una repetición temporal"))
        assertTrue(hypothesis.nullHypothesis!!.contains("eventos aislados"))
        assertTrue(hypothesis.falsificationCriteriaJson.contains("no encuentra repetición"))
        assertEquals("[]", hypothesis.supportingEvidenceIdsJson)
        assertEquals("[]", hypothesis.contradictingEvidenceIdsJson)
        assertTrue(projection.provenanceEdges.any {
            it.fromId == projection.claim.id && it.toId == hypothesis.id
        })
    }
}
