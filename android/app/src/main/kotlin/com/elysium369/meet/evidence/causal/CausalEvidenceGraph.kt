package com.elysium369.meet.evidence.causal

import com.elysium369.meet.authority.VerificationLevel
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class CausalNodeType {
    OBSERVATION,                // DTC observed, Freeze Frame captured, physical sensor anomaly
    HYPOTHESIS,                 // Diagnostic reasoning, AI hypothesis, inspection suspect
    DIAGNOSTIC_TEST,            // Specific test executed (e.g. cylinder balance, smoke test)
    PHYSICAL_MEASUREMENT,       // Empirical measurement (e.g. 1.2 bar, 0.45 ohm, 14.1 V)
    REPAIR_INTERVENTION,        // Repair procedure executed by technician
    PART_REPLACEMENT,           // OEM or verified part installed (VIN-matched)
    POST_VERIFICATION,          // Post-repair scan, road test, readiness completion
    CLEARANCE_CERTIFICATE,      // Certified clearance / forensic stamp
}

enum class CausalRelationType {
    OBSERVATION_CAUSED_HYPOTHESIS,
    HYPOTHESIS_LED_TO_TEST,
    TEST_PRODUCED_MEASUREMENT,
    MEASUREMENT_JUSTIFIED_REPAIR,
    REPAIR_EXECUTED_WITH_PART,
    REPAIR_TRIGGERED_POST_VERIFICATION,
    POST_VERIFICATION_CERTIFIED_CLEARANCE,
}

data class CausalEvidenceNode(
    val nodeId: String = UUID.randomUUID().toString(),
    val entityId: String,               // Vehicle ID, VIN, or Trip ID
    val nodeType: CausalNodeType,
    val relationToParent: CausalRelationType? = null,
    val parentIds: List<String> = emptyList(),
    val payloadJson: String,
    val proofHash: String,
    val recordedAtEpochMs: Long = System.currentTimeMillis(),
    val verificationLevel: VerificationLevel = VerificationLevel.CRYPTOGRAPHICALLY_ANCHORED,
) {
    companion object {
        fun computeProofHash(
            nodeId: String,
            entityId: String,
            nodeType: CausalNodeType,
            parentHashes: List<String>,
            payloadJson: String,
            timestampEpochMs: Long,
        ): String {
            val sortedParentHashes = parentHashes.sorted().joinToString(":")
            val raw = "$nodeId:$entityId:$nodeType:$sortedParentHashes:$payloadJson:$timestampEpochMs"
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * ElysiumCausalEvidenceGraph — Verifiable memory of vehicle truth, repairs, and diagnostics.
 *
 * Implements a cryptographically chained Directed Acyclic Graph (DAG) that answers
 * not only WHAT happened, but WHY we believe it happened, and with WHAT immutable evidence.
 */
@Singleton
class CausalEvidenceGraph @Inject constructor() {

    private val nodes = ConcurrentHashMap<String, CausalEvidenceNode>()
    private val entityIndex = ConcurrentHashMap<String, MutableList<String>>()
    private val childrenIndex = ConcurrentHashMap<String, MutableList<String>>()

    /**
     * Appends a new causal evidence node to the graph.
     * Enforces that all specified parent nodes must already exist in the graph.
     */
    fun appendNode(
        entityId: String,
        nodeType: CausalNodeType,
        payloadJson: String,
        relationToParent: CausalRelationType? = null,
        parentIds: List<String> = emptyList(),
        verificationLevel: VerificationLevel = VerificationLevel.CRYPTOGRAPHICALLY_ANCHORED,
        timestampEpochMs: Long = System.currentTimeMillis(),
    ): CausalEvidenceNode {
        // Collect parent hashes to cryptographically chain them
        val parentHashes = parentIds.map { parentId ->
            val parent = nodes[parentId] ?: throw IllegalArgumentException("Parent node $parentId does not exist in CausalEvidenceGraph")
            parent.proofHash
        }

        val nodeId = UUID.randomUUID().toString()
        val proofHash = CausalEvidenceNode.computeProofHash(
            nodeId = nodeId,
            entityId = entityId,
            nodeType = nodeType,
            parentHashes = parentHashes,
            payloadJson = payloadJson,
            timestampEpochMs = timestampEpochMs,
        )

        val node = CausalEvidenceNode(
            nodeId = nodeId,
            entityId = entityId,
            nodeType = nodeType,
            relationToParent = relationToParent,
            parentIds = parentIds,
            payloadJson = payloadJson,
            proofHash = proofHash,
            recordedAtEpochMs = timestampEpochMs,
            verificationLevel = verificationLevel,
        )

        nodes[nodeId] = node
        entityIndex.getOrPut(entityId) { mutableListOf() }.add(nodeId)
        for (parentId in parentIds) {
            childrenIndex.getOrPut(parentId) { mutableListOf() }.add(nodeId)
        }

        return node
    }

    fun getNode(nodeId: String): CausalEvidenceNode? = nodes[nodeId]

    fun getNodesForEntity(entityId: String): List<CausalEvidenceNode> {
        val nodeIds = entityIndex[entityId] ?: return emptyList()
        return nodeIds.mapNotNull { nodes[it] }
    }

    /**
     * Verifies cryptographic chain integrity starting from a specific node up to all root ancestors.
     * Returns true if and only if every node along the path has an untampered hash.
     */
    fun verifyChainIntegrity(nodeId: String): Boolean {
        val node = nodes[nodeId] ?: return false
        val parentHashes = node.parentIds.map { parentId ->
            val parent = nodes[parentId] ?: return false
            parent.proofHash
        }

        val expectedHash = CausalEvidenceNode.computeProofHash(
            nodeId = node.nodeId,
            entityId = node.entityId,
            nodeType = node.nodeType,
            parentHashes = parentHashes,
            payloadJson = node.payloadJson,
            timestampEpochMs = node.recordedAtEpochMs,
        )

        if (node.proofHash != expectedHash) return false

        // Recursively verify all parents
        for (parentId in node.parentIds) {
            if (!verifyChainIntegrity(parentId)) return false
        }

        return true
    }

    /**
     * Traces the causal path backwards from a terminal node (e.g. CLEARANCE_CERTIFICATE)
     * back to initial root cause OBSERVATIONs.
     */
    fun traceRootCauses(terminalNodeId: String): List<CausalEvidenceNode> {
        val visited = mutableSetOf<String>()
        val result = mutableListOf<CausalEvidenceNode>()
        val queue = ArrayDeque<String>()
        queue.add(terminalNodeId)

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            if (visited.add(currentId)) {
                val node = nodes[currentId]
                if (node != null) {
                    result.add(node)
                    for (parentId in node.parentIds) {
                        queue.add(parentId)
                    }
                }
            }
        }

        return result
    }

    fun clear() {
        nodes.clear()
        entityIndex.clear()
        childrenIndex.clear()
    }
}
