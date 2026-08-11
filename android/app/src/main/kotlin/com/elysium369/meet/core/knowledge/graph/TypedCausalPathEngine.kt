package com.elysium369.meet.core.knowledge.graph

import java.util.PriorityQueue

data class TypedCausalStep(
    val from: KnowledgeNode,
    val edge: KnowledgeEdge,
    val to: KnowledgeNode,
)

data class TypedCausalPath(
    val steps: List<TypedCausalStep>,
    /** Evidence ranking only. This is not a failure probability. */
    val evidenceRank: Double,
    val sourceRefs: List<SourceRef>,
    val reviewStates: Set<GraphReviewState>,
    val applicabilityStates: Set<VehicleApplicabilityState>,
    val evidenceRequirements: List<String>,
    val unresolvedVehicleConstraints: List<String>,
    val constraintDecisions: List<KnowledgeConstraintDecision>,
) {
    val terminal: KnowledgeNode get() = steps.last().to
}

/** Directional, typed graph traversal. Invalid semantic transitions are never explored. */
class TypedCausalPathEngine(
    private val repository: AutomotiveKnowledgeGraphRepository,
) {
    fun pathsFrom(
        root: KnowledgeNode,
        applicabilityContext: KnowledgeApplicabilityContext? = null,
        terminalTypes: Set<KnowledgeNodeType> = setOf(
            KnowledgeNodeType.COMPONENT,
            KnowledgeNodeType.MEASUREMENT,
        ),
        maxDepth: Int = 5,
        maxResults: Int = 32,
    ): List<TypedCausalPath> {
        require(maxDepth in 1..12)
        require(maxResults in 1..256)
        data class Cursor(
            val node: KnowledgeNode,
            val steps: List<TypedCausalStep>,
            val visited: Set<String>,
            val rank: Double,
        )

        val queue = PriorityQueue<Cursor>(
            compareByDescending<Cursor> { it.rank }.thenBy { it.steps.size }.thenBy { it.node.id },
        )
        queue += Cursor(root, emptyList(), setOf(root.id), 1.0)
        val results = mutableListOf<TypedCausalPath>()
        while (queue.isNotEmpty() && results.size < maxResults) {
            val cursor = queue.remove()
            if (cursor.steps.size >= maxDepth) continue
            repository.outgoingEdges(cursor.node.id).asSequence()
                .filter { isUsable(it, applicabilityContext) }
                .sortedBy(KnowledgeEdge::id)
                .forEach { edge ->
                    val target = repository.node(edge.to) ?: return@forEach
                    if (target.id in cursor.visited || !isAllowed(cursor.node.type, edge.type, target.type)) {
                        return@forEach
                    }
                    val step = TypedCausalStep(cursor.node, edge, target)
                    val steps = cursor.steps + step
                    val rank = cursor.rank * edgeWeight(edge)
                    if (target.type in terminalTypes) results += materialize(steps, rank, applicabilityContext)
                    if (target.type !in TERMINAL_ONLY_TYPES) {
                        queue += Cursor(target, steps, cursor.visited + target.id, rank)
                    }
                }
        }
        return results.distinctBy { path -> path.steps.map { it.edge.id } }
            .sortedWith(compareByDescending<TypedCausalPath> { it.evidenceRank }.thenBy { it.terminal.id })
            .take(maxResults)
    }

    private fun materialize(
        steps: List<TypedCausalStep>,
        rank: Double,
        context: KnowledgeApplicabilityContext?,
    ): TypedCausalPath {
        val decisions = steps.map { KnowledgeApplicabilityEngine.evaluate(it.edge.vehicleConstraints, context) }
        return TypedCausalPath(
            steps = steps,
            evidenceRank = rank.coerceIn(0.0, 1.0),
            sourceRefs = steps.flatMap { it.edge.sourceRefs + it.to.sourceRefs }.distinct(),
            reviewStates = steps.mapTo(linkedSetOf()) { it.edge.reviewState },
            applicabilityStates = steps.mapTo(linkedSetOf()) { it.edge.applicability },
            evidenceRequirements = steps.flatMap { it.edge.evidenceRequired }.filter(String::isNotBlank).distinct(),
            unresolvedVehicleConstraints = decisions.flatMap { it.missingConstraints + it.conflictingConstraints }.distinct(),
            constraintDecisions = decisions,
        )
    }

    private fun isUsable(edge: KnowledgeEdge, context: KnowledgeApplicabilityContext?): Boolean =
        edge.applicability !in setOf(
            VehicleApplicabilityState.NOT_APPLICABLE,
            VehicleApplicabilityState.CONFLICTED,
        ) && edge.reviewState != GraphReviewState.CONFLICTED &&
            KnowledgeApplicabilityEngine.evaluate(edge.vehicleConstraints, context).state !in setOf(
                KnowledgeConstraintState.NOT_APPLICABLE,
                KnowledgeConstraintState.CONFLICTED,
            )

    private fun edgeWeight(edge: KnowledgeEdge): Double {
        val confidence = when (edge.confidence) {
            GraphConfidence.HIGH -> 0.95
            GraphConfidence.MEDIUM -> 0.78
            GraphConfidence.LOW -> 0.55
            GraphConfidence.UNASSESSED -> 0.35
        }
        val review = when (edge.reviewState) {
            GraphReviewState.REVIEWED, GraphReviewState.OBSERVED -> 1.0
            GraphReviewState.REVIEW_REQUIRED -> 0.7
            GraphReviewState.CONFLICTED -> 0.0
        }
        val provenance = if (edge.sourceRefs.any(SourceRef::isComplete)) 1.0 else 0.65
        return confidence * review * provenance
    }

    private fun isAllowed(
        from: KnowledgeNodeType,
        edge: KnowledgeEdgeType,
        to: KnowledgeNodeType,
    ): Boolean = Transition(from, edge, to) in GRAMMAR

    private data class Transition(
        val from: KnowledgeNodeType,
        val edge: KnowledgeEdgeType,
        val to: KnowledgeNodeType,
    )

    private companion object {
        val TERMINAL_ONLY_TYPES = setOf(KnowledgeNodeType.MEASUREMENT, KnowledgeNodeType.VERIFICATION)
        val GRAMMAR = setOf(
            Transition(KnowledgeNodeType.DTC, KnowledgeEdgeType.HAS_FAILURE_MODE, KnowledgeNodeType.FAILURE_MODE),
            Transition(KnowledgeNodeType.DTC, KnowledgeEdgeType.INVOLVES_ECU, KnowledgeNodeType.ECU),
            Transition(KnowledgeNodeType.DTC, KnowledgeEdgeType.HAS_DIAGNOSTIC_TEST, KnowledgeNodeType.DIAGNOSTIC_TEST),
            Transition(KnowledgeNodeType.DTC, KnowledgeEdgeType.MAY_CAUSE, KnowledgeNodeType.COMPONENT),
            Transition(KnowledgeNodeType.DTC, KnowledgeEdgeType.AFFECTS, KnowledgeNodeType.COMPONENT),
            Transition(KnowledgeNodeType.DTC, KnowledgeEdgeType.SUGGESTS_PART_CANDIDATE, KnowledgeNodeType.COMPONENT),
            Transition(KnowledgeNodeType.FAILURE_MODE, KnowledgeEdgeType.ROUTES_THROUGH_CIRCUIT, KnowledgeNodeType.CIRCUIT),
            Transition(KnowledgeNodeType.FAILURE_MODE, KnowledgeEdgeType.HAS_DIAGNOSTIC_TEST, KnowledgeNodeType.DIAGNOSTIC_TEST),
            Transition(KnowledgeNodeType.FAILURE_MODE, KnowledgeEdgeType.AFFECTS, KnowledgeNodeType.COMPONENT),
            Transition(KnowledgeNodeType.CIRCUIT, KnowledgeEdgeType.AFFECTS, KnowledgeNodeType.COMPONENT),
            Transition(KnowledgeNodeType.CIRCUIT, KnowledgeEdgeType.CARRIED_BY_SIGNAL, KnowledgeNodeType.SIGNAL),
            Transition(KnowledgeNodeType.ECU, KnowledgeEdgeType.CARRIED_BY_SIGNAL, KnowledgeNodeType.SIGNAL),
            Transition(KnowledgeNodeType.SIGNAL, KnowledgeEdgeType.AFFECTS, KnowledgeNodeType.COMPONENT),
            Transition(KnowledgeNodeType.SIGNAL, KnowledgeEdgeType.OBSERVED_BY_PID, KnowledgeNodeType.PID),
            Transition(KnowledgeNodeType.PID, KnowledgeEdgeType.CONFIRMED_BY_TEST, KnowledgeNodeType.DIAGNOSTIC_TEST),
            Transition(KnowledgeNodeType.DIAGNOSTIC_TEST, KnowledgeEdgeType.HAS_STEP, KnowledgeNodeType.PROCEDURE_STEP),
            Transition(KnowledgeNodeType.DIAGNOSTIC_TEST, KnowledgeEdgeType.CONFIRMED_BY_TEST, KnowledgeNodeType.MEASUREMENT),
            Transition(KnowledgeNodeType.PROCEDURE_STEP, KnowledgeEdgeType.CONFIRMED_BY_TEST, KnowledgeNodeType.MEASUREMENT),
            Transition(KnowledgeNodeType.COMPONENT, KnowledgeEdgeType.HAS_DIAGNOSTIC_TEST, KnowledgeNodeType.DIAGNOSTIC_TEST),
            Transition(KnowledgeNodeType.COMPONENT, KnowledgeEdgeType.REQUIRES_TEST_BEFORE_REPLACE, KnowledgeNodeType.DIAGNOSTIC_TEST),
            Transition(KnowledgeNodeType.COMPONENT, KnowledgeEdgeType.ROUTES_THROUGH_CIRCUIT, KnowledgeNodeType.CIRCUIT),
        )
    }
}
