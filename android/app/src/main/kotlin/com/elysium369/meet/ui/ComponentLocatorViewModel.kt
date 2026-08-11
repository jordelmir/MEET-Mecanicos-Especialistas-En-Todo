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
import com.elysium369.meet.core.knowledge.graph.DiagnosticKnowledgeQuery
import com.elysium369.meet.core.knowledge.graph.DiagnosticKnowledgeQueryEngine
import com.elysium369.meet.core.knowledge.graph.ActiveVehicleIdentity
import com.elysium369.meet.core.knowledge.graph.KnowledgeApplicabilityContext
import com.elysium369.meet.core.knowledge.graph.KnowledgeEdgeType
import com.elysium369.meet.core.knowledge.graph.TypedCausalPathEngine
import com.elysium369.meet.data.visualdiagnostics.VisualDiagnosticRepositoryImpl
import com.elysium369.meet.data.local.dao.VehicleDao
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
    private val vehicleDao: VehicleDao,
) : ViewModel() {
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
                val vehicle = vehicleDao.getVehicleById(identity.vehicleId)
                    ?: return@withContext null
                val applicabilityContext = KnowledgeApplicabilityContext(
                    vehicle = ActiveVehicleIdentity(
                        make = vehicle.make,
                        model = vehicle.model,
                        year = vehicle.year,
                        engine = vehicle.engine,
                        transmission = vehicle.transmissionType,
                        vin = vehicle.vin.takeIf(String::isNotBlank),
                    ),
                    ecuName = identity.ecuEndpointId,
                    ecuAddress = identity.ecuEndpointId,
                    findingNamespace = identity.diagnosticNamespace,
                    findingRawIdentity = identity.rawDtcIdentity,
                )
                val knowledgeMatch = DiagnosticKnowledgeQueryEngine(knowledgeGraph).resolve(
                    DiagnosticKnowledgeQuery(
                        namespace = identity.diagnosticNamespace,
                        displayCode = identity.displayCode,
                        rawDtcIdentity = identity.rawDtcIdentity,
                        failureType = null,
                        ecuEndpoint = identity.ecuEndpointId,
                        vehicleProfile = null,
                    ),
                )
                val root = knowledgeMatch.node
                val relations = root?.let { dtcNode ->
                    TypedCausalPathEngine(knowledgeGraph).pathsFrom(
                        root = dtcNode,
                        applicabilityContext = applicabilityContext,
                    )
                        .filter { it.terminal.type.name == "COMPONENT" }
                        .map { path ->
                                val component = path.terminal
                                val edge = path.steps.last().edge
                                val refs = path.sourceRefs.map {
                                    "${it.sourceDocumentId}:${it.blockId}:${it.textHash}"
                                }
                                SpatialKnowledgeRelation(
                                    componentId = component.canonicalKey ?: component.id,
                                    relationship = path.steps.joinToString(" → ") { it.edge.type.name },
                                    pathType = edge.type.toSpatialPathType(),
                                    pathDescription = (listOf(dtcNode.label) + path.steps.map { it.to.label })
                                        .joinToString(" → "),
                                    evidenceScore = path.evidenceRank,
                                    source = if (refs.isEmpty()) {
                                        "${knowledgeMatch.authority.name}: grafo sin referencia calificada"
                                    } else {
                                        "${knowledgeMatch.authority.name}: grafo automotriz citado"
                                    },
                                    requiredEvidence = path.evidenceRequirements
                                        .ifEmpty { listOf("Confirmar VIN/OEM y prueba física") }
                                        .joinToString("; "),
                                    sourceReferences = refs,
                                    reviewState = path.reviewStates.joinToString(" → ") { it.name },
                                    applicability = path.applicabilityStates.joinToString(" → ") { it.name },
                                    vehicleConstraints = path.unresolvedVehicleConstraints,
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

    private fun KnowledgeEdgeType.toSpatialPathType(): String = when (this) {
        KnowledgeEdgeType.CARRIED_BY_SIGNAL, KnowledgeEdgeType.OBSERVED_BY_PID -> "SIGNAL"
        KnowledgeEdgeType.ROUTES_THROUGH_CIRCUIT -> "ELECTRICAL"
        KnowledgeEdgeType.INVOLVES_ECU -> "COMMUNICATION"
        else -> "MECHANICAL"
    }

}
