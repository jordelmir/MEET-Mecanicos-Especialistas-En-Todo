package com.elysium369.meet.evidence.causal.local

import androidx.room.*

/**
 * ══════════════════════════════════════════════════════════════════════
 *  D U R A B L E   C A U S A L   E V I D E N C E   E N T I T I E S
 *  ──────────────────────────────────────────────────────────────
 *  Governed by: ELYSIUM MASTER IMPLEMENTATION ORDER — ASCENSION MAXIMA §8
 *
 *  - Causal DAG nodes and edges are persisted durably in Room.
 *  - No volatile in-memory collections (ConcurrentHashMap lost on process death).
 *  - Atomic transaction insertion for DAG consistency.
 * ══════════════════════════════════════════════════════════════════════
 */

@Entity(
    tableName = "causal_evidence_nodes",
    indices = [
        Index("entityId"),
        Index("proofHash", unique = true),
    ],
)
data class CausalEvidenceNodeEntity(
    @PrimaryKey val nodeId: String,
    val entityId: String,
    val nodeType: String,
    val payloadJson: String,
    val proofHash: String,
    val recordedAtEpochMs: Long,
    val verificationLevel: String,
)

@Entity(
    tableName = "causal_evidence_edges",
    primaryKeys = [
        "parentNodeId",
        "childNodeId",
    ],
)
data class CausalEvidenceEdgeEntity(
    val parentNodeId: String,
    val childNodeId: String,
    val relationType: String,
)

@Dao
interface CausalEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNode(node: CausalEvidenceNodeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEdges(edges: List<CausalEvidenceEdgeEntity>)

    @Transaction
    suspend fun insertDagStep(
        node: CausalEvidenceNodeEntity,
        edges: List<CausalEvidenceEdgeEntity>,
    ) {
        insertNode(node)
        if (edges.isNotEmpty()) {
            insertEdges(edges)
        }
    }

    @Query("""
        SELECT * FROM causal_evidence_nodes
        WHERE entityId = :entityId
        ORDER BY recordedAtEpochMs ASC
    """)
    suspend fun nodesForEntity(entityId: String): List<CausalEvidenceNodeEntity>

    @Query("""
        SELECT * FROM causal_evidence_nodes
        WHERE nodeId = :nodeId
        LIMIT 1
    """)
    suspend fun nodeById(nodeId: String): CausalEvidenceNodeEntity?

    @Query("""
        SELECT parentNodeId FROM causal_evidence_edges
        WHERE childNodeId = :childNodeId
    """)
    suspend fun parentIdsForNode(childNodeId: String): List<String>

    @Query("""
        SELECT childNodeId FROM causal_evidence_edges
        WHERE parentNodeId = :parentNodeId
    """)
    suspend fun childIdsForNode(parentNodeId: String): List<String>
}
