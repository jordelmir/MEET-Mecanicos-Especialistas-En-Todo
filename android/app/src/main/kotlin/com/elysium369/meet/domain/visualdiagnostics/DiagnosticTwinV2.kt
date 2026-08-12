package com.elysium369.meet.domain.visualdiagnostics

import com.elysium369.meet.core.knowledge.graph.KnowledgeConstraintState
import com.elysium369.meet.core.knowledge.graph.TypedCausalPath
import com.elysium369.meet.domain.diagnostics.VehicleEvidenceGraph
import com.elysium369.meet.domain.diagnostics.VehicleEvidenceNodeType

enum class DiagnosticTwinState {
    OBSERVED, RELATED, UNTESTED, VERIFIED_OK, ANOMALOUS, NOT_APPLICABLE, UNKNOWN,
}

enum class VisualAuthority { EVIDENCE_OBSERVED, TEST_VERIFIED, CAUSALLY_RELATED, EDUCATIONAL_ONLY }
enum class DiagnosticTwinLayer { SYSTEM, ASSEMBLY, COMPONENT, CIRCUIT, SIGNAL, FLUID, COMMUNICATION, EVIDENCE, TEST_POINT }

data class DiagnosticTwinElement(
    val canonicalEntityId: String,
    val label: String,
    val layer: DiagnosticTwinLayer,
    val state: DiagnosticTwinState,
    val evidenceIds: Set<String>,
    val reason: String,
    val visualAuthority: VisualAuthority,
    val sourceReferenceIds: Set<String> = emptySet(),
    val sourceContentHashes: Set<String> = emptySet(),
)

data class DiagnosticTwinV2(
    val vehicleId: String,
    val vehicleBindingId: String,
    val topology: FusedVehiclePowertrainTopology,
    val elements: List<DiagnosticTwinElement>,
    val generatedAt: Long,
)

data class DiagnosticTwinObservation(
    val canonicalEntityId: String,
    val label: String,
    val evidenceIds: Set<String>,
    val anomalous: Boolean? = null,
    val verifiedOk: Boolean = false,
    val sourceReferenceIds: Set<String> = emptySet(),
    val sourceContentHashes: Set<String> = emptySet(),
)

/** Evidence projection. Red/anomalous requires an observation; causal proximity stays RELATED. */
object DiagnosticTwinV2Engine {
    fun project(
        graph: VehicleEvidenceGraph,
        topology: FusedVehiclePowertrainTopology,
        causalPaths: List<TypedCausalPath>,
        observations: List<DiagnosticTwinObservation>,
        generatedAt: Long,
    ): DiagnosticTwinV2 {
        require(generatedAt > 0)
        val observedById = observations.associateBy(DiagnosticTwinObservation::canonicalEntityId)
        val related = causalPaths.groupBy { it.terminal.canonicalKey ?: it.terminal.id }
        val graphNodes = graph.nodes.associateBy { it.id }
        val graphEvidence = graph.nodes.associate { it.id to it.evidenceIds }
        val entityIds = (observedById.keys + related.keys + graphEvidence.keys).toSortedSet()
        val elements = entityIds.map { entityId ->
            val observation = observedById[entityId]
            val paths = related[entityId].orEmpty()
            val constraintStates = paths.flatMap { it.constraintDecisions }.map { it.state }
            val evidence = buildSet {
                addAll(observation?.evidenceIds.orEmpty())
                addAll(graphEvidence[entityId].orEmpty())
            }
            val sourceReferenceIds = buildSet {
                addAll(observation?.sourceReferenceIds.orEmpty())
                addAll(paths.flatMap { it.sourceRefs }.map { "${it.sourceDocumentId}:${it.blockId}" })
                addAll(graphNodes[entityId]?.sourceReferenceIds.orEmpty())
            }
            val sourceContentHashes = buildSet {
                addAll(observation?.sourceContentHashes.orEmpty())
                addAll(paths.flatMap { it.sourceRefs }.map { it.textHash })
                addAll(graphNodes[entityId]?.sourceContentHashes.orEmpty())
            }
            val state = when {
                observation?.anomalous == true -> DiagnosticTwinState.ANOMALOUS
                observation?.verifiedOk == true -> DiagnosticTwinState.VERIFIED_OK
                observation != null -> DiagnosticTwinState.OBSERVED
                constraintStates.any { it in setOf(KnowledgeConstraintState.NOT_APPLICABLE, KnowledgeConstraintState.CONFLICTED) } -> DiagnosticTwinState.NOT_APPLICABLE
                paths.isNotEmpty() -> DiagnosticTwinState.RELATED
                evidence.isNotEmpty() -> DiagnosticTwinState.UNTESTED
                else -> DiagnosticTwinState.UNKNOWN
            }
            DiagnosticTwinElement(
                canonicalEntityId = entityId,
                label = observation?.label ?: paths.firstOrNull()?.terminal?.label ?: entityId,
                layer = when (graphNodes[entityId]?.type) {
                    VehicleEvidenceNodeType.VEHICLE -> DiagnosticTwinLayer.SYSTEM
                    VehicleEvidenceNodeType.ECU -> DiagnosticTwinLayer.COMMUNICATION
                    VehicleEvidenceNodeType.COMPONENT, VehicleEvidenceNodeType.PART -> DiagnosticTwinLayer.COMPONENT
                    VehicleEvidenceNodeType.CIRCUIT -> DiagnosticTwinLayer.CIRCUIT
                    VehicleEvidenceNodeType.SIGNAL, VehicleEvidenceNodeType.MEASUREMENT -> DiagnosticTwinLayer.SIGNAL
                    VehicleEvidenceNodeType.DIAGNOSTIC_TEST, VehicleEvidenceNodeType.VERIFICATION -> DiagnosticTwinLayer.TEST_POINT
                    VehicleEvidenceNodeType.FINDING, VehicleEvidenceNodeType.OBSERVATION,
                    VehicleEvidenceNodeType.SNAPSHOT, VehicleEvidenceNodeType.HYPOTHESIS,
                    VehicleEvidenceNodeType.REPAIR -> DiagnosticTwinLayer.EVIDENCE
                    null -> DiagnosticTwinLayer.ASSEMBLY
                },
                state = state,
                evidenceIds = evidence,
                reason = when (state) {
                    DiagnosticTwinState.ANOMALOUS -> "Anomalía respaldada por observación asociada."
                    DiagnosticTwinState.VERIFIED_OK -> "Prueba registrada dentro de especificación."
                    DiagnosticTwinState.OBSERVED -> "Elemento observado; no implica que esté averiado."
                    DiagnosticTwinState.RELATED -> "Relación causal tipada; requiere prueba física."
                    DiagnosticTwinState.UNTESTED -> "Existe evidencia relacionada, pero falta prueba concluyente."
                    DiagnosticTwinState.NOT_APPLICABLE -> "Las restricciones verificadas excluyen este elemento."
                    DiagnosticTwinState.UNKNOWN -> "Dato no capturado."
                },
                visualAuthority = when (state) {
                    DiagnosticTwinState.ANOMALOUS, DiagnosticTwinState.OBSERVED -> VisualAuthority.EVIDENCE_OBSERVED
                    DiagnosticTwinState.VERIFIED_OK -> VisualAuthority.TEST_VERIFIED
                    DiagnosticTwinState.RELATED, DiagnosticTwinState.UNTESTED -> VisualAuthority.CAUSALLY_RELATED
                    DiagnosticTwinState.NOT_APPLICABLE, DiagnosticTwinState.UNKNOWN -> VisualAuthority.EDUCATIONAL_ONLY
                },
                sourceReferenceIds = sourceReferenceIds,
                sourceContentHashes = sourceContentHashes,
            )
        }
        return DiagnosticTwinV2(graph.vehicleId, graph.vehicleBindingId, topology, elements, generatedAt)
    }
}
