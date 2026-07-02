package com.elysium369.meet.core.knowledge

import kotlinx.serialization.Serializable

/**
 * Source tier classification. Source-tier H is rejected at import time.
 * See .mavis/AGENT.md and the Knowledge OS spec for full policy.
 */
@Serializable
enum class SourceTier {
    A_OWNED_CREATED,
    B_PUBLIC_PERMISSIVE,
    C_GOVERNMENT_PUBLIC,
    D_VERIFIED_MECHANIC,
    E_COMMUNITY_VALIDATED,
    F_AI_GENERATED_PENDING_REVIEW,
    G_EXTERNAL_LINK_ONLY,
    H_REJECTED_UNKNOWN_LICENSE
}

@Serializable
enum class ValidationStatus {
    VALIDATED,
    NEEDS_REVIEW,
    AI_GENERATED,
    COMMUNITY_PENDING,
    EXTERNAL_ONLY,
    REJECTED,
    DEPRECATED
}

@Serializable
enum class DataQuality {
    REAL,
    STALE,
    SIMULATED,
    MISSING,
    INVALID
}

@Serializable
data class SourcePolicy(
    val tier: SourceTier,
    val licenseType: String,
    val attributionRequired: Boolean = false,
    val commercialUseAllowed: Boolean = true,
    val redistributionAllowed: Boolean = true,
    val notes: String = ""
)

/**
 * Conceptual node in the knowledge graph. No OEM data; everything is
 * tagged A_OWNED_CREATED unless otherwise noted.
 */
@Serializable
data class KnowledgeNode(
    val id: String,
    val type: String,           // e.g. "VehicleMake", "Dtc", "Component"
    val name: String,
    val description: String = "",
    val sourceTier: SourceTier = SourceTier.A_OWNED_CREATED,
    val validationStatus: ValidationStatus = ValidationStatus.VALIDATED
)

@Serializable
data class KnowledgeEdge(
    val id: String,
    val from: String,           // node id
    val to: String,             // node id
    val type: String            // e.g. "BELONGS_TO", "PART_OF", "POWERS"
)

@Serializable
data class ValidationRule(
    val id: String,
    val rule: String
)

/**
 * A versioned knowledge pack. Loaded from `assets/knowledge/packs/[id].json`.
 */
@Serializable
data class KnowledgePack(
    val packId: String,
    val title: String,
    val domain: String,
    val schemaVersion: Int,
    val packVersion: String,
    val language: String = "en-US",
    val generatedAtEpochMs: Long = 0L,
    val sourcePolicy: SourcePolicy,
    val disclaimer: String = "",
    val nodes: List<KnowledgeNode> = emptyList(),
    val edges: List<KnowledgeEdge> = emptyList(),
    val validationRules: List<ValidationRule> = emptyList()
)

/**
 * Outcome of a pack import. Lists which nodes/edges were accepted,
 * which were rejected, and why.
 */
sealed class PackImportResult {
    data class Success(
        val packId: String,
        val nodesAccepted: Int,
        val edgesAccepted: Int,
        val rejectedNodeIds: List<String> = emptyList(),
        val rejectedEdgeIds: List<String> = emptyList()
    ) : PackImportResult()

    data class Rejected(
        val packId: String,
        val reason: String
    ) : PackImportResult()
}
