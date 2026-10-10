package com.elysium369.meet.education.engine

import com.elysium369.meet.education.domain.UniversalCompetency
import com.elysium369.meet.education.domain.UniversalPrerequisite

/**
 * Result of curriculum DAG validation.
 */
data class DagValidationReport(
    val isValid: Boolean,
    val hasCycles: Boolean,
    val cycleNodes: List<String> = emptyList(),
    val danglingPrerequisites: List<Pair<String, String>> = emptyList(),
    val selfPrerequisites: List<String> = emptyList(),
    val topologicalSort: List<String> = emptyList(),
    val maxGraphDepth: Int = 0,
    val errors: List<String> = emptyList(),
)

/**
 * Deterministic DAG validator for the Universal Curriculum Knowledge Graph.
 * Ensures strict acyclicity (no circular reasoning), absence of dangling dependencies,
 * and computable learning progression order.
 */
object CurriculumDagValidator {

    private enum class NodeColor {
        WHITE, // Unvisited
        GRAY,  // In current recursion stack (cycle detected if reached)
        BLACK  // Completely processed
    }

    /**
     * Validates a curriculum graph defined by a set of competencies and prerequisite edges.
     */
    fun validate(
        competencies: Collection<UniversalCompetency>,
        prerequisites: Collection<UniversalPrerequisite>,
    ): DagValidationReport {
        val competencyMap = competencies.associateBy { it.id }
        val errors = mutableListOf<String>()

        // 1. Check for self-prerequisites
        val selfPrereqs = prerequisites
            .filter { it.competencyId == it.prerequisiteId }
            .map { it.competencyId }
        if (selfPrereqs.isNotEmpty()) {
            errors.add("Self-prerequisite detected for: ${selfPrereqs.joinToString()}")
        }

        // 2. Check for dangling prerequisites (prerequisite refers to non-existent competency)
        val dangling = mutableListOf<Pair<String, String>>()
        prerequisites.forEach { edge ->
            if (!competencyMap.containsKey(edge.competencyId)) {
                dangling.add(edge.competencyId to edge.prerequisiteId)
                errors.add("Edge refers to missing competencyId: ${edge.competencyId}")
            }
            if (!competencyMap.containsKey(edge.prerequisiteId)) {
                dangling.add(edge.competencyId to edge.prerequisiteId)
                errors.add("Edge refers to missing prerequisiteId: ${edge.prerequisiteId}")
            }
        }

        // 3. Build Adjacency List for Directed Graph
        // Edge: prerequisite -> dependent (must learn prerequisite before dependent)
        val allNodeIds = competencyMap.keys
        val adjList = mutableMapOf<String, MutableList<String>>()
        val inDegree = mutableMapOf<String, Int>()

        allNodeIds.forEach { id ->
            adjList[id] = mutableListOf()
            inDegree[id] = 0
        }

        prerequisites.forEach { edge ->
            if (competencyMap.containsKey(edge.competencyId) && competencyMap.containsKey(edge.prerequisiteId)) {
                // prerequisite -> competencyId
                adjList[edge.prerequisiteId]?.add(edge.competencyId)
                inDegree[edge.competencyId] = (inDegree[edge.competencyId] ?: 0) + 1
            }
        }

        // 4. Cycle Detection using 3-color DFS
        val colors = mutableMapOf<String, NodeColor>()
        allNodeIds.forEach { colors[it] = NodeColor.WHITE }

        val cycleNodes = mutableListOf<String>()
        var hasCycle = false

        fun dfs(node: String, path: MutableList<String>): Boolean {
            colors[node] = NodeColor.GRAY
            path.add(node)

            for (neighbor in adjList[node].orEmpty()) {
                val neighborColor = colors[neighbor] ?: NodeColor.WHITE
                if (neighborColor == NodeColor.GRAY) {
                    hasCycle = true
                    val cycleStartIdx = path.indexOf(neighbor)
                    if (cycleStartIdx != -1) {
                        cycleNodes.addAll(path.subList(cycleStartIdx, path.size))
                    } else {
                        cycleNodes.add(neighbor)
                    }
                    return true
                } else if (neighborColor == NodeColor.WHITE) {
                    if (dfs(neighbor, path)) return true
                }
            }

            path.removeAt(path.size - 1)
            colors[node] = NodeColor.BLACK
            return false
        }

        for (node in allNodeIds) {
            if (colors[node] == NodeColor.WHITE) {
                if (dfs(node, mutableListOf())) {
                    break
                }
            }
        }

        if (hasCycle) {
            errors.add("Circular prerequisite dependency detected: ${cycleNodes.joinToString(" -> ")}")
        }

        // 5. Kahn's Algorithm for Topological Sort & Depth Calculation
        val topologicalOrder = mutableListOf<String>()
        val depthMap = mutableMapOf<String, Int>()
        allNodeIds.forEach { depthMap[it] = 1 }

        if (!hasCycle && dangling.isEmpty() && selfPrereqs.isEmpty()) {
            val queue = ArrayDeque<String>()
            val currentInDegree = inDegree.toMutableMap()

            currentInDegree.filter { it.value == 0 }.keys.forEach { queue.add(it) }

            while (queue.isNotEmpty()) {
                val curr = queue.removeFirst()
                topologicalOrder.add(curr)

                for (neighbor in adjList[curr].orEmpty()) {
                    val currentDepth = depthMap[curr] ?: 1
                    val neighborDepth = depthMap[neighbor] ?: 1
                    depthMap[neighbor] = maxOf(neighborDepth, currentDepth + 1)

                    val deg = (currentInDegree[neighbor] ?: 1) - 1
                    currentInDegree[neighbor] = deg
                    if (deg == 0) {
                        queue.add(neighbor)
                    }
                }
            }
        }

        val maxDepth = if (depthMap.isEmpty()) 0 else depthMap.values.maxOrNull() ?: 0
        val isValid = !hasCycle && dangling.isEmpty() && selfPrereqs.isEmpty() && errors.isEmpty()

        return DagValidationReport(
            isValid = isValid,
            hasCycles = hasCycle,
            cycleNodes = cycleNodes.distinct(),
            danglingPrerequisites = dangling.distinct(),
            selfPrerequisites = selfPrereqs.distinct(),
            topologicalSort = topologicalOrder,
            maxGraphDepth = maxDepth,
            errors = errors,
        )
    }
}
