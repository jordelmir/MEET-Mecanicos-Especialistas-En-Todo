package com.elysium369.meet.core.catalog

import android.content.Context
import java.text.Normalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROPRIETARY_CATALOG_MANIFEST_ASSET = "knowledge/proprietary/manifest.json"
const val PROPRIETARY_CATALOG_ENTITY_INDEX_ASSET = "knowledge/proprietary/entity_index.json"
const val PROPRIETARY_VEHICLE_LABEL = "Hyundai Accent/Verna 2005 · caja automática · motor 1600 cc"

@Serializable
data class ProprietaryCatalogManifest(
    val schemaVersion: Int,
    val corpusId: String,
    val corpusVersion: String,
    val title: String,
    val vehicleLabel: String,
    val provenanceLabel: String,
    val visualAuthority: String,
    val sourceDocuments: List<ProprietarySourceDocument>,
    val systems: List<ProprietaryCatalogSystem>,
    val sections: List<ProprietaryCatalogSection>,
    val entityIndexPath: String,
    val statistics: ProprietaryCatalogStatistics,
    val contentSha256: String
)

@Serializable
data class ProprietarySourceDocument(
    val id: String,
    val sourceFileName: String,
    val sourceSha256: String,
    val blockCount: Int,
    val ownership: String
)

@Serializable
data class ProprietaryCatalogSystem(
    val id: String,
    val title: String,
    val color: String,
    val sectionCount: Int,
    val blockCount: Int,
    val entityCount: Int,
    val realCaseCount: Int
)

@Serializable
data class ProprietaryCatalogSection(
    val id: String,
    val systemId: String,
    val titleOriginal: String,
    val sourceDocumentId: String,
    val sourceFileName: String,
    val sourceDocumentSha256: String,
    val sourceOrderStart: Int,
    val sourceOrderEnd: Int,
    val blockCount: Int,
    val entityCount: Int,
    val realCaseCount: Int,
    val shardPath: String,
    val contentSha256: String
)

@Serializable
data class ProprietaryCatalogStatistics(
    val blockCount: Int,
    val entityCount: Int,
    val realCaseCount: Int,
    val sectionCount: Int,
    val shardCount: Int,
    val roleCounts: Map<String, Int>
)

@Serializable
data class ProprietaryEntityIndex(
    val schemaVersion: Int,
    val corpusId: String,
    val corpusVersion: String,
    val vehicleLabel: String,
    val entities: List<ProprietaryCatalogEntity>,
    val contentSha256: String
)

@Serializable
data class ProprietaryCatalogEntity(
    val id: String,
    val nameOriginal: String,
    val recordRole: String,
    val systemId: String,
    val sectionId: String,
    val shardPath: String,
    val sourceDocumentId: String,
    val sourceFileName: String,
    val sourceDocumentSha256: String,
    val sourceBlockId: String,
    val sourceTextHash: String,
    val sourceOrder: Int,
    val vehicleScope: String,
    val threeDimensionalBinding: ProprietaryThreeDimensionalBinding
)

@Serializable
data class ProprietaryThreeDimensionalBinding(
    val sceneId: String,
    val nodeId: String,
    val visualAuthority: String,
    val isDimensionalModel: Boolean,
    val seed: Long
)

@Serializable
data class ProprietarySectionShard(
    val schemaVersion: Int,
    val corpusId: String,
    val sectionId: String,
    val systemId: String,
    val titleOriginal: String,
    val vehicleLabel: String,
    val sourceDocumentId: String,
    val sourceFileName: String,
    val sourceDocumentSha256: String,
    val blocks: List<ProprietarySourceBlock>,
    val contentSha256: String
)

@Serializable
data class ProprietarySourceBlock(
    val blockId: String,
    val kind: String,
    val order: Int,
    val recordRole: String,
    val sectionPath: List<String>,
    val styleId: String = "",
    val text: String,
    val textHash: String,
    val entityId: String? = null,
    val parentEntityId: String? = null,
    val rows: List<List<String>>? = null
)

object ProprietaryCatalogParser {
    private val json = Json { ignoreUnknownKeys = false }

    fun decodeManifest(raw: String): ProprietaryCatalogManifest =
        json.decodeFromString<ProprietaryCatalogManifest>(raw).also { manifest ->
            require(manifest.schemaVersion == 1) { "Unsupported proprietary manifest schema" }
            require(manifest.vehicleLabel == PROPRIETARY_VEHICLE_LABEL) { "Unexpected vehicle label" }
            require(manifest.statistics.blockCount == 74_648) { "Incomplete proprietary corpus" }
            require(manifest.statistics.entityCount > 4_500) { "Incomplete proprietary entity index" }
            require(manifest.sections.size == manifest.statistics.sectionCount) { "Section count mismatch" }
            require(manifest.sourceDocuments.map { it.sourceSha256 }.toSet() == EXPECTED_PROPRIETARY_SOURCE_HASHES) {
                "Unexpected proprietary source documents"
            }
        }

    fun decodeEntityIndex(raw: String): ProprietaryEntityIndex =
        json.decodeFromString<ProprietaryEntityIndex>(raw).also { index ->
            require(index.schemaVersion == 1) { "Unsupported proprietary index schema" }
            require(index.vehicleLabel == PROPRIETARY_VEHICLE_LABEL) { "Unexpected vehicle label" }
            require(index.entities.map { it.id }.distinct().size == index.entities.size) { "Duplicate proprietary entity ID" }
            require(index.entities.all { entity ->
                entity.id == entity.threeDimensionalBinding.nodeId &&
                    entity.threeDimensionalBinding.visualAuthority == "PROCEDURAL_SCHEMATIC" &&
                    !entity.threeDimensionalBinding.isDimensionalModel
            }) { "Broken or overstated proprietary 3D binding" }
        }

    fun decodeSection(raw: String): ProprietarySectionShard =
        json.decodeFromString<ProprietarySectionShard>(raw).also { shard ->
            require(shard.schemaVersion == 1) { "Unsupported proprietary shard schema" }
            require(shard.vehicleLabel == PROPRIETARY_VEHICLE_LABEL) { "Unexpected shard vehicle label" }
            require(shard.blocks.map { it.blockId }.distinct().size == shard.blocks.size) { "Duplicate block in shard" }
        }

    private val EXPECTED_PROPRIETARY_SOURCE_HASHES = setOf(
        "09f2926a22542a4e7be24e50f2a4f4c42674f32958e8e541683fbb0cf76352d7",
        "baf4add3f22202fc7d66f7b7f4aee549d90780f1891da6fa66ffbc2db1820824"
    )
}

class ProprietaryPartsCatalogRepository(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var manifestCache: ProprietaryCatalogManifest? = null
    @Volatile private var indexCache: ProprietaryEntityIndex? = null
    private val shardCache = object : LinkedHashMap<String, ProprietarySectionShard>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ProprietarySectionShard>?): Boolean = size > 6
    }

    fun loadManifest(): ProprietaryCatalogManifest = manifestCache ?: synchronized(this) {
        manifestCache ?: readAsset(PROPRIETARY_CATALOG_MANIFEST_ASSET)
            .let(ProprietaryCatalogParser::decodeManifest)
            .also { manifestCache = it }
    }

    fun loadEntityIndex(): ProprietaryEntityIndex = indexCache ?: synchronized(this) {
        indexCache ?: readAsset(PROPRIETARY_CATALOG_ENTITY_INDEX_ASSET)
            .let(ProprietaryCatalogParser::decodeEntityIndex)
            .also { indexCache = it }
    }

    fun loadSection(path: String): ProprietarySectionShard = synchronized(shardCache) {
        shardCache[path] ?: readAsset(path)
            .let(ProprietaryCatalogParser::decodeSection)
            .also { shardCache[path] = it }
    }

    fun entity(entityId: String): ProprietaryCatalogEntity? =
        loadEntityIndex().entities.firstOrNull { it.id == entityId }

    fun search(
        query: String,
        systemId: String? = null,
        includeRealCases: Boolean = true,
        limit: Int = 400
    ): List<ProprietaryCatalogEntity> {
        val needle = query.normalizedCatalogText()
        return loadEntityIndex().entities.asSequence()
            .filter { systemId == null || it.systemId == systemId }
            .filter { includeRealCases || it.recordRole == "COMPONENT" }
            .filter { needle.isBlank() || it.nameOriginal.normalizedCatalogText().contains(needle) }
            .take(limit)
            .toList()
    }

    fun literalContext(entity: ProprietaryCatalogEntity, maxBlocks: Int = 360): List<ProprietarySourceBlock> {
        return selectLiteralContext(loadSection(entity.shardPath).blocks, entity, maxBlocks)
    }

    private fun readAsset(path: String): String =
        appContext.assets.open(path).bufferedReader().use { it.readText() }
}

/**
 * Keeps the direct entity block and links later literal explanations that mention it.
 * The source documents often declare a BOM first and explain those pieces under a
 * system heading afterwards.
 */
internal fun selectLiteralContext(
    blocks: List<ProprietarySourceBlock>,
    entity: ProprietaryCatalogEntity,
    maxBlocks: Int
): List<ProprietarySourceBlock> {
    if (maxBlocks <= 0) return emptyList()
    val sourceIndex = blocks.indexOfFirst { it.blockId == entity.sourceBlockId }
    if (sourceIndex < 0) return emptyList()
    val selected = linkedMapOf<String, ProprietarySourceBlock>()

    fun addDirectContext(index: Int) {
        val anchor = blocks[index]
        selected[anchor.blockId] = anchor
        val ownerId = anchor.entityId ?: anchor.parentEntityId ?: return
        for (cursor in (index + 1) until blocks.size) {
            val block = blocks[cursor]
            if (block.entityId != null && block.entityId != ownerId) break
            if (block.entityId == ownerId || block.parentEntityId == ownerId) {
                selected[block.blockId] = block
            } else if (selected.size > 1) {
                break
            }
        }
    }

    addDirectContext(sourceIndex)
    val needle = entity.nameOriginal.normalizedCatalogText()
    if (needle.length >= 4) {
        blocks.forEachIndexed { index, block ->
            val searchable = buildString {
                append(block.text)
                block.rows.orEmpty().flatten().forEach { append('\n').append(it) }
            }.normalizedCatalogText()
            if (!searchable.contains(needle)) return@forEachIndexed
            val parentIndex = block.parentEntityId?.let { parentId ->
                (index downTo 0).firstOrNull { blocks[it].entityId == parentId }
            }
            addDirectContext(parentIndex ?: index)
        }
    }
    return selected.values.sortedBy(ProprietarySourceBlock::order).take(maxBlocks)
}

internal fun String.normalizedCatalogText(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase()
    .trim()
