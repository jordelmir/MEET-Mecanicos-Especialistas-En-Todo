package com.elysium369.meet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elysium369.meet.ai.DiagnosticAiContextBuilder
import com.elysium369.meet.ai.ProprietaryGroundedContextBuilder
import com.elysium369.meet.core.diagnostics.DiagnosticSpatialFindingContext
import com.elysium369.meet.core.diagnostics.DiagnosticSpatialProjection
import com.elysium369.meet.core.diagnostics.DtcSpatialResolver
import com.elysium369.meet.core.diagnostics.SpatialKnowledgeRelation
import com.elysium369.meet.core.knowledge.graph.AutomotiveKnowledgeGraphRepository
import com.elysium369.meet.core.knowledge.graph.GraphConfidence
import com.elysium369.meet.core.knowledge.graph.GraphNeighbor
import com.elysium369.meet.core.knowledge.graph.KnowledgeEdgeType
import com.elysium369.meet.core.knowledge.graph.KnowledgeNode
import com.elysium369.meet.core.knowledge.graph.KnowledgeNodeType
import com.elysium369.meet.data.visualdiagnostics.VisualDiagnosticRepositoryImpl
import com.elysium369.meet.domain.diagnostics.DiagnosticFindingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** DI boundary for the 3D diagnostic feature; Compose no longer constructs repositories. */
@HiltViewModel
class ComponentLocatorViewModel @Inject constructor(
    val visualRepository: VisualDiagnosticRepositoryImpl,
    val diagnosticAiContextBuilder: DiagnosticAiContextBuilder,
    val proprietaryGroundedContextBuilder: ProprietaryGroundedContextBuilder,
    private val findingRepository: DiagnosticFindingRepository,
    private val knowledgeGraph: AutomotiveKnowledgeGraphRepository,
) : ViewModel() {
    private val spatialEdgeTypes = setOf(
        KnowledgeEdgeType.MAY_CAUSE,
        KnowledgeEdgeType.MAY_SET_DTC,
        KnowledgeEdgeType.AFFECTS,
        KnowledgeEdgeType.HAS_FAILURE_MODE,
        KnowledgeEdgeType.INVOLVES_ECU,
        KnowledgeEdgeType.CARRIED_BY_SIGNAL,
        KnowledgeEdgeType.ROUTES_THROUGH_CIRCUIT,
        KnowledgeEdgeType.OBSERVED_BY_PID,
        KnowledgeEdgeType.REPAIRED_BY,
        KnowledgeEdgeType.PART_OF,
    )
    private val _spatialProjection = MutableStateFlow<DiagnosticSpatialProjection?>(null)
    val spatialProjection: StateFlow<DiagnosticSpatialProjection?> = _spatialProjection.asStateFlow()

    private val _spatialFindingCode = MutableStateFlow<String?>(null)
    val spatialFindingCode: StateFlow<String?> = _spatialFindingCode.asStateFlow()

    /** Resolves a persisted finding; UI query strings never reconstruct diagnostic authority. */
    fun loadFindingProjection(findingId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val finding = findingRepository.observeFinding(findingId).first() ?: return@withContext null
                val identity = finding.identity
                val root = knowledgeGraph.dtc(identity.displayCode)
                val relations = root?.let { dtcNode ->
                    componentPathsFrom(dtcNode, maxDepth = 3)
                        .map { (component, path) ->
                                val edge = path.last().edge
                                val refs = path.flatMap { it.edge.sourceRefs }
                                    .distinct()
                                    .map { "${it.sourceDocumentId}:${it.blockId}:${it.textHash}" }
                                val evidence = path.flatMap { it.edge.evidenceRequired }.distinct()
                                SpatialKnowledgeRelation(
                                    componentId = component.canonicalKey ?: component.id,
                                    relationship = path.joinToString(" → ") { it.edge.type.name },
                                    pathType = edge.type.toSpatialPathType(),
                                    pathDescription = (listOf(dtcNode.label) + path.map { it.node.label })
                                        .joinToString(" → "),
                                    evidenceScore = path.minOf { it.edge.confidence.toEvidenceScore() },
                                    source = if (refs.isEmpty()) "Grafo sin referencia calificada" else "Grafo automotriz citado",
                                    requiredEvidence = evidence.ifEmpty { listOf("Confirmar VIN/OEM y prueba física") }
                                        .joinToString("; "),
                                    sourceReferences = refs,
                                    reviewState = path.joinToString(" → ") { it.edge.reviewState.name },
                                    applicability = path.joinToString(" → ") { it.edge.applicability.name },
                                    vehicleConstraints = path.flatMap { it.edge.vehicleConstraints }.distinct(),
                                )
                        }
                        .distinctBy { it.componentId to it.relationship }
                }.orEmpty()
                identity.displayCode to DtcSpatialResolver.resolve(
                    DiagnosticSpatialFindingContext(
                        stableFindingKey = identity.id,
                        displayCode = identity.displayCode,
                        rawDtcIdentity = identity.rawDtcIdentity,
                        namespace = identity.diagnosticNamespace,
                        moduleIdentity = identity.ecuEndpointId,
                        moduleName = identity.ecuEndpointId,
                        knowledgeRelations = relations,
                    ),
                )
            }
            _spatialFindingCode.value = result?.first
            _spatialProjection.value = result?.second
        }
    }

    private fun componentPathsFrom(
        root: KnowledgeNode,
        maxDepth: Int,
    ): List<Pair<KnowledgeNode, List<GraphNeighbor>>> {
        data class Traversal(
            val node: KnowledgeNode,
            val path: List<GraphNeighbor>,
            val visited: Set<String>,
        )

        val queue = ArrayDeque<Traversal>()
        queue += Traversal(root, emptyList(), setOf(root.id))
        val results = mutableListOf<Pair<KnowledgeNode, List<GraphNeighbor>>>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.path.size >= maxDepth) continue
            knowledgeGraph.neighbors(current.node.id, spatialEdgeTypes).forEach { neighbor ->
                if (neighbor.node.id in current.visited) return@forEach
                val path = current.path + neighbor
                if (neighbor.node.type == KnowledgeNodeType.COMPONENT) {
                    results += neighbor.node to path
                } else {
                    queue += Traversal(neighbor.node, path, current.visited + neighbor.node.id)
                }
            }
        }
        return results
    }

    private fun KnowledgeEdgeType.toSpatialPathType(): String = when (this) {
        KnowledgeEdgeType.CARRIED_BY_SIGNAL, KnowledgeEdgeType.OBSERVED_BY_PID -> "SIGNAL"
        KnowledgeEdgeType.ROUTES_THROUGH_CIRCUIT -> "ELECTRICAL"
        KnowledgeEdgeType.INVOLVES_ECU -> "COMMUNICATION"
        else -> "MECHANICAL"
    }

    private fun GraphConfidence.toEvidenceScore(): Double = when (this) {
        GraphConfidence.HIGH -> 0.85
        GraphConfidence.MEDIUM -> 0.65
        GraphConfidence.LOW -> 0.4
        GraphConfidence.UNASSESSED -> 0.2
    }
}
