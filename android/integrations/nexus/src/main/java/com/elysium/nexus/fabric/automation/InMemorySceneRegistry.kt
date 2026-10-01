package com.elysium.nexus.fabric.automation

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * §36 Concrete Scene Registry Implementation.
 *
 * In-memory scene storage with import/export support.
 * Scenes are portable, serializable, and testable.
 *
 * ## Storage
 *
 * Scenes are stored in a [ConcurrentHashMap] for
 * thread-safe access. For durable (process-death-safe)
 * storage use [RoomSceneRegistry].
 *
 * ## Import/Export
 *
 * Scenes can be imported from declarative YAML/JSON
 * [SceneDefinition] and exported back. The parse/export
 * logic lives in [SceneDefinitionConverter] (shared with
 * the Room-backed registry).
 *
 * ## Tag-based Discovery
 *
 * Scenes are tagged for organized access:
 * - "movie" — movie mode scenes
 * - "morning" — morning routine scenes
 * - "night" — bedtime scenes
 * - "gaming" — gaming mode scenes
 */
class InMemorySceneRegistry : SceneRegistry {

    private val TAG = "SceneRegistry"
    private val scenes = ConcurrentHashMap<String, Scene>()
    private val scenesFlow = MutableStateFlow<List<Scene>>(emptyList())

    override suspend fun getScene(sceneId: String): Scene? {
        return scenes[sceneId]
    }

    override fun observeScenes(): Flow<List<Scene>> = scenesFlow.asStateFlow()

    override suspend fun saveScene(scene: Scene) {
        scenes[scene.id] = scene
        scenesFlow.value = scenes.values.toList()
        Log.d(TAG, "Scene saved: ${scene.name} (${scene.id})")
    }

    override suspend fun deleteScene(sceneId: String) {
        scenes.remove(sceneId)
        scenesFlow.value = scenes.values.toList()
        Log.d(TAG, "Scene deleted: $sceneId")
    }

    override suspend fun importScene(definition: SceneDefinition): Scene {
        val scene = SceneDefinitionConverter.toScene(definition)
        saveScene(scene)
        return scene
    }

    override suspend fun exportScene(sceneId: String): SceneDefinition? {
        val scene = scenes[sceneId] ?: return null
        return SceneDefinitionConverter.toDefinition(scene)
    }

    override suspend fun getScenesByTag(tag: String): List<Scene> {
        return scenes.values.filter { tag in it.tags }
    }
}