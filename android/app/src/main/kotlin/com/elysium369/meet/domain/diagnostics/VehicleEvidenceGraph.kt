package com.elysium369.meet.domain.diagnostics

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
) {
    init {
        require(id.isNotBlank() && vehicleId.isNotBlank() && vehicleBindingId.isNotBlank())
        require(fromNodeId.isNotBlank() && toNodeId.isNotBlank() && fromNodeId != toNodeId)
        require(evidenceIds.none(String::isBlank) && createdAt > 0)
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
