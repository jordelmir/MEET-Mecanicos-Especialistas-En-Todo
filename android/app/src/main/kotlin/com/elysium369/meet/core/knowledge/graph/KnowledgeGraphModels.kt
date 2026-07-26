package com.elysium369.meet.core.knowledge.graph

import kotlinx.serialization.Serializable

@Serializable
enum class KnowledgeNodeType {
    SYSTEM,
    ASSEMBLY,
    SECTION,
    COMPONENT,
    ALIAS,
    SYMPTOM,
    DTC,
    DIAGNOSTIC_TEST,
    MEASUREMENT,
    PROCEDURE,
    PROCEDURE_STEP,
    TOOL,
    SAFETY_WARNING,
    PART_CANDIDATE,
    VEHICLE_PROFILE,
    SOURCE_BLOCK,
    VISUAL_TARGET
}

@Serializable
enum class KnowledgeEdgeType {
    PART_OF,
    HAS_ALIAS,
    MAY_CAUSE,
    MAY_SET_DTC,
    AFFECTS,
    CONFIRMED_BY_TEST,
    HAS_DIAGNOSTIC_TEST,
    REQUIRES_TEST_BEFORE_REPLACE,
    USES_TOOL,
    HAS_WARNING,
    HAS_PROCEDURE,
    HAS_STEP,
    NEXT_STEP,
    SUGGESTS_PART_CANDIDATE,
    APPLIES_TO,
    EXCLUDED_FROM,
    SUPPORTED_BY_SOURCE,
    VISUALIZED_BY
}

@Serializable
enum class VehicleApplicabilityState {
    CONFIRMED,
    PROBABLE,
    CONDITIONAL,
    GENERIC,
    NOT_DOCUMENTED,
    NOT_APPLICABLE,
    CONFLICTED
}

@Serializable
enum class GraphConfidence { HIGH, MEDIUM, LOW, UNASSESSED }

@Serializable
enum class GraphReviewState { REVIEWED, REVIEW_REQUIRED, OBSERVED, CONFLICTED }

@Serializable
enum class VehicleMarketState { CONFIRMED, UNCONFIRMED, MULTIPLE }

@Serializable
enum class SourceRecordRole { COMPONENT_RECORD, REAL_CASE, SECTION_SHARD, LITERAL_BLOCK }

@Serializable
enum class ObservedEvidenceSubjectKind { NODE, EDGE, PROFILE, APPLICABILITY_RULE }

@Serializable
enum class ObservedEvidenceStatus { VERIFIED, PENDING_REVIEW, REJECTED }

@Serializable
data class SourceRef(
    val sourceDocumentId: String,
    val blockId: String,
    val textHash: String
) {
    fun isComplete(): Boolean =
        SOURCE_DOCUMENT_PATTERN.matches(sourceDocumentId) &&
            SOURCE_BLOCK_PATTERN.matches(blockId) &&
            SHA_256_PATTERN.matches(textHash)

    private companion object {
        val SOURCE_DOCUMENT_PATTERN = Regex("^document_[0-9]+$")
        val SOURCE_BLOCK_PATTERN = Regex("^block_[0-9]{6}_[0-9a-f]{10}$")
        val SHA_256_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}

@Serializable
data class CuratedPackInput(
    val packId: String,
    val packVersion: String,
    val path: String,
    val contentSha256: String,
    val authority: String,
    val reviewState: GraphReviewState,
    val reviewedBy: String? = null,
    val reviewedAt: String? = null,
    val reviewedContentSha256: String? = null
)

@Serializable
data class GraphSourceInputs(
    val corpusManifestPath: String,
    val corpusManifestSha256: String,
    val corpusId: String,
    val corpusVersion: String,
    val entityIndexPath: String,
    val entityIndexSha256: String,
    val curatedOverlayPath: String,
    val curatedOverlaySha256: String,
    val curatedPacks: List<CuratedPackInput>
)

@Serializable
data class KnowledgeNode(
    val id: String,
    val type: KnowledgeNodeType,
    val label: String,
    val sourceBlockIds: List<String>,
    val sourceRefs: List<SourceRef>,
    val curatedSourceIds: List<String>,
    val canonicalKey: String? = null,
    val description: String? = null,
    val sourceRecordRole: SourceRecordRole? = null
)

@Serializable
data class KnowledgeEdge(
    val id: String,
    val from: String,
    val to: String,
    val type: KnowledgeEdgeType,
    val sourceBlockIds: List<String>,
    val sourceRefs: List<SourceRef>,
    val curatedSourceIds: List<String>,
    val observedEvidenceIds: List<String>,
    val evidenceRequired: List<String>,
    val reviewState: GraphReviewState,
    val applicability: VehicleApplicabilityState,
    val confidence: GraphConfidence,
    val reviewedCuratedSourceIds: List<String> = emptyList(),
    val reason: String? = null,
    val requiresVinConfirmation: Boolean = false,
    val requiresOemConfirmation: Boolean = false,
    val requiresPhysicalConfirmation: Boolean = false,
    val sequenceId: String? = null,
    val sequenceOrder: Int? = null
)

@Serializable
data class VehicleGraphProfile(
    val id: String,
    val nodeId: String,
    val make: String,
    val models: List<String>,
    val year: Int,
    val engine: String,
    val transmission: String,
    val marketState: VehicleMarketState,
    val requiresVinConfirmation: Boolean,
    val applicability: VehicleApplicabilityState,
    val confidence: GraphConfidence,
    val reviewState: GraphReviewState,
    val sourceBlockIds: List<String>,
    val sourceRefs: List<SourceRef>,
    val curatedSourceIds: List<String>,
    val evidenceRequired: List<String>,
    val reviewedCuratedSourceIds: List<String> = emptyList(),
    val observedEvidenceIds: List<String> = emptyList()
)

@Serializable
data class VehicleApplicabilityRule(
    val id: String,
    val profileId: String,
    val canonicalKey: String,
    val state: VehicleApplicabilityState,
    val confidence: GraphConfidence,
    val reviewState: GraphReviewState,
    val reason: String,
    val sourceBlockIds: List<String>,
    val sourceRefs: List<SourceRef>,
    val curatedSourceIds: List<String>,
    val evidenceRequired: List<String>,
    val reviewedCuratedSourceIds: List<String> = emptyList(),
    val observedEvidenceIds: List<String> = emptyList()
)

@Serializable
data class StructuredObservedEvidence(
    val id: String,
    val subjectId: String,
    val subjectKind: ObservedEvidenceSubjectKind,
    val status: ObservedEvidenceStatus,
    val artifactSha256: String,
    val observationMethod: String,
    val observedBy: String,
    val observedAt: String,
    val reviewedBy: String,
    val reviewedAt: String
)

@Serializable
data class GraphStatistics(
    val sourceBlockCount: Int,
    val qualifiedSourceRefCount: Int,
    val bareSourceBlockIdCount: Int,
    val corpusSystemNodeCount: Int,
    val corpusSectionNodeCount: Int,
    val entityNodeCount: Int,
    val corpusComponentNodeCount: Int,
    val corpusRealCaseNodeCount: Int,
    val totalSystemNodeCount: Int,
    val totalSectionNodeCount: Int,
    val totalComponentNodeCount: Int,
    val totalSourceBlockNodeCount: Int,
    val baseNodeCount: Int,
    val structuralEdgeCount: Int,
    val curatedNodeCount: Int,
    val curatedEdgeCount: Int,
    val nodeCount: Int,
    val edgeCount: Int,
    val profileCount: Int,
    val applicabilityRuleCount: Int,
    val observedEvidenceCount: Int
)

@Serializable
data class AutomotiveKnowledgeGraph(
    val schemaVersion: Int,
    val graphId: String,
    val graphVersion: String,
    val sourceCorpusHash: String,
    val sourceInputs: GraphSourceInputs,
    val nodes: List<KnowledgeNode>,
    val edges: List<KnowledgeEdge>,
    val profiles: List<VehicleGraphProfile>,
    val applicabilityRules: List<VehicleApplicabilityRule>,
    val observedEvidence: List<StructuredObservedEvidence>,
    val statistics: GraphStatistics,
    val contentSha256: String
)

@Serializable
enum class GraphDirection { OUTGOING, INCOMING }

@Serializable
data class GraphNeighbor(
    val node: KnowledgeNode,
    val edge: KnowledgeEdge,
    val direction: GraphDirection
)

@Serializable
enum class GraphIntegrityStatus { NOT_LOADED, VALID, INVALID }

@Serializable
data class GraphLoadMetrics(
    val assetByteCount: Long,
    val parseDurationMillis: Long,
    val nodeCount: Int,
    val edgeCount: Int,
    val sourceRefCount: Int
) {
    companion object {
        val EMPTY = GraphLoadMetrics(
            assetByteCount = 0L,
            parseDurationMillis = 0L,
            nodeCount = 0,
            edgeCount = 0,
            sourceRefCount = 0
        )
    }
}
