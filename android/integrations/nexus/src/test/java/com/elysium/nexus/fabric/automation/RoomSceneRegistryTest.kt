package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.profile.db.SceneDao
import com.elysium.nexus.fabric.profile.db.SceneEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §36 durable registry: RoomSceneRegistry against an in-memory
 * fake DAO. Validates that save/get/delete/tags flow through the
 * full-fidelity codec and that corrupt payloads are surfaced,
 * not silently substituted.
 */
class RoomSceneRegistryTest {

    private class FakeSceneDao : SceneDao {
        private val rows = mutableMapOf<String, SceneEntity>()
        private val flow = MutableStateFlow<List<SceneEntity>>(emptyList())

        override suspend fun getById(sceneId: String): SceneEntity? = rows[sceneId]

        override fun observeAll(): Flow<List<SceneEntity>> = flow

        override suspend fun upsert(entity: SceneEntity) {
            rows[entity.sceneId] = entity
            flow.value = rows.values.toList()
        }

        override suspend fun deleteById(sceneId: String) {
            rows.remove(sceneId)
            flow.value = rows.values.toList()
        }

        override suspend fun count(): Int = rows.size
    }

    private fun scene(id: String, name: String, tag: String? = null): Scene = Scene(
        id = id,
        name = name,
        steps = listOf(
            ActionStep(
                targetDeviceId = DeviceId("tv"),
                action = UniversalAction.PowerOn(DeviceId("tv")),
                timeoutMs = 5_000L
            )
        ),
        tags = if (tag != null) setOf(tag) else emptySet()
    )

    @Test
    fun saveAndGetSceneRoundTripsFidelity() = runBlocking {
        val registry = RoomSceneRegistry(FakeSceneDao(), nowMs = { 42L })
        val original = scene("s1", "Movie Night", "movie")

        registry.saveScene(original)
        val got = registry.getScene("s1")

        assertEquals(original.name, got!!.name)
        assertEquals(original.tags, got.tags)
        assertEquals(original.steps.size, got.steps.size)
        assertEquals(
            original.steps[0].action::class.simpleName,
            got.steps[0].action::class.simpleName
        )
        assertEquals(5_000L, got.steps[0].timeoutMs)
    }

    @Test
    fun saveKeepsCreatedTimestampOnUpdate() = runBlocking {
        val dao = FakeSceneDao()
        val registry = RoomSceneRegistry(dao, nowMs = { 100L })

        registry.saveScene(scene("s2", "First"))
        registry.saveScene(scene("s2", "Updated"))

        val row = dao.getById("s2")
        assertEquals(100L, row!!.createdAtEpochMs) // preserved
        assertEquals("Updated", row.name)
    }

    @Test
    fun deleteRemovesScene() = runBlocking {
        val registry = RoomSceneRegistry(FakeSceneDao())
        registry.saveScene(scene("s3", "Temp"))
        registry.deleteScene("s3")
        assertNull(registry.getScene("s3"))
    }

    @Test
    fun observeScenesEmitsSavedScenes() = runBlocking {
        val registry = RoomSceneRegistry(FakeSceneDao())
        registry.saveScene(scene("s4", "A", "night"))
        registry.saveScene(scene("s5", "B", "movie"))

        val observed = registry.observeScenes().first()
        assertEquals(2, observed.size)
    }

    @Test
    fun getScenesByTagFilters() = runBlocking {
        val registry = RoomSceneRegistry(FakeSceneDao())
        registry.saveScene(scene("s6", "Night A", "night"))
        registry.saveScene(scene("s7", "Movie B", "movie"))

        val night = registry.getScenesByTag("night")
        assertEquals(listOf("s6"), night.map { it.id })
    }

    @Test
    fun importAndExportSceneDefinitionRoundTrips() = runBlocking {
        val registry = RoomSceneRegistry(FakeSceneDao())
        val imported = registry.importScene(
            SceneDefinition(
                scene = "Imported",
                description = "from DSL",
                steps = listOf(
                    StepDefinition(device = "tv", action = "power_on", timeout = 4_000L)
                ),
                tags = setOf("dsl")
            )
        )

        // persisted (semantic equality: runtime-only fields regenerate)
        val persisted = registry.getScene(imported.id)
        assertEquals(imported.id, persisted!!.id)
        assertEquals(imported.name, persisted.name)
        assertEquals(imported.description, persisted.description)
        assertEquals(imported.tags, persisted.tags)
        assertEquals(imported.steps.size, persisted.steps.size)
        assertEquals(
            imported.steps[0].action::class.simpleName,
            persisted.steps[0].action::class.simpleName
        )

        // export back
        val exported = registry.exportScene(imported.id)
        assertEquals("Imported", exported!!.scene)
        assertEquals("power_on", exported.steps.single().action)
    }

    @Test
    fun corruptPayloadIsSurfacedNotSubstituted() = runBlocking {
        val dao = FakeSceneDao()
        val registry = RoomSceneRegistry(dao)
        // Insert a corrupt row directly behind the registry's back.
        dao.upsert(
            SceneEntity(
                sceneId = "corrupt-1",
                name = "Broken",
                payloadJson = "{not json",
                tagsCsv = "",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )

        assertNull("corrupt row must not decode", registry.getScene("corrupt-1"))
        assertTrue(
            "corrupt row must be reported",
            SceneRuntimeIssues.peek()?.sceneId == "corrupt-1"
        )
        val observed = registry.observeScenes().first()
        assertTrue("corrupt row must not appear in listing", observed.isEmpty())
    }
}