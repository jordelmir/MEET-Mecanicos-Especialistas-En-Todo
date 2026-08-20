package com.elysium369.meet.core.proofgraph

import com.elysium369.meet.core.domain.ClaimNature
import com.elysium369.meet.core.domain.EntityRef
import com.elysium369.meet.core.domain.SourceAuthority

enum class ProofRelationType {
    PROVES,
    RESOLVES,
    CAUSED_BY,
    DOCUMENTED_IN,
    INSTALLED_IN,
    DERIVED_FROM,
    VERIFIED_BY
}

data class ProofNode(
    val nodeId: String,
    val entityRef: EntityRef,
    val label: String,
    val authority: SourceAuthority,
    val timestampUtc: Long,
    val metadata: Map<String, String> = emptyMap()
)

data class ProofEdge(
    val edgeId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val relationType: ProofRelationType,
    val notes: String? = null
)

data class VerificationResult(
    val isVerified: Boolean,
    val integrityHash: String?,
    val verifiedAtUtc: Long,
    val verifierName: String,
    val notes: String
)

data class ProofGraph(
    val vehicleId: String,
    val nodes: Map<String, ProofNode> = emptyMap(),
    val edges: List<ProofEdge> = emptyList()
) {
    fun getAncestors(nodeId: String): List<ProofNode> {
        val directEdges = edges.filter { it.toNodeId == nodeId }
        return directEdges.mapNotNull { nodes[it.fromNodeId] }
    }

    fun getDescendants(nodeId: String): List<ProofNode> {
        val directEdges = edges.filter { it.fromNodeId == nodeId }
        return directEdges.mapNotNull { nodes[it.toNodeId] }
    }
}
