package com.elysium369.meet.legal.domain

import java.util.UUID

/**
 * LegalRelationGraph — Cross-domain legal relationships (Section 84).
 *
 * Laws:
 * - No graph DB required initially
 * - Relationships are directional
 * - Each edge has a type and metadata
 * - Query by entity to find connected entities
 */
enum class LegalRelationType {
    PERSON_PARTICIPATED_IN_EVENT,
    EVENT_OCCURRED_AT_LOCATION,
    CAMERA_OBSERVES_LOCATION,
    EVIDENCE_SUPPORTS_CONTEXT_OF_EVENT,
    EVENT_INVOLVES_VEHICLE,
    DOCUMENT_ISSUED_BY_ORGANIZATION,
    EVENT_RELATED_TO_EVENT,
    PERSON_ASSOCIATED_WITH_ORGANIZATION,
    EVIDENCE_COLLECTED_BY_PERSON,
    EVENT_OCCURRED_ON_DATE,
    VEHICLE_DAMAGED_IN_EVENT,
    PERSON_WITNESSED_EVENT,
    EXPENSE_ASSOCIATED_WITH_EVENT,
    DAMAGE_RESULTED_FROM_EVENT,
    MESSAGE_RELATED_TO_EVENT,
}

data class LegalRelationEdge(
    val edgeId: String = UUID.randomUUID().toString(),
    val relationType: LegalRelationType,
    val sourceEntityId: String,
    val targetEntityId: String,
    val metadata: Map<String, String> = emptyMap(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f, // 0.0 to 1.0
)

data class LegalRelationNode(
    val entityId: String,
    val entityType: String, // PERSON, EVENT, LOCATION, CAMERA, EVIDENCE, DOCUMENT, ORG, VEHICLE
    val displayName: String,
    val edges: List<LegalRelationEdge> = emptyList(),
)

class LegalRelationGraph {

    private val edges = mutableListOf<LegalRelationEdge>()
    private val entityIndex = mutableMapOf<String, MutableList<LegalRelationEdge>>()

    /** Add a relation edge. */
    fun addEdge(edge: LegalRelationEdge) {
        edges.add(edge)
        entityIndex.getOrPut(edge.sourceEntityId) { mutableListOf() }.add(edge)
        entityIndex.getOrPut(edge.targetEntityId) { mutableListOf() }.add(edge)
    }

    /** Create and add a relation. */
    fun relate(
        relationType: LegalRelationType,
        sourceEntityId: String,
        targetEntityId: String,
        metadata: Map<String, String> = emptyMap(),
        confidence: Float = 1.0f,
    ): LegalRelationEdge {
        val edge = LegalRelationEdge(
            relationType = relationType,
            sourceEntityId = sourceEntityId,
            targetEntityId = targetEntityId,
            metadata = metadata,
            confidence = confidence,
        )
        addEdge(edge)
        return edge
    }

    /** Get all edges connected to an entity. */
    fun getEdgesForEntity(entityId: String): List<LegalRelationEdge> {
        return entityIndex[entityId] ?: emptyList()
    }

    /** Get edges by relation type. */
    fun getEdgesByType(relationType: LegalRelationType): List<LegalRelationEdge> {
        return edges.filter { it.relationType == relationType }
    }

    /** Find all entities connected to a given entity (BFS, depth-limited). */
    fun findConnected(entityId: String, maxDepth: Int = 2): Map<String, Set<String>> {
        val visited = mutableSetOf<String>()
        val result = mutableMapOf<String, MutableSet<String>>()
        val queue = mutableListOf(entityId to 0)

        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeAt(0)
            if (current in visited || depth > maxDepth) continue
            visited.add(current)

            val connectedEdges = entityIndex[current] ?: emptyList()
            for (edge in connectedEdges) {
                val neighbor = if (edge.sourceEntityId == current) edge.targetEntityId else edge.sourceEntityId
                if (neighbor !in visited) {
                    result.getOrPut(current) { mutableSetOf() }.add(neighbor)
                    if (depth < maxDepth) {
                        queue.add(neighbor to depth + 1)
                    }
                }
            }
        }

        return result
    }

    /** Find shortest path between two entities. */
    fun findPath(from: String, to: String, maxDepth: Int = 4): List<String>? {
        val visited = mutableSetOf(from)
        val queue = mutableListOf(listOf(from))

        while (queue.isNotEmpty()) {
            val path = queue.removeAt(0)
            val current = path.last()

            if (current == to) return path
            if (path.size > maxDepth) continue

            val connectedEdges = entityIndex[current] ?: emptyList()
            for (edge in connectedEdges) {
                val neighbor = if (edge.sourceEntityId == current) edge.targetEntityId else edge.sourceEntityId
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(path + neighbor)
                }
            }
        }

        return null
    }

    /** Get all edges. */
    fun getAllEdges(): List<LegalRelationEdge> = edges.toList()

    /** Get total edge count. */
    fun size(): Int = edges.size

    /** Remove all edges for an entity. */
    fun removeEntity(entityId: String) {
        val relatedEdges = entityIndex.remove(entityId) ?: return
        for (edge in relatedEdges) {
            edges.remove(edge)
            entityIndex[edge.sourceEntityId]?.remove(edge)
            entityIndex[edge.targetEntityId]?.remove(edge)
        }
    }
}
