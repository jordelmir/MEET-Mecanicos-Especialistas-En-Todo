package com.elysium.nexus.fabric.automation

import com.elysium.nexus.fabric.profile.db.SceneDao
import com.elysium.nexus.fabric.profile.db.SceneEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * §36 durable scene registry — Room-backed.
 *
 * Scenes survive process death and app restarts. The
 * full-fidelity payload is stored via [SceneJsonCodec];
 * listing/tags come from the row columns.
 *
 * Honest contract: a corrupt row is never silently
 * substituted with an empty scene — it is logged loudly
 * and skipped in listings.
 */
class RoomSceneRegistry(
    private val dao: SceneDao,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : SceneRegistry {

    override suspend fun getScene(sceneId: String): Scene? {
        val row = dao.getById(sceneId) ?: return null
        return try {
            SceneJsonCodec.decode(row.payloadJson)
        } catch (e: Exception) {
            SceneRuntimeIssues.report(sceneId, e)
            null
        }
    }

    override fun observeScenes(): Flow<List<Scene>> = dao.observeAll().map { rows ->
        rows.mapNotNull { row ->
            try {
                SceneJsonCodec.decode(row.payloadJson)
            } catch (e: Exception) {
                SceneRuntimeIssues.report(row.sceneId, e)
                null
            }
        }
    }

    override suspend fun saveScene(scene: Scene) {
        val existingCreated = dao.getById(scene.id)?.createdAtEpochMs
        dao.upsert(
            SceneEntity(
                sceneId = scene.id,
                name = scene.name,
                payloadJson = SceneJsonCodec.encode(scene),
                tagsCsv = scene.tags.joinToString(","),
                createdAtEpochMs = existingCreated ?: nowMs(),
                updatedAtEpochMs = nowMs()
            )
        )
    }

    override suspend fun deleteScene(sceneId: String) {
        dao.deleteById(sceneId)
    }

    override suspend fun importScene(definition: SceneDefinition): Scene {
        val scene = SceneDefinitionConverter.toScene(definition)
        saveScene(scene)
        return scene
    }

    override suspend fun exportScene(sceneId: String): SceneDefinition? {
        val scene = getScene(sceneId) ?: return null
        return SceneDefinitionConverter.toDefinition(scene)
    }

    override suspend fun getScenesByTag(tag: String): List<Scene> {
        return dao.observeAll().first().mapNotNull { row ->
            if (row.tagsCsv.split(",").any { it == tag }) {
                try {
                    SceneJsonCodec.decode(row.payloadJson)
                } catch (e: Exception) {
                    SceneRuntimeIssues.report(row.sceneId, e)
                    null
                }
            } else {
                null
            }
        }
    }
}

/** Corrupt-row visibility: last issue surfaced for diagnostics. */
internal object SceneRuntimeIssues {
    @Volatile
    private var lastIssue: SceneRuntimeIssue? = null

    fun report(sceneId: String, cause: Exception) {
        lastIssue = SceneRuntimeIssue(sceneId, cause.message ?: cause::class.simpleName.toString())
        android.util.Log.e(
            "RoomSceneRegistry",
            "corrupt scene row $sceneId: ${cause.message ?: cause::class.simpleName}"
        )
    }

    fun peek(): SceneRuntimeIssue? = lastIssue
}

internal data class SceneRuntimeIssue(
    val sceneId: String,
    val reason: String
)