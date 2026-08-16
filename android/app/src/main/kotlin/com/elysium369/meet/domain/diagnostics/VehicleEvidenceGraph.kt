package com.elysium369.meet.domain.diagnostics

import com.elysium369.meet.data.local.entities.DiagnosticFindingEntity
import com.elysium369.meet.data.local.entities.DiagnosticObservationEntity

enum class VehicleEvidenceNodeType {
    VEHICLE, ECU, COMPONENT, CIRCUIT, SIGNAL, FINDING, OBSERVATION, MEASUREMENT,
    SNAPSHOT, HYPOTHESIS, DIAGNOSTIC_TEST, REPAIR, PART, VERIFICATION,
}

enum class VehicleEvidenceEdgeType {
    REPORTED_BY, OBSERVED_AT, MEASURED_BY, SUPPORTS, CONTRADICTS, CONNECTED_TO,
    TESTED_BY, REPAIRED_BY, VERIFIED_BY, APPLIES_TO,
}

data class VehicleEvidenceGraphNode(
    val id: String,
    val vehicleId: String,
    val vehicleBindingId: String,
    val type: VehicleEvidenceNodeType,
    val label: String,
    val evidenceIds: Set<String>,
    val createdAt: Long,
    val sourceReferenceIds: Set<String> = emptySet(),
    val sourceContentHashes: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank() && vehicleId.isNotBlank() && vehicleBindingId.isNotBlank())
        require(label.isNotBlank() && evidenceIds.none(String::isBlank) && createdAt > 0)
    }
}

data class VehicleEvidenceGraphEdge(
    val id: String,
    val vehicleId: String,
    val vehicleBindingId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val type: VehicleEvidenceEdgeType,
    val evidenceIds: Set<String>,
    val createdAt: Long,
    val sourceReferenceIds: Set<String> = emptySet(),
    val sourceContentHashes: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank() && vehicleId.isNotBlank() && vehicleBindingId.isNotBlank())
        require(fromNodeId.isNotBlank() && toNodeId.isNotBlank() && fromNodeId != toNodeId)
        require(evidenceIds.none(String::isBlank) && createdAt > 0)
    }
}

data class VehicleEvidenceGraphProjectionInput(
    val vehicleId: String,
    val vehicleBindingId: String,
    val findings: List<DiagnosticFindingEntity>,
    val observations: List<DiagnosticObservationEntity>,
)

interface VehicleEvidenceGraphRepository {
    fun rebuild(input: VehicleEvidenceGraphProjectionInput): VehicleEvidenceGraph
}

/** Deterministic projection over canonical authorities; no competing graph database. */
object DeterministicVehicleEvidenceGraphRepository : VehicleEvidenceGraphRepository {
    override fun rebuild(input: VehicleEvidenceGraphProjectionInput): VehicleEvidenceGraph {
        require(input.vehicleBindingId.isNotBlank())
        val scopedFindings = input.findings
            .filter { it.vehicleId == input.vehicleId && it.vehicleBindingId == input.vehicleBindingId }
            .sortedBy { it.id }
        val findingIds = scopedFindings.mapTo(hashSetOf()) { it.id }
        val scopedObservations = input.observations
            .filter { it.findingId in findingIds }
            .sortedWith(compareBy<DiagnosticObservationEntity> { it.findingId }.thenBy { it.findingSequence }.thenBy { it.id })
        val vehicleNode = VehicleEvidenceGraphNode(
            id = "vehicle:${input.vehicleId}",
            vehicleId = input.vehicleId,
            vehicleBindingId = input.vehicleBindingId,
            type = VehicleEvidenceNodeType.VEHICLE,
            label = "Vehículo verificado",
            evidenceIds = setOf(input.vehicleBindingId),
            createdAt = scopedFindings.minOfOrNull { it.createdAtMs } ?: 1L,
        )
        val nodes = buildList {
            add(vehicleNode)
            scopedFindings.forEach { finding ->
                add(
                    VehicleEvidenceGraphNode(
                        id = "finding:${finding.id}",
                        vehicleId = input.vehicleId,
                        vehicleBindingId = input.vehicleBindingId,
                        type = VehicleEvidenceNodeType.FINDING,
                        label = "${finding.displayCode} · ${finding.moduleRole.ifBlank { finding.ecuEndpointId }}",
                        evidenceIds = setOf(finding.id),
                        createdAt = finding.createdAtMs,
                    ),
                )
            }
            scopedObservations.forEach { observation ->
                add(
                    VehicleEvidenceGraphNode(
                        id = "observation:${observation.id}",
                        vehicleId = input.vehicleId,
                        vehicleBindingId = input.vehicleBindingId,
                        type = VehicleEvidenceNodeType.OBSERVATION,
                        label = observation.observationState,
                        evidenceIds = listOfNotNull(observation.exchangeId, observation.id).toSet(),
                        createdAt = observation.observedAt,
                    ),
                )
            }
        }
        val edges = buildList {
            scopedFindings.forEach { finding ->
                add(
                    VehicleEvidenceGraphEdge(
                        id = "vehicle-finding:${finding.id}",
                        vehicleId = input.vehicleId,
                        vehicleBindingId = input.vehicleBindingId,
                        fromNodeId = vehicleNode.id,
                        toNodeId = "finding:${finding.id}",
                        type = VehicleEvidenceEdgeType.APPLIES_TO,
                        evidenceIds = setOf(finding.id),
                        createdAt = finding.createdAtMs,
                    ),
                )
            }
            scopedObservations.forEach { observation ->
                add(
                    VehicleEvidenceGraphEdge(
                        id = "finding-observation:${observation.id}",
                        vehicleId = input.vehicleId,
                        vehicleBindingId = input.vehicleBindingId,
                        fromNodeId = "finding:${observation.findingId}",
                        toNodeId = "observation:${observation.id}",
                        type = VehicleEvidenceEdgeType.OBSERVED_AT,
                        evidenceIds = listOfNotNull(observation.exchangeId, observation.id).toSet(),
                        createdAt = observation.observedAt,
                    ),
                )
            }
        }
        return VehicleEvidenceGraph(input.vehicleId, input.vehicleBindingId, nodes, edges)
    }
}

data class VehicleEvidenceGraph(
    val vehicleId: String,
    val vehicleBindingId: String,
    val nodes: List<VehicleEvidenceGraphNode>,
    val edges: List<VehicleEvidenceGraphEdge>,
) {
    init {
        require(vehicleId.isNotBlank() && vehicleBindingId.isNotBlank())
        require(nodes.map { it.id }.distinct().size == nodes.size)
        require(edges.map { it.id }.distinct().size == edges.size)
        require(nodes.all { it.vehicleId == vehicleId && it.vehicleBindingId == vehicleBindingId })
        require(edges.all { it.vehicleId == vehicleId && it.vehicleBindingId == vehicleBindingId })
        val nodeIds = nodes.mapTo(hashSetOf()) { it.id }
        require(edges.all { it.fromNodeId in nodeIds && it.toNodeId in nodeIds })
    }

    fun append(
        newNodes: List<VehicleEvidenceGraphNode>,
        newEdges: List<VehicleEvidenceGraphEdge>,
    ): VehicleEvidenceGraph {
        require(newNodes.none { candidate -> nodes.any { it.id == candidate.id } }) {
            "Vehicle Evidence Graph es append-only; un nodeId existente no puede reescribirse."
        }
        require(newEdges.none { candidate -> edges.any { it.id == candidate.id } }) {
            "Vehicle Evidence Graph es append-only; un edgeId existente no puede reescribirse."
        }
        return VehicleEvidenceGraph(vehicleId, vehicleBindingId, nodes + newNodes, edges + newEdges)
    }

    fun evidenceIdsFor(nodeIds: Set<String>): Set<String> {
        val selected = nodes.filter { it.id in nodeIds }
        val selectedIds = selected.mapTo(hashSetOf()) { it.id }
        return selected.flatMapTo(linkedSetOf()) { it.evidenceIds } +
            edges.filter { it.fromNodeId in selectedIds || it.toNodeId in selectedIds }
                .flatMap { it.evidenceIds }
    }
}

enum class AiAutomotiveAction {
    SUMMARIZE, EXPLAIN, TRANSLATE, GUIDE, TECHNICIAN_NARRATIVE, COMPARE_EVIDENCE,
    INVENT_DTC, CREATE_ECU_EVIDENCE, DECLARE_COMPONENT_FAILED, DECLARE_REPAIR_VERIFIED,
    AUTHORIZE_ACTIVE_OPERATION, OVERRIDE_SAFETY_KERNEL,
}

data class EvidenceExplainerRequest(
    val action: AiAutomotiveAction,
    val graphNodeIds: Set<String>,
    val citationEvidenceIds: Set<String>,
)

data class EvidenceExplainerAuthorization(
    val allowed: Boolean,
    val reason: String,
    val boundedEvidenceIds: Set<String>,
)

/** The model may explain evidence, never create authority. */
object EvidenceExplainerPolicy {
    private val allowedActions = setOf(
        AiAutomotiveAction.SUMMARIZE,
        AiAutomotiveAction.EXPLAIN,
        AiAutomotiveAction.TRANSLATE,
        AiAutomotiveAction.GUIDE,
        AiAutomotiveAction.TECHNICIAN_NARRATIVE,
        AiAutomotiveAction.COMPARE_EVIDENCE,
    )

    fun authorize(
        graph: VehicleEvidenceGraph,
        request: EvidenceExplainerRequest,
    ): EvidenceExplainerAuthorization {
        if (request.action !in allowedActions) {
            return EvidenceExplainerAuthorization(false, "La IA no posee esa autoridad automotriz.", emptySet())
        }
        val bounded = graph.evidenceIdsFor(request.graphNodeIds)
        if (bounded.isEmpty() || request.citationEvidenceIds.isEmpty()) {
            return EvidenceExplainerAuthorization(false, "La explicación requiere IDs de evidencia citables.", bounded)
        }
        if (!bounded.containsAll(request.citationEvidenceIds)) {
            return EvidenceExplainerAuthorization(false, "Una cita no pertenece al subgrafo vehicular solicitado.", bounded)
        }
        return EvidenceExplainerAuthorization(true, "Explicación limitada a evidencia existente.", bounded)
    }
}
