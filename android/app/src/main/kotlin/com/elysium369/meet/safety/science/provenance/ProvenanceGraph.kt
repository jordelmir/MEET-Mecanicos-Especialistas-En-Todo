package com.elysium369.meet.safety.science.provenance

import java.time.Instant
import java.util.UUID

/**
 * A node in the evidence provenance DAG.
 *
 * Every evidence artifact, transformation, analysis, and publication
 * is tracked as a node with an optional content hash for integrity.
 */
data class ProvenanceNode(
    val id: UUID,
    val nodeType: ProvenanceNodeType,
    val hash: String?,
    val createdAt: Instant,
)

enum class ProvenanceNodeType {
    ORIGINAL_EVIDENCE,
    DERIVED_EVIDENCE,
    TRANSFORMATION,
    ANALYSIS,
    DATASET,
    REPORT,
    PUBLICATION,
    REPLICATION,
}

/**
 * A directed edge in the provenance DAG.
 *
 * Read as: [fromId] --[relation]--> [toId].
 * Example: ORIGINAL_EVIDENCE --DERIVED_FROM--> DERIVED_EVIDENCE
 */
data class ProvenanceEdge(
    val fromId: UUID,
    val toId: UUID,
    val relation: ProvenanceRelation,
)

enum class ProvenanceRelation {
    DERIVED_FROM,
    GENERATED_FROM,
    ANALYZED_BY,
    PUBLISHED_AS,
    REPLICATED_AS,
}
