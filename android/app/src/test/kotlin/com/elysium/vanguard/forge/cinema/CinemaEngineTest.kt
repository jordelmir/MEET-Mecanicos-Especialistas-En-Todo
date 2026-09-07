package com.elysium.vanguard.forge.cinema

import org.junit.Assert.*
import org.junit.Test

class CinemaEngineTest {

    private fun createTestTimeline(): CinemaTimeline {
        val initialEntities = mapOf(
            "cube-1" to EntityState("cube-1", 0f, 0f, 0f),
            "sphere-1" to EntityState("sphere-1", 5f, 0f, 0f),
        )

        val events = listOf(
            EntityMoveEvent(
                timestamp = WorldTimestamp(10),
                eventId = "ev-1",
                entityId = "cube-1",
                toX = 3f, toY = 1f, toZ = 0f,
            ),
            EntitySpawnEvent(
                timestamp = WorldTimestamp(20),
                eventId = "ev-2",
                entity = EntityState("cone-1", 0f, 5f, 0f),
            ),
            EntityPropertyChangeEvent(
                timestamp = WorldTimestamp(30),
                eventId = "ev-3",
                entityId = "sphere-1",
                propertyKey = "color",
                propertyValue = "#FF0000",
            ),
            EntityRemoveEvent(
                timestamp = WorldTimestamp(40),
                eventId = "ev-4",
                entityId = "cube-1",
            ),
            CameraKeyframe(
                timestamp = WorldTimestamp(15),
                eventId = "cam-1",
                positionX = 10f, positionY = 5f, positionZ = 10f,
            ),
        )

        return CinemaTimeline(
            timelineId = "test-timeline",
            title = "Test Cinema",
            initialState = WorldStateSnapshot(
                timestamp = WorldTimestamp(0),
                entities = initialEntities,
                stateHash = CinemaEngine.computeStateHash(initialEntities),
            ),
            events = events,
            totalFrames = 60,
            framesPerSecond = 30,
        )
    }

    // ─── Determinism Invariant (§34) ───

    @Test
    fun `computing state at same timestamp is deterministic`() {
        val timeline = createTestTimeline()
        assertTrue(CinemaEngine.verifyDeterminism(timeline, WorldTimestamp(25)))
    }

    @Test
    fun `state at frame 0 equals initial state`() {
        val timeline = createTestTimeline()
        val state = CinemaEngine.computeState(timeline, WorldTimestamp(0))
        assertEquals(2, state.entityCount)
        assertEquals(timeline.initialState.stateHash, state.stateHash)
    }

    // ─── Event Application ───

    @Test
    fun `move event changes entity position`() {
        val timeline = createTestTimeline()
        val state = CinemaEngine.computeState(timeline, WorldTimestamp(10))
        val cube = state.entities["cube-1"]!!
        assertEquals(3f, cube.positionX)
        assertEquals(1f, cube.positionY)
    }

    @Test
    fun `spawn event adds new entity`() {
        val timeline = createTestTimeline()
        val before = CinemaEngine.computeState(timeline, WorldTimestamp(19))
        val after = CinemaEngine.computeState(timeline, WorldTimestamp(20))
        assertEquals(2, before.entityCount)
        assertEquals(3, after.entityCount)
        assertNotNull(after.entities["cone-1"])
    }

    @Test
    fun `property change updates entity properties`() {
        val timeline = createTestTimeline()
        val state = CinemaEngine.computeState(timeline, WorldTimestamp(30))
        val sphere = state.entities["sphere-1"]!!
        assertEquals("#FF0000", sphere.properties["color"])
    }

    @Test
    fun `remove event deletes entity`() {
        val timeline = createTestTimeline()
        val before = CinemaEngine.computeState(timeline, WorldTimestamp(39))
        val after = CinemaEngine.computeState(timeline, WorldTimestamp(40))
        assertNotNull(before.entities["cube-1"])
        assertNull(after.entities["cube-1"])
    }

    // ─── Camera Does NOT Affect World State (§34) ───

    @Test
    fun `camera keyframe does not change world state`() {
        val timeline = createTestTimeline()
        val beforeCam = CinemaEngine.computeState(timeline, WorldTimestamp(14))
        val afterCam = CinemaEngine.computeState(timeline, WorldTimestamp(15))
        // Camera keyframe at frame 15 must NOT change entity state
        assertEquals(beforeCam.entities, afterCam.entities)
    }

    // ─── Hash Consistency ───

    @Test
    fun `identical entity maps produce identical hashes`() {
        val entities1 = mapOf("a" to EntityState("a", 1f, 2f, 3f))
        val entities2 = mapOf("a" to EntityState("a", 1f, 2f, 3f))
        assertEquals(
            CinemaEngine.computeStateHash(entities1),
            CinemaEngine.computeStateHash(entities2),
        )
    }

    @Test
    fun `different entity maps produce different hashes`() {
        val entities1 = mapOf("a" to EntityState("a", 1f, 2f, 3f))
        val entities2 = mapOf("a" to EntityState("a", 1f, 2f, 4f))
        assertNotEquals(
            CinemaEngine.computeStateHash(entities1),
            CinemaEngine.computeStateHash(entities2),
        )
    }

    // ─── Timeline Properties ───

    @Test
    fun `timeline duration calculates correctly`() {
        val timeline = createTestTimeline()
        assertEquals(2.0, timeline.durationSeconds, 0.001)
    }

    @Test
    fun `world timestamp is non-negative`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorldTimestamp(-1)
        }
    }
}
