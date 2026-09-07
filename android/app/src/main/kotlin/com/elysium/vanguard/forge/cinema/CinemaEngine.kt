package com.elysium.vanguard.forge.cinema

import kotlinx.serialization.Serializable

/**
 * ASTRA V6 §34 — Interactive/Executable Cinema.
 *
 * Cinema Determinism Invariant:
 * "Viewer exploration must NOT mutate canonical state."
 *
 * The world has a canonical timeline of states. A viewer can:
 * - Detach the camera and explore freely
 * - Rewind/forward in time
 * - View from any angle
 * - BUT never change the canonical sequence of events
 *
 * WorldState(t) is a pure function: given the same initial conditions
 * and the same timeline of events, the result is always identical.
 * Hash(WorldState(t₁)) == Hash(WorldState(t₁)) for all replays.
 */

// ─── Timeline Primitives ───

@Serializable
data class WorldTimestamp(val frameIndex: Long) {
    init { require(frameIndex >= 0) { "Frame index cannot be negative" } }
    operator fun compareTo(other: WorldTimestamp): Int = frameIndex.compareTo(other.frameIndex)
    operator fun plus(frames: Long) = WorldTimestamp(frameIndex + frames)
    operator fun minus(other: WorldTimestamp) = frameIndex - other.frameIndex
}

@Serializable
data class WorldStateSnapshot(
    val timestamp: WorldTimestamp,
    val entities: Map<String, EntityState>,
    val stateHash: String,
) {
    val entityCount: Int get() = entities.size
}

@Serializable
data class EntityState(
    val entityId: String,
    val positionX: Float,
    val positionY: Float,
    val positionZ: Float,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val scaleZ: Float = 1f,
    val visible: Boolean = true,
    val properties: Map<String, String> = emptyMap(),
)

// ─── Timeline Events (canonical, immutable) ───

@Serializable
sealed class TimelineEvent {
    abstract val timestamp: WorldTimestamp
    abstract val eventId: String
}

@Serializable
data class EntityMoveEvent(
    override val timestamp: WorldTimestamp,
    override val eventId: String,
    val entityId: String,
    val toX: Float,
    val toY: Float,
    val toZ: Float,
    val durationFrames: Long = 1,
) : TimelineEvent()

@Serializable
data class EntitySpawnEvent(
    override val timestamp: WorldTimestamp,
    override val eventId: String,
    val entity: EntityState,
) : TimelineEvent()

@Serializable
data class EntityRemoveEvent(
    override val timestamp: WorldTimestamp,
    override val eventId: String,
    val entityId: String,
) : TimelineEvent()

@Serializable
data class EntityPropertyChangeEvent(
    override val timestamp: WorldTimestamp,
    override val eventId: String,
    val entityId: String,
    val propertyKey: String,
    val propertyValue: String,
) : TimelineEvent()

@Serializable
data class CameraKeyframe(
    override val timestamp: WorldTimestamp,
    override val eventId: String,
    val targetEntityId: String? = null,
    val positionX: Float,
    val positionY: Float,
    val positionZ: Float,
    val lookAtX: Float = 0f,
    val lookAtY: Float = 0f,
    val lookAtZ: Float = 0f,
) : TimelineEvent()

// ─── Camera State (viewer-controlled, NOT canonical) ───

@Serializable
data class CameraState(
    val positionX: Float,
    val positionY: Float,
    val positionZ: Float,
    val lookAtX: Float = 0f,
    val lookAtY: Float = 0f,
    val lookAtZ: Float = 0f,
    val isDetached: Boolean = false,
)

// ─── Cinema Timeline (the deterministic engine) ───

/**
 * A complete cinema timeline. Given the same initialState and events,
 * [computeState] always returns the same result for the same timestamp.
 */
@Serializable
data class CinemaTimeline(
    val timelineId: String,
    val title: String,
    val initialState: WorldStateSnapshot,
    val events: List<TimelineEvent>,
    val totalFrames: Long,
    val framesPerSecond: Int = 30,
) {
    val durationSeconds: Double get() = totalFrames.toDouble() / framesPerSecond
}

object CinemaEngine {

    /**
     * Deterministic state computation.
     * Applies all events up to (and including) the given timestamp
     * to the initial state. This is a PURE function.
     */
    fun computeState(timeline: CinemaTimeline, at: WorldTimestamp): WorldStateSnapshot {
        val entities = timeline.initialState.entities.toMutableMap()

        val applicableEvents = timeline.events
            .filter { it.timestamp.frameIndex <= at.frameIndex }
            .sortedBy { it.timestamp.frameIndex }

        for (event in applicableEvents) {
            when (event) {
                is EntitySpawnEvent -> {
                    entities[event.entity.entityId] = event.entity
                }
                is EntityRemoveEvent -> {
                    entities.remove(event.entityId)
                }
                is EntityMoveEvent -> {
                    val existing = entities[event.entityId]
                    if (existing != null) {
                        entities[event.entityId] = existing.copy(
                            positionX = event.toX,
                            positionY = event.toY,
                            positionZ = event.toZ,
                        )
                    }
                }
                is EntityPropertyChangeEvent -> {
                    val existing = entities[event.entityId]
                    if (existing != null) {
                        entities[event.entityId] = existing.copy(
                            properties = existing.properties + (event.propertyKey to event.propertyValue),
                        )
                    }
                }
                is CameraKeyframe -> {
                    // Camera keyframes do NOT affect world state (§34)
                }
            }
        }

        val stateHash = computeStateHash(entities)
        return WorldStateSnapshot(
            timestamp = at,
            entities = entities.toMap(),
            stateHash = stateHash,
        )
    }

    /**
     * Deterministic hash of world state.
     * Two identical world states MUST produce identical hashes.
     */
    fun computeStateHash(entities: Map<String, EntityState>): String {
        val canonical = entities.entries
            .sortedBy { it.key }
            .joinToString("|") { (id, state) ->
                "$id:${state.positionX},${state.positionY},${state.positionZ}:" +
                    "${state.rotationX},${state.rotationY},${state.rotationZ}:" +
                    "${state.scaleX},${state.scaleY},${state.scaleZ}:" +
                    "${state.visible}:" +
                    state.properties.entries.sortedBy { it.key }
                        .joinToString(",") { "${it.key}=${it.value}" }
            }
        // Use simple hash for now; SHA-256 for production
        return "state-${canonical.hashCode().toUInt()}"
    }

    /**
     * §34 Cinema Determinism Invariant Test:
     * Computing state at the same timestamp twice MUST yield identical hashes.
     */
    fun verifyDeterminism(timeline: CinemaTimeline, at: WorldTimestamp): Boolean {
        val state1 = computeState(timeline, at)
        val state2 = computeState(timeline, at)
        return state1.stateHash == state2.stateHash
    }
}
