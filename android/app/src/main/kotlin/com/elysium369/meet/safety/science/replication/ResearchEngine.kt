package com.elysium369.meet.safety.science.replication

import com.elysium369.meet.safety.science.analysis.BiasAssessment
import com.elysium369.meet.safety.science.domain.ExternalIdentifier
import java.time.Instant
import java.util.UUID

// ── Research Datasets (§30) ─────────────────────────────────────────

/**
 * Immutable research dataset. New version = new dataset object.
 * dataset-v1 NEVER silently changes.
 */
data class ResearchDataset(
    val id: UUID,
    val name: String,
    val version: String,
    val evidenceIds: List<UUID>,
    val claimIds: List<UUID>,
    val eventIds: List<UUID>,
    val datasetHash: String,
    val methodologyVersion: String,
    val createdAt: Instant,
)

// ── Research Runs (§31) ─────────────────────────────────────────────

/**
 * Reproducibility contract: Dataset + Code + Methodology + Parameters = Result.
 */
data class ResearchRun(
    val id: UUID,
    val datasetId: UUID,
    val datasetHash: String,
    val methodologyVersion: String,
    val codeCommit: String,
    val parameters: Map<String, String>,
    val resultArtifactHash: String,
    val biasAssessment: BiasAssessment?,
    val createdAt: Instant,
)

// ── Replication Studies (§32) ───────────────────────────────────────

data class ReplicationStudy(
    val id: UUID,
    val originalResearchRunId: UUID,
    val replicatorEntityId: UUID,
    val institutionEntityId: UUID?,
    val datasetVersion: String,
    val methodologyVersion: String,
    val independentDatasetHash: String?,
    val result: ReplicationResult,
    val deviations: List<String>,
    val createdAt: Instant,
)

enum class ReplicationResult {
    SUCCESSFUL,
    PARTIAL,
    FAILED,
    INCONCLUSIVE,
    NOT_REPRODUCIBLE,
}

// ── Peer Review (§33) ───────────────────────────────────────────────

/**
 * Peer review does NOT automatically convert a conclusion to AUTHORITATIVE.
 */
data class PeerReview(
    val id: UUID,
    val publicationId: UUID,
    val reviewerEntityId: UUID,
    val methodologyReviewed: Boolean,
    val evidenceReviewed: Boolean,
    val analysisReviewed: Boolean,
    val provenanceReviewed: Boolean,
    val decision: PeerReviewDecision,
    val conflictOfInterestDeclared: Boolean,
    val createdAt: Instant,
)

enum class PeerReviewDecision {
    ACCEPT,
    MINOR_REVISION,
    MAJOR_REVISION,
    REJECT,
    CONDITIONAL,
}

// ── Research Publication (§34) ──────────────────────────────────────

/**
 * Publication lifecycle: DRAFT → UNDER_REVIEW → PEER_REVIEWED →
 * PUBLISHED → CORRECTED | RETRACTED.
 *
 * PUBLISHED → delete is PROHIBITED. History persists.
 * Corrections create a new version with [supersedesPublicationId].
 */
data class ResearchPublication(
    val id: UUID,
    val title: String,
    val abstractText: String,
    val researchRunId: UUID,
    val datasetHash: String,
    val methodologyHash: String,
    val codeCommit: String,
    val evidenceManifestHash: String,
    val peerReviewIds: List<UUID>,
    val replicationIds: List<UUID>,
    val limitations: List<String>,
    val sensitivity: com.elysium369.meet.safety.science.domain.ResearchSensitivity,
    val supersedesPublicationId: UUID?,
    val status: PublicationStatus,
)

enum class PublicationStatus {
    DRAFT,
    UNDER_REVIEW,
    PEER_REVIEWED,
    PUBLISHED,
    RETRACTED,
    CORRECTED,
}

// ── External Audit (§73) ────────────────────────────────────────────

data class ExternalAudit(
    val id: UUID,
    val researchRunId: UUID,
    val auditorEntityId: UUID,
    val institutionEntityId: UUID?,
    val scope: List<String>,
    val findings: List<String>,
    val unresolvedIssues: List<String>,
    val outcome: ExternalAuditOutcome,
)

enum class ExternalAuditOutcome {
    PASSED,
    PASSED_WITH_FINDINGS,
    FAILED,
    INCONCLUSIVE,
}

// ── Researcher Identity (§74-75) ────────────────────────────────────

data class ResearcherProfile(
    val entityId: UUID,
    val displayName: String,
    val affiliations: List<UUID>,
    val externalIds: List<ExternalIdentifier>,
)

data class ConflictOfInterestDeclaration(
    val researcherId: UUID,
    val researchId: UUID,
    val declared: Boolean,
    val description: String?,
)
