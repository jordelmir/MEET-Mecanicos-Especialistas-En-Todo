package com.elysium369.meet.core.knowledge.graph

import android.content.Context
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

const val AUTOMOTIVE_KNOWLEDGE_GRAPH_ASSET =
    "knowledge/graph/automotive_knowledge_graph.json"

const val EXPECTED_AUTOMOTIVE_GRAPH_CONTENT_SHA256 =
    "2617bfa199a0e5b88f9ccb03ed46741d657f7f9fe00ba8aefe8f17926d4ab466"

class AutomotiveKnowledgeGraphValidationException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

@OptIn(ExperimentalSerializationApi::class)
object AutomotiveKnowledgeGraphParser {
    private val strictJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = false
    }

    fun decode(raw: String): AutomotiveKnowledgeGraph =
        decodeWithExpectedContentSha256(raw, EXPECTED_AUTOMOTIVE_GRAPH_CONTENT_SHA256)

    internal fun decodeWithExpectedContentSha256(
        raw: String,
        expectedHash: String
    ): AutomotiveKnowledgeGraph {
        try {
            if (!SHA_256_PATTERN.matches(expectedHash)) {
                invalid("Expected automotive knowledge graph release hash is malformed")
            }
            val root = strictJson.parseToJsonElement(raw) as? JsonObject
                ?: invalid("Automotive knowledge graph root must be a JSON object")
            val claimedHash = root["contentSha256"]?.jsonPrimitive?.content
                ?: invalid("Automotive knowledge graph is missing contentSha256")
            if (!SHA_256_PATTERN.matches(claimedHash)) {
                invalid("Automotive knowledge graph contentSha256 is malformed")
            }
            if (claimedHash != expectedHash) {
                invalid("Automotive knowledge graph is not the pinned release payload")
            }

            val unhashedRoot = JsonObject(root.filterKeys { it != "contentSha256" })
            val actualHash = sha256(canonicalize(unhashedRoot).encodeToByteArray())
            if (actualHash != claimedHash) {
                invalid("Automotive knowledge graph content hash mismatch")
            }

            val graph = strictJson.decodeFromJsonElement<AutomotiveKnowledgeGraph>(root)
            validate(graph)
            return graph
        } catch (error: AutomotiveKnowledgeGraphValidationException) {
            throw error
        } catch (error: Exception) {
            throw AutomotiveKnowledgeGraphValidationException(
                "Automotive knowledge graph could not be decoded or validated: ${error.message}",
                error
            )
        }
    }

    private fun validate(graph: AutomotiveKnowledgeGraph) {
        if (graph.schemaVersion != EXPECTED_SCHEMA_VERSION) {
            invalid("Unsupported automotive knowledge graph schema ${graph.schemaVersion}")
        }
        if (graph.sourceInputs.corpusId != EXPECTED_CORPUS_ID) {
            invalid("Unexpected external automotive corpus ID")
        }
        if (graph.sourceInputs.corpusVersion != EXPECTED_CORPUS_VERSION) {
            invalid("Unexpected external automotive corpus version")
        }
        if (
            graph.sourceCorpusHash != EXPECTED_CORPUS_HASH ||
            graph.sourceInputs.corpusManifestSha256 != EXPECTED_CORPUS_HASH
        ) {
            invalid("Unexpected external automotive corpus hash")
        }
        validateHash(graph.sourceInputs.entityIndexSha256, "entity index")
        validateHash(graph.sourceInputs.curatedOverlaySha256, "curated overlay")

        val nodeIds = uniqueIds(graph.nodes, KnowledgeNode::id, "node")
        val edgeIds = uniqueIds(graph.edges, KnowledgeEdge::id, "edge")
        val profileIds = uniqueIds(graph.profiles, VehicleGraphProfile::id, "profile")
        val ruleIds = uniqueIds(
            graph.applicabilityRules,
            VehicleApplicabilityRule::id,
            "applicability rule"
        )
        val evidenceIds = uniqueIds(
            graph.observedEvidence,
            StructuredObservedEvidence::id,
            "observed evidence"
        )
        val packIds = uniqueIds(
            graph.sourceInputs.curatedPacks,
            CuratedPackInput::packId,
            "curated pack"
        )

        val normalizedDtcCodes = graph.nodes
            .filter { it.type == KnowledgeNodeType.DTC }
            .map { node ->
                val canonicalCode = node.canonicalKey
                    ?: invalid("DTC node ${node.id} is missing a canonical code")
                normalizeGraphDtc(canonicalCode).also { normalized ->
                    if (normalized.isBlank()) invalid("DTC node ${node.id} has a blank canonical code")
                }
            }
        val duplicateDtc = normalizedDtcCodes.groupingBy { it }.eachCount()
            .entries.firstOrNull { it.value > 1 }
        if (duplicateDtc != null) {
            invalid("Duplicate normalized DTC code ${duplicateDtc.key}")
        }

        graph.nodes.forEach { node ->
            validateSourceCarrier(node.id, node.sourceBlockIds, node.sourceRefs)
            validateUniqueStrings(node.curatedSourceIds, "curated sources for node ${node.id}")
            validateKnownPacks(node.id, node.curatedSourceIds, packIds)
        }

        val nodesById = graph.nodes.associateBy(KnowledgeNode::id)
        graph.edges.forEach { edge ->
            if (edge.from == edge.to) invalid("Self-referential edge ${edge.id}")
            if (edge.from !in nodeIds || edge.to !in nodeIds) invalid("Orphan edge ${edge.id}")
            validateSourceCarrier(edge.id, edge.sourceBlockIds, edge.sourceRefs)
            validateUniqueStrings(edge.curatedSourceIds, "curated sources for edge ${edge.id}")
            validateUniqueStrings(edge.reviewedCuratedSourceIds, "reviewed sources for edge ${edge.id}")
            validateUniqueStrings(edge.observedEvidenceIds, "observed evidence for edge ${edge.id}")
            validateUniqueStrings(edge.evidenceRequired, "required evidence for edge ${edge.id}")
            validateKnownPacks(edge.id, edge.curatedSourceIds, packIds)
            if (edge.sequenceOrder != null && edge.sequenceOrder < 1) {
                invalid("Invalid sequence order for edge ${edge.id}")
            }
        }

        graph.profiles.forEach { profile ->
            if (nodesById[profile.nodeId]?.type != KnowledgeNodeType.VEHICLE_PROFILE) {
                invalid("Profile ${profile.id} does not reference a VEHICLE_PROFILE node")
            }
            validateSourceCarrier(profile.id, profile.sourceBlockIds, profile.sourceRefs)
            validateUniqueStrings(profile.curatedSourceIds, "curated sources for profile ${profile.id}")
            validateUniqueStrings(profile.reviewedCuratedSourceIds, "reviewed sources for profile ${profile.id}")
            validateUniqueStrings(profile.observedEvidenceIds, "observed evidence for profile ${profile.id}")
            validateKnownPacks(profile.id, profile.curatedSourceIds, packIds)
        }

        val ruleKeys = graph.applicabilityRules.map { it.profileId to it.canonicalKey }
        if (ruleKeys.size != ruleKeys.toSet().size) {
            invalid("Duplicate applicability rule profile/canonical key")
        }
        graph.applicabilityRules.forEach { rule ->
            if (rule.profileId !in profileIds) invalid("Unknown profile for applicability rule ${rule.id}")
            validateSourceCarrier(rule.id, rule.sourceBlockIds, rule.sourceRefs)
            validateUniqueStrings(rule.curatedSourceIds, "curated sources for rule ${rule.id}")
            validateUniqueStrings(rule.reviewedCuratedSourceIds, "reviewed sources for rule ${rule.id}")
            validateUniqueStrings(rule.observedEvidenceIds, "observed evidence for rule ${rule.id}")
            validateKnownPacks(rule.id, rule.curatedSourceIds, packIds)
        }

        val packsById = graph.sourceInputs.curatedPacks.associateBy(CuratedPackInput::packId)
        graph.sourceInputs.curatedPacks.forEach(::validatePack)

        graph.observedEvidence.forEach { evidence ->
            if (evidence.status != ObservedEvidenceStatus.VERIFIED) {
                invalid("Observed evidence ${evidence.id} is not VERIFIED")
            }
            validateHash(evidence.artifactSha256, "observed evidence ${evidence.id}")
            if (
                evidence.subjectId.isBlank() ||
                evidence.observationMethod.isBlank() ||
                evidence.observedBy.isBlank() ||
                evidence.reviewedBy.isBlank() ||
                !UTC_TIMESTAMP_PATTERN.matches(evidence.observedAt) ||
                !UTC_TIMESTAMP_PATTERN.matches(evidence.reviewedAt)
            ) {
                invalid("Observed evidence ${evidence.id} has incomplete reviewer metadata")
            }
            val subjectExists = when (evidence.subjectKind) {
                ObservedEvidenceSubjectKind.NODE -> evidence.subjectId in nodeIds
                ObservedEvidenceSubjectKind.EDGE -> evidence.subjectId in edgeIds
                ObservedEvidenceSubjectKind.PROFILE -> evidence.subjectId in profileIds
                ObservedEvidenceSubjectKind.APPLICABILITY_RULE -> evidence.subjectId in ruleIds
            }
            if (!subjectExists) invalid("Observed evidence ${evidence.id} has an unknown subject")
        }
        val evidenceById = graph.observedEvidence.associateBy(StructuredObservedEvidence::id)

        graph.edges.forEach { edge ->
            validateAuthorityCarrier(
                id = edge.id,
                kind = ObservedEvidenceSubjectKind.EDGE,
                state = edge.applicability,
                curatedSourceIds = edge.curatedSourceIds,
                reviewedCuratedSourceIds = edge.reviewedCuratedSourceIds,
                observedEvidenceIds = edge.observedEvidenceIds,
                packsById = packsById,
                evidenceById = evidenceById
            )
        }
        graph.profiles.forEach { profile ->
            validateAuthorityCarrier(
                id = profile.id,
                kind = ObservedEvidenceSubjectKind.PROFILE,
                state = profile.applicability,
                curatedSourceIds = profile.curatedSourceIds,
                reviewedCuratedSourceIds = profile.reviewedCuratedSourceIds,
                observedEvidenceIds = profile.observedEvidenceIds,
                packsById = packsById,
                evidenceById = evidenceById
            )
        }
        graph.applicabilityRules.forEach { rule ->
            validateAuthorityCarrier(
                id = rule.id,
                kind = ObservedEvidenceSubjectKind.APPLICABILITY_RULE,
                state = rule.state,
                curatedSourceIds = rule.curatedSourceIds,
                reviewedCuratedSourceIds = rule.reviewedCuratedSourceIds,
                observedEvidenceIds = rule.observedEvidenceIds,
                packsById = packsById,
                evidenceById = evidenceById
            )
        }

        val qualifiedRefs = graph.nodes.flatMap(KnowledgeNode::sourceRefs).toSet()
        val bareBlockIds = qualifiedRefs.map(SourceRef::blockId).toSet()
        val corpusSystemCount = graph.nodes.count {
            it.type == KnowledgeNodeType.SYSTEM && it.id.startsWith(CORPUS_SYSTEM_PREFIX)
        }
        val corpusSectionCount = graph.nodes.count {
            it.type == KnowledgeNodeType.SECTION && it.id.startsWith(CORPUS_SECTION_PREFIX)
        }
        val entityNodes = graph.nodes.filter { it.id.startsWith(CORPUS_ENTITY_PREFIX) }
        val corpusComponentCount = entityNodes.count {
            it.type == KnowledgeNodeType.COMPONENT &&
                it.sourceRecordRole == SourceRecordRole.COMPONENT_RECORD
        }
        val corpusRealCaseCount = entityNodes.count {
            it.type == KnowledgeNodeType.SOURCE_BLOCK &&
                it.sourceRecordRole == SourceRecordRole.REAL_CASE
        }
        val baseNodeCount = corpusSystemCount + corpusSectionCount + entityNodes.size
        val structuralEdgeCount = graph.edges.count { it.id.startsWith(CORPUS_EDGE_PREFIX) }
        val actualStatistics = GraphStatistics(
            sourceBlockCount = qualifiedRefs.size,
            qualifiedSourceRefCount = qualifiedRefs.size,
            bareSourceBlockIdCount = bareBlockIds.size,
            corpusSystemNodeCount = corpusSystemCount,
            corpusSectionNodeCount = corpusSectionCount,
            entityNodeCount = entityNodes.size,
            corpusComponentNodeCount = corpusComponentCount,
            corpusRealCaseNodeCount = corpusRealCaseCount,
            totalSystemNodeCount = graph.nodes.count { it.type == KnowledgeNodeType.SYSTEM },
            totalSectionNodeCount = graph.nodes.count { it.type == KnowledgeNodeType.SECTION },
            totalComponentNodeCount = graph.nodes.count { it.type == KnowledgeNodeType.COMPONENT },
            totalSourceBlockNodeCount = graph.nodes.count { it.type == KnowledgeNodeType.SOURCE_BLOCK },
            baseNodeCount = baseNodeCount,
            structuralEdgeCount = structuralEdgeCount,
            curatedNodeCount = graph.nodes.size - baseNodeCount,
            curatedEdgeCount = graph.edges.size - structuralEdgeCount,
            nodeCount = graph.nodes.size,
            edgeCount = graph.edges.size,
            profileCount = graph.profiles.size,
            applicabilityRuleCount = graph.applicabilityRules.size,
            observedEvidenceCount = graph.observedEvidence.size
        )
        if (graph.statistics != actualStatistics) invalid("Graph statistics do not match graph contents")
        if (graph.statistics != REQUIRED_STATISTICS) invalid("Graph corpus statistics are incomplete or unexpected")
    }

    private fun validatePack(pack: CuratedPackInput) {
        validateHash(pack.contentSha256, "curated pack ${pack.packId}")
        if (pack.reviewState != GraphReviewState.REVIEWED) return
        if (
            pack.reviewedBy.isNullOrBlank() ||
            pack.reviewedAt == null ||
            !UTC_TIMESTAMP_PATTERN.matches(pack.reviewedAt) ||
            pack.reviewedContentSha256 != pack.contentSha256
        ) {
            invalid("REVIEWED curated pack ${pack.packId} lacks exact hash attestation")
        }
    }

    private fun validateAuthorityCarrier(
        id: String,
        kind: ObservedEvidenceSubjectKind,
        state: VehicleApplicabilityState,
        curatedSourceIds: List<String>,
        reviewedCuratedSourceIds: List<String>,
        observedEvidenceIds: List<String>,
        packsById: Map<String, CuratedPackInput>,
        evidenceById: Map<String, StructuredObservedEvidence>
    ) {
        if (!curatedSourceIds.containsAll(reviewedCuratedSourceIds)) {
            invalid("Reviewed curated sources are not a subset for $id")
        }
        reviewedCuratedSourceIds.forEach { packId ->
            if (packsById[packId]?.reviewState != GraphReviewState.REVIEWED) {
                invalid("Reviewed curated source $packId lacks REVIEWED authority for $id")
            }
        }
        val verifiedEvidence = observedEvidenceIds.map { evidenceId ->
            val evidence = evidenceById[evidenceId]
                ?: invalid("Unknown observed evidence $evidenceId referenced by $id")
            if (
                evidence.status != ObservedEvidenceStatus.VERIFIED ||
                evidence.subjectKind != kind ||
                evidence.subjectId != id
            ) {
                invalid("Observed evidence $evidenceId is not correctly bound to $id")
            }
            evidence
        }
        if (
            state == VehicleApplicabilityState.CONFIRMED &&
            reviewedCuratedSourceIds.isEmpty() &&
            verifiedEvidence.isEmpty()
        ) {
            invalid("CONFIRMED $kind $id lacks reviewed or verified authority")
        }
    }

    private fun validateSourceCarrier(
        id: String,
        sourceBlockIds: List<String>,
        sourceRefs: List<SourceRef>
    ) {
        validateUniqueStrings(sourceBlockIds, "source block IDs for $id")
        if (sourceRefs.any { !it.isComplete() }) invalid("Incomplete source reference for $id")
        if (sourceRefs.size != sourceRefs.toSet().size) invalid("Duplicate source reference for $id")
        if (sourceBlockIds.toSet() != sourceRefs.map(SourceRef::blockId).toSet()) {
            invalid("sourceBlockIds do not match sourceRefs for $id")
        }
    }

    private fun validateKnownPacks(id: String, sourceIds: List<String>, packIds: Set<String>) {
        val unknown = sourceIds.toSet() - packIds
        if (unknown.isNotEmpty()) invalid("Unknown curated source for $id: ${unknown.first()}")
    }

    private fun validateUniqueStrings(values: List<String>, label: String) {
        if (values.any(String::isBlank) || values.size != values.toSet().size) {
            invalid("$label contains blank or duplicate values")
        }
    }

    private fun validateHash(hash: String, label: String) {
        if (!SHA_256_PATTERN.matches(hash)) invalid("Invalid SHA-256 for $label")
    }

    private fun <T> uniqueIds(
        values: List<T>,
        idOf: (T) -> String,
        label: String
    ): Set<String> {
        val ids = values.map(idOf)
        if (ids.any(String::isBlank)) invalid("Missing $label ID")
        val duplicate = ids.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
        if (duplicate != null) invalid("Duplicate $label ID ${duplicate.key}")
        return ids.toSet()
    }

    private fun canonicalize(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries.sortedBy(Map.Entry<String, JsonElement>::key)
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
                "${JsonPrimitive(key)}:${canonicalize(value)}"
            }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]", separator = ",") {
            canonicalize(it)
        }
        else -> element.toString()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun invalid(message: String): Nothing =
        throw AutomotiveKnowledgeGraphValidationException(message)

    private val SHA_256_PATTERN = Regex("^[0-9a-f]{64}$")
    private val UTC_TIMESTAMP_PATTERN =
        Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")
    private const val EXPECTED_SCHEMA_VERSION = 1
    private const val EXPECTED_CORPUS_ID = "meet_owner_proprietary_parts_corpus"
    private const val EXPECTED_CORPUS_VERSION = "1.0.0"
    private const val EXPECTED_CORPUS_HASH =
        "7a4a2f2f328bf422ea1c4d987f88eb093e664d6cf4e53609282506d4261d960f"
    private const val CORPUS_SYSTEM_PREFIX = "corpus_system_"
    private const val CORPUS_SECTION_PREFIX = "corpus_section_"
    private const val CORPUS_ENTITY_PREFIX = "corpus_entity_"
    private const val CORPUS_EDGE_PREFIX = "corpus_edge_"

    private val REQUIRED_STATISTICS = GraphStatistics(
        sourceBlockCount = 74_648,
        qualifiedSourceRefCount = 74_648,
        bareSourceBlockIdCount = 74_638,
        corpusSystemNodeCount = 26,
        corpusSectionNodeCount = 347,
        entityNodeCount = 5_050,
        corpusComponentNodeCount = 4_753,
        corpusRealCaseNodeCount = 297,
        totalSystemNodeCount = 28,
        totalSectionNodeCount = 347,
        totalComponentNodeCount = 4_759,
        totalSourceBlockNodeCount = 297,
        baseNodeCount = 5_423,
        structuralEdgeCount = 5_397,
        curatedNodeCount = 23,
        curatedEdgeCount = 14,
        nodeCount = 5_446,
        edgeCount = 5_411,
        profileCount = 1,
        applicabilityRuleCount = 8,
        observedEvidenceCount = 0
    )
}

/**
 * Lazy fail-closed graph access. The first query loads and validates the full asset synchronously;
 * Android callers must dispatch that first access away from the main thread.
 */
class AutomotiveKnowledgeGraphRepository(
    private val assetLoader: () -> ByteArray
) {
    constructor(context: Context) : this(
        assetLoader = {
            context.applicationContext.assets.open(AUTOMOTIVE_KNOWLEDGE_GRAPH_ASSET)
                .use { it.readBytes() }
        }
    )

    @Volatile
    private var loadState: LoadState = LoadState.NotLoaded

    @Volatile
    private var metrics: GraphLoadMetrics = GraphLoadMetrics.EMPTY

    fun node(id: String): KnowledgeNode? = index()?.nodesById?.get(id)

    fun nodesByCanonicalKey(key: String): List<KnowledgeNode> =
        index()?.nodesByCanonicalKey?.get(key).orEmpty()

    fun nodeForCatalogEntity(rawEntityId: String): KnowledgeNode? =
        node("corpus_entity_$rawEntityId")

    fun neighbors(
        id: String,
        edgeTypes: Set<KnowledgeEdgeType> = emptySet()
    ): List<GraphNeighbor> {
        val index = index() ?: return emptyList()
        val outgoing = index.outgoing[id].orEmpty()
            .filter { edgeTypes.isEmpty() || it.type in edgeTypes }
            .mapNotNull { edge ->
                index.nodesById[edge.to]?.let { GraphNeighbor(it, edge, GraphDirection.OUTGOING) }
            }
        val incoming = index.incoming[id].orEmpty()
            .filter { edgeTypes.isEmpty() || it.type in edgeTypes }
            .mapNotNull { edge ->
                index.nodesById[edge.from]?.let { GraphNeighbor(it, edge, GraphDirection.INCOMING) }
            }
        return (outgoing + incoming).sortedWith(
            compareBy<GraphNeighbor> { it.edge.sequenceOrder ?: Int.MAX_VALUE }
                .thenBy { it.edge.id }
                .thenBy { it.direction.name }
        )
    }

    fun outgoingEdges(
        id: String,
        types: Set<KnowledgeEdgeType> = emptySet()
    ): List<KnowledgeEdge> = index()?.outgoing?.get(id).orEmpty()
        .filter { types.isEmpty() || it.type in types }

    fun incomingEdges(
        id: String,
        types: Set<KnowledgeEdgeType> = emptySet()
    ): List<KnowledgeEdge> = index()?.incoming?.get(id).orEmpty()
        .filter { types.isEmpty() || it.type in types }

    fun components(systemId: String): List<KnowledgeNode> {
        val index = index() ?: return emptyList()
        if (systemId !in index.nodesById) return emptyList()
        val queue = ArrayDeque<String>()
        val visited = mutableSetOf(systemId)
        val components = mutableListOf<KnowledgeNode>()
        queue.add(systemId)
        while (queue.isNotEmpty()) {
            val parentId = queue.removeFirst()
            index.incoming[parentId].orEmpty()
                .asSequence()
                .filter { it.type == KnowledgeEdgeType.PART_OF }
                .map(KnowledgeEdge::from)
                .filter(visited::add)
                .forEach { childId ->
                    val child = index.nodesById[childId] ?: return@forEach
                    if (child.type == KnowledgeNodeType.COMPONENT) components += child
                    queue.add(childId)
                }
        }
        return components.sortedBy(KnowledgeNode::id)
    }

    fun dtc(code: String): KnowledgeNode? = index()?.dtcs?.get(normalizeGraphDtc(code))

    fun profile(id: String): VehicleGraphProfile? = index()?.profilesById?.get(id)

    fun applicabilityRule(profileId: String, canonicalKey: String): VehicleApplicabilityRule? =
        index()?.rulesByProfileAndCanonicalKey?.get(profileId to canonicalKey)

    fun observedEvidence(id: String): StructuredObservedEvidence? =
        index()?.observedEvidenceById?.get(id)

    fun integrityStatus(): GraphIntegrityStatus =
        if (index() != null) GraphIntegrityStatus.VALID else GraphIntegrityStatus.INVALID

    fun loadMetrics(): GraphLoadMetrics = metrics

    private fun index(): GraphIndex? {
        when (val current = loadState) {
            is LoadState.Valid -> return current.index
            LoadState.Invalid -> return null
            LoadState.NotLoaded -> Unit
        }
        return synchronized(this) {
            when (val current = loadState) {
                is LoadState.Valid -> current.index
                LoadState.Invalid -> null
                LoadState.NotLoaded -> loadOnce()
            }
        }
    }

    private fun loadOnce(): GraphIndex? {
        var assetByteCount = 0L
        var parseStartedNanos = 0L
        return try {
            val bytes = assetLoader()
            assetByteCount = bytes.size.toLong()
            parseStartedNanos = System.nanoTime()
            val graph = AutomotiveKnowledgeGraphParser.decode(bytes.toString(Charsets.UTF_8))
            val parseDurationMillis = (System.nanoTime() - parseStartedNanos) / 1_000_000L
            val index = GraphIndex.create(graph)
            metrics = GraphLoadMetrics(
                assetByteCount = assetByteCount,
                parseDurationMillis = parseDurationMillis,
                nodeCount = graph.nodes.size,
                edgeCount = graph.edges.size,
                sourceRefCount = graph.statistics.qualifiedSourceRefCount
            )
            loadState = LoadState.Valid(index)
            index
        } catch (_: Exception) {
            val duration = if (parseStartedNanos == 0L) {
                0L
            } else {
                (System.nanoTime() - parseStartedNanos) / 1_000_000L
            }
            metrics = GraphLoadMetrics(
                assetByteCount = assetByteCount,
                parseDurationMillis = duration,
                nodeCount = 0,
                edgeCount = 0,
                sourceRefCount = 0
            )
            loadState = LoadState.Invalid
            null
        }
    }

    private sealed interface LoadState {
        data object NotLoaded : LoadState
        data class Valid(val index: GraphIndex) : LoadState
        data object Invalid : LoadState
    }

    private data class GraphIndex(
        val nodesById: Map<String, KnowledgeNode>,
        val edgesById: Map<String, KnowledgeEdge>,
        val outgoing: Map<String, List<KnowledgeEdge>>,
        val incoming: Map<String, List<KnowledgeEdge>>,
        val profilesById: Map<String, VehicleGraphProfile>,
        val rulesByProfileAndCanonicalKey: Map<Pair<String, String>, VehicleApplicabilityRule>,
        val observedEvidenceById: Map<String, StructuredObservedEvidence>,
        val nodesByCanonicalKey: Map<String, List<KnowledgeNode>>,
        val dtcs: Map<String, KnowledgeNode>
    ) {
        companion object {
            private val EDGE_ORDER = compareBy<KnowledgeEdge> {
                it.sequenceOrder ?: Int.MAX_VALUE
            }.thenBy(KnowledgeEdge::id)

            fun create(graph: AutomotiveKnowledgeGraph): GraphIndex {
                val canonicalNodes = graph.nodes.asSequence()
                    .filter { it.canonicalKey != null }
                    .groupBy { requireNotNull(it.canonicalKey) }
                    .mapValues { (_, nodes) -> nodes.sortedBy(KnowledgeNode::id) }
                    .toSortedMap()
                val dtcs = graph.nodes.asSequence()
                    .filter { it.type == KnowledgeNodeType.DTC }
                    .map { node ->
                        normalizeGraphDtc(requireNotNull(node.canonicalKey)) to node
                    }
                    .toList()
                check(dtcs.all { it.first.isNotBlank() }) {
                    "Validated graph contains a blank normalized DTC code"
                }
                check(dtcs.map { it.first }.distinct().size == dtcs.size) {
                    "Validated graph contains duplicate normalized DTC codes"
                }
                return GraphIndex(
                    nodesById = graph.nodes.associateBy(KnowledgeNode::id),
                    edgesById = graph.edges.associateBy(KnowledgeEdge::id),
                    outgoing = graph.edges.groupBy(KnowledgeEdge::from)
                        .mapValues { (_, edges) -> edges.sortedWith(EDGE_ORDER) },
                    incoming = graph.edges.groupBy(KnowledgeEdge::to)
                        .mapValues { (_, edges) -> edges.sortedWith(EDGE_ORDER) },
                    profilesById = graph.profiles.associateBy(VehicleGraphProfile::id),
                    rulesByProfileAndCanonicalKey = graph.applicabilityRules.associateBy {
                        it.profileId to it.canonicalKey
                    },
                    observedEvidenceById = graph.observedEvidence.associateBy(
                        StructuredObservedEvidence::id
                    ),
                    nodesByCanonicalKey = canonicalNodes,
                    dtcs = dtcs.toMap().toSortedMap()
                )
            }
        }
    }

}

private fun normalizeGraphDtc(code: String): String = code.trim().uppercase(Locale.ROOT)
