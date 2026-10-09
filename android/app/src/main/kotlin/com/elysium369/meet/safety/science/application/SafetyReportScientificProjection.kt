package com.elysium369.meet.safety.science.application

import com.elysium369.meet.safety.domain.SafetyReportCategory
import com.elysium369.meet.safety.domain.SourceRelation
import com.elysium369.meet.safety.science.data.SciClaimEntity
import com.elysium369.meet.safety.science.data.SciClaimEvidenceEntity
import com.elysium369.meet.safety.science.data.SciEventEntity
import com.elysium369.meet.safety.science.data.SciHypothesisEntity
import com.elysium369.meet.safety.science.data.SciProvenanceEdgeEntity
import com.elysium369.meet.safety.science.data.SciProvenanceNodeEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Builds an honest LOCAL scientific projection for a received Safety report.
 *
 * This does not assert that the incident is confirmed and does not create a
 * remote receipt. Attachments only CONTEXTUALIZE a claim until an authorized
 * reviewer establishes a stronger relationship.
 */
data class SafetyReportEvidenceInput(
    val evidenceId: String,
    val contentSha256: String,
)

data class SafetyReportScientificProjection(
    val claim: SciClaimEntity,
    val hypothesis: SciHypothesisEntity?,
    val event: SciEventEntity,
    val claimEvidenceLinks: List<SciClaimEvidenceEntity>,
    val provenanceNodes: List<SciProvenanceNodeEntity>,
    val provenanceEdges: List<SciProvenanceEdgeEntity>,
)

data class SafetyReportScientificProjectionInput(
    val reportId: String,
    val category: SafetyReportCategory,
    val narrative: String,
    val factualClaim: String,
    val sourceRelation: SourceRelation,
    val scientificHypothesis: String,
    val nullHypothesis: String,
    val falsificationCriteria: String,
    val enableScientificAnalysis: Boolean,
    val occurredAt: Long?,
    val recordedAt: Long,
    val evidence: List<SafetyReportEvidenceInput>,
)

object SafetyReportScientificProjectionFactory {
    fun build(input: SafetyReportScientificProjectionInput): SafetyReportScientificProjection {
        val reportId = input.reportId.trim()
        UUID.fromString(reportId)
        require(input.recordedAt > 0) { "Report record time must be known" }
        require(input.evidence.map { it.evidenceId }.distinct().size == input.evidence.size) {
            "Evidence identifiers must be unique within a report"
        }
        input.evidence.forEach { evidence ->
            UUID.fromString(evidence.evidenceId)
            require(evidence.contentSha256.matches(Regex("^[a-f0-9]{64}$"))) {
                "Evidence hash must be a lowercase SHA-256 digest"
            }
        }

        val reportNodeId = "safety-report:$reportId"
        val shortId = reportId.take(8)
        val claimId = stableId(reportId, "claim")
        val eventId = stableId(reportId, "event")
        val hypothesisText = input.scientificHypothesis.trim()
        val hypothesisId = if (input.enableScientificAnalysis && hypothesisText.isNotBlank()) {
            stableId(reportId, "hypothesis")
        } else {
            null
        }
        val now = input.recordedAt
        val evidenceIds = input.evidence.map { it.evidenceId }
        val claimBody = input.factualClaim.trim().ifBlank { input.narrative.trim() }.take(4_000)
        val claim = SciClaimEntity(
            id = claimId,
            proposition = "Reporte #$shortId registra una afirmación no corroborada " +
                "(categoría=${input.category.name}; fuente declarada=${input.sourceRelation.name}): $claimBody",
            predicate = "REPORTED_INCIDENT",
            assertionState = "OBSERVED", // The received report is observed; the underlying incident is not thereby confirmed.
            causalStatus = if (hypothesisId != null) "HYPOTHESIS_FORMULATED" else "NOT_ASSESSED",
            occurredAt = input.occurredAt,
            knownAt = now,
            createdAt = now,
            updatedAt = now,
        )

        val hypothesis = hypothesisId?.let { id ->
            SciHypothesisEntity(
                id = id,
                proposition = "[Reporte #$shortId] $hypothesisText",
                nullHypothesis = input.nullHypothesis.trim().takeIf(String::isNotBlank),
                // Attached material is not automatically deemed supporting evidence.
                supportingEvidenceIdsJson = "[]",
                contradictingEvidenceIdsJson = "[]",
                alternativeHypothesisIdsJson = "[]",
                falsificationCriteriaJson = jsonArray(
                    input.falsificationCriteria.trim().takeIf(String::isNotBlank)?.let(::listOf) ?: emptyList(),
                ),
                status = "PROPOSED",
                methodologyVersion = "safety-science-v1",
                createdAt = now,
                updatedAt = now,
            )
        }

        val eventState = when (input.sourceRelation) {
            SourceRelation.DIRECT_WITNESS -> "OBSERVED"
            SourceRelation.FAMILY_OR_NEIGHBOR,
            SourceRelation.SECOND_HAND,
            SourceRelation.DOCUMENTARY,
            SourceRelation.JOURNALISTIC,
            SourceRelation.PUBLIC_RECORD,
            SourceRelation.INSTITUTIONAL,
            SourceRelation.UNKNOWN -> "UNKNOWN"
        }
        val event = SciEventEntity(
            id = eventId,
            eventType = input.category.name,
            occurredAt = input.occurredAt,
            knownAt = now,
            recordedAt = now,
            evidenceIdsJson = jsonArray(evidenceIds),
            claimIdsJson = jsonArray(listOf(claimId)),
            assertionState = eventState,
            createdAt = now,
        )

        val nodes = buildList {
            add(SciProvenanceNodeEntity(reportNodeId, "SAFETY_REPORT", createdAt = now))
            add(SciProvenanceNodeEntity(claimId, "REPORTED_CLAIM", createdAt = now))
            add(SciProvenanceNodeEntity(eventId, "REPORTED_EVENT", createdAt = now))
            input.evidence.forEach { evidence ->
                add(
                    SciProvenanceNodeEntity(
                        id = evidence.evidenceId,
                        nodeType = "SAFETY_EVIDENCE",
                        contentHash = evidence.contentSha256,
                        createdAt = now,
                    ),
                )
            }
            hypothesisId?.let { id ->
                add(SciProvenanceNodeEntity(id, "INVESTIGATIVE_HYPOTHESIS", createdAt = now))
            }
        }

        val edges = buildList {
            add(SciProvenanceEdgeEntity(reportNodeId, claimId, "RECORDED_CLAIM", now))
            add(SciProvenanceEdgeEntity(reportNodeId, eventId, "RECORDED_EVENT", now))
            add(SciProvenanceEdgeEntity(claimId, eventId, "DESCRIBES_REPORTED_EVENT", now))
            input.evidence.forEach { evidence ->
                add(SciProvenanceEdgeEntity(reportNodeId, evidence.evidenceId, "ATTACHED_EVIDENCE", now))
                add(SciProvenanceEdgeEntity(evidence.evidenceId, claimId, "CONTEXTUALIZES_CLAIM", now))
            }
            hypothesisId?.let { id ->
                add(SciProvenanceEdgeEntity(reportNodeId, id, "HAS_HYPOTHESIS", now))
                add(SciProvenanceEdgeEntity(claimId, id, "EXAMINED_BY_HYPOTHESIS", now))
            }
        }

        val evidenceLinks = input.evidence.map { evidence ->
            SciClaimEvidenceEntity(
                claimId = claimId,
                evidenceId = evidence.evidenceId,
                relationType = "CONTEXTUALIZES",
                createdAt = now,
            )
        }

        return SafetyReportScientificProjection(
            claim = claim,
            hypothesis = hypothesis,
            event = event,
            claimEvidenceLinks = evidenceLinks,
            provenanceNodes = nodes,
            provenanceEdges = edges,
        )
    }

    private fun stableId(reportId: String, kind: String): String =
        UUID.nameUUIDFromBytes("elysium-safety-report:$reportId:$kind".toByteArray(Charsets.UTF_8)).toString()

    private fun jsonArray(values: List<String>): String =
        JsonArray(values.map { JsonPrimitive(it) }).toString()
}
