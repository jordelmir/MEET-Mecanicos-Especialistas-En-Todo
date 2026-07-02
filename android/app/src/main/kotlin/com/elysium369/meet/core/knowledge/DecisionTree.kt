package com.elysium369.meet.core.knowledge

import kotlinx.serialization.Serializable

/**
 * User answer to a decision-tree node question.
 */
@Serializable
enum class DecisionAnswer {
    YES,
    NO,
    UNKNOWN
}

@Serializable
data class DecisionNode(
    val id: String,
    val question: String,
    val howToTest: String = "",
    val tools: List<String> = emptyList(),
    val expectedValue: String = "",
    val yesId: String? = null,
    val noId: String? = null,
    val unknownId: String? = null,
    val safetyWarning: String? = null,
    val isTerminal: Boolean = false
)

@Serializable
data class DecisionTree(
    val id: String,
    val title: String,
    val rootId: String,
    val nodes: List<DecisionNode>
)

/**
 * Step-by-step traversal of a decision tree.
 * Pure data traversal — no I/O, no platform deps, easy to test.
 */
class DecisionTreeEngine {

    /**
     * Walks the tree following user answers until a terminal node.
     * Returns the path of (node, answer) pairs for the audit log.
     */
    fun walk(
        tree: DecisionTree,
        answers: Map<String, DecisionAnswer>
    ): List<Pair<DecisionNode, DecisionAnswer?>> {
        val byId = tree.nodes.associateBy { it.id }
        val path = mutableListOf<Pair<DecisionNode, DecisionAnswer?>>()
        var currentId: String? = tree.rootId
        var safety = 0
        while (currentId != null && safety < 100) {
            safety++
            val node = byId[currentId] ?: return path
            val answer = answers[node.id]
            path += node to answer
            if (node.isTerminal) return path
            val nextId = when (answer) {
                DecisionAnswer.YES -> node.yesId
                DecisionAnswer.NO -> node.noId
                DecisionAnswer.UNKNOWN, null -> node.unknownId ?: node.yesId
            }
            currentId = nextId
        }
        return path
    }

    /**
     * Return the next node the user should be asked about, given a partial
     * history of answers. Returns null if the tree is complete (we have
     * reached a terminal node).
     */
    fun nextNode(
        tree: DecisionTree,
        answers: Map<String, DecisionAnswer>
    ): DecisionNode? {
        val byId = tree.nodes.associateBy { it.id }
        var currentId: String? = tree.rootId
        var safety = 0
        while (currentId != null && safety < 100) {
            safety++
            val node = byId[currentId] ?: return null
            if (node.isTerminal) return null
            if (node.id !in answers) return node
            currentId = when (answers[node.id]) {
                DecisionAnswer.YES -> node.yesId
                DecisionAnswer.NO -> node.noId
                DecisionAnswer.UNKNOWN, null -> node.unknownId ?: node.yesId
            }
        }
        return null
    }
}
