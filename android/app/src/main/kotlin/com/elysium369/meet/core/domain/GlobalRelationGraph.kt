package com.elysium369.meet.core.domain

import java.util.UUID

/**
 * GlobalRelationGraph — Cross-domain entity relationship graph (Section 118).
 *
 * Laws:
 * - Entities are never duplicated
 * - Entities are related, not embedded
 * - Each edge has a type and bidirectional query support
 * - No graph DB required initially
 */
enum class GlobalRelationType {
    PRINCIPAL_OWNS_VEHICLE,
    PRINCIPAL_MEMBER_OF_CIRCLE,
    VEHICLE_USED_IN_RIDE,
    RIDE_INVOLVES_FUEL,
    RIDE_GENERATES_EVIDENCE,
    FUEL_LINKED_TO_VEHICLE,
    VEHICLE_LINKED_TO_PROPERTY,
    PRINCIPAL_HAS_PROPERTY,
    PRINCIPAL_IN_LEGAL_CASE,
    VEHICLE_IN_LEGAL_CASE,
    RIDE_IN_LEGAL_CASE,
    EVIDENCE_IN_LEGAL_CASE,
    COMMUNICATION_ABOUT_RIDE,
    COMMUNICATION_ABOUT_PROPERTY,
    COMMUNICATION_ABOUT_LEGAL_CASE,
    PTT_CHANNEL_FOR_RIDE,
    PTT_CHANNEL_FOR_CIRCLE,
    SAFE_JOURNEY_FOR_RIDE,
    SAFE_JOURNEY_INVOLVES_VEHICLE,
    PRESENCE_OF_PRINCIPAL,
    MARKET_LISTING_FOR_PROPERTY,
    REWARD_EARNED_FROM_RIDE,
    REWARD_EARNED_FROM_FUEL,
}

data class GlobalRelationEdge(
    val edgeId: String = UUID.randomUUID().toString(),
    val relationType: GlobalRelationType,
    val sourceEntityId: String,
    val sourceEntityType: String,
    val targetEntityId: String,
    val targetEntityType: String,
    val metadata: Map<String, String> = emptyMap(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

data class GlobalEntitySummary(
    val entityId: String,
    val entityType: String,
    val displayName: String,
    val relationCount: Int,
    val connectedTypes: Set<String>,
)

class GlobalRelationGraph {

    private val edges = mutableListOf<GlobalRelationEdge>()
    private val entityIndex = mutableMapOf<String, MutableList<GlobalRelationEdge>>()
    private val typeIndex = mutableMapOf<String, MutableList<GlobalRelationEdge>>()

    /** Add a relation edge. */
    fun addEdge(edge: GlobalRelationEdge) {
        edges.add(edge)
        entityIndex.getOrPut(edge.sourceEntityId) { mutableListOf() }.add(edge)
        entityIndex.getOrPut(edge.targetEntityId) { mutableListOf() }.add(edge)
        typeIndex.getOrPut(edge.sourceEntityType) { mutableListOf() }.add(edge)
        typeIndex.getOrPut(edge.targetEntityType) { mutableListOf() }.add(edge)
    }

    /** Create and add a relation. */
    fun relate(
        relationType: GlobalRelationType,
        sourceEntityId: String,
        sourceEntityType: String,
        targetEntityId: String,
        targetEntityType: String,
        metadata: Map<String, String> = emptyMap(),
    ): GlobalRelationEdge {
        val edge = GlobalRelationEdge(
            relationType = relationType,
            sourceEntityId = sourceEntityId,
            sourceEntityType = sourceEntityType,
            targetEntityId = targetEntityId,
            targetEntityType = targetEntityType,
            metadata = metadata,
        )
        addEdge(edge)
        return edge
    }

    /** Get all edges connected to an entity. */
    fun getEdgesForEntity(entityId: String): List<GlobalRelationEdge> {
        return entityIndex[entityId] ?: emptyList()
    }

    /** Get edges by relation type. */
    fun getEdgesByType(relationType: GlobalRelationType): List<GlobalRelationEdge> {
        return edges.filter { it.relationType == relationType }
    }

    /** Get edges by entity type. */
    fun getEdgesByEntityType(entityType: String): List<GlobalRelationEdge> {
        return typeIndex[entityType] ?: emptyList()
    }

    /** Get summary of an entity's relations. */
    fun getEntitySummary(entityId: String): GlobalEntitySummary? {
        val entityEdges = entityIndex[entityId] ?: return null
        val connectedTypes = entityEdges.map { edge ->
            if (edge.sourceEntityId == entityId) edge.targetEntityType else edge.sourceEntityType
        }.toSet()

        return GlobalEntitySummary(
            entityId = entityId,
            entityType = entityEdges.firstOrNull()?.let {
                if (it.sourceEntityId == entityId) it.sourceEntityType else it.targetEntityType
            } ?: "UNKNOWN",
            displayName = entityId,
            relationCount = entityEdges.size,
            connectedTypes = connectedTypes,
        )
    }

    /** Find related entities of a specific type. */
    fun findRelated(
        entityId: String,
        targetType: String,
    ): List<GlobalRelationEdge> {
        return entityIndex[entityId]?.filter { edge ->
            val neighborType = if (edge.sourceEntityId == entityId) edge.targetEntityType else edge.sourceEntityType
            neighborType == targetType
        } ?: emptyList()
    }

    /** Get all entities of a given type. */
    fun getEntitiesOfType(entityType: String): Set<String> {
        val result = mutableSetOf<String>()
        for (edge in typeIndex[entityType] ?: emptyList()) {
            if (edge.sourceEntityType == entityType) result.add(edge.sourceEntityId)
            if (edge.targetEntityType == entityType) result.add(edge.targetEntityId)
        }
        return result
    }

    /** Get total edge count. */
    fun size(): Int = edges.size

    /** Get all edges. */
    fun getAllEdges(): List<GlobalRelationEdge> = edges.toList()

    /** Remove all edges for an entity. */
    fun removeEntity(entityId: String) {
        val relatedEdges = entityIndex.remove(entityId) ?: return
        for (edge in relatedEdges) {
            edges.remove(edge)
            entityIndex[edge.sourceEntityId]?.remove(edge)
            entityIndex[edge.targetEntityId]?.remove(edge)
            typeIndex[edge.sourceEntityType]?.remove(edge)
            typeIndex[edge.targetEntityType]?.remove(edge)
        }
    }
}
