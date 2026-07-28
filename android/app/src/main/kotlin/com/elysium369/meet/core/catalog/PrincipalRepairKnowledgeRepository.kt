package com.elysium369.meet.core.catalog

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PRINCIPAL_REPAIR_SOURCE_ASSET =
    "knowledge/proprietary_v2/base_de_datos_principal.json"
const val PRINCIPAL_REPAIR_SOURCE_SHA256 =
    "d45ee91823a1105bfe2a3436a4f67da0ed6217cd06916e9e17d10382b47b519f"
const val PRINCIPAL_REPAIR_SOURCE_BLOCK_COUNT = 76_934

@Serializable
internal data class PrincipalRepairExtraction(
    val document: PrincipalRepairDocument,
    val blocks: List<PrincipalRepairBlock>,
)

@Serializable
internal data class PrincipalRepairDocument(
    val sourceFileName: String,
    val sourceSha256: String,
)

@Serializable
internal data class PrincipalRepairBlock(
    val blockId: String,
    val kind: String,
    val order: Int,
    val sectionPath: List<String> = emptyList(),
    val styleId: String = "",
    val text: String,
    val textHash: String,
)

class PrincipalRepairKnowledgeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: LoadedPrincipalRepairKnowledge? = null

    fun evidenceFor(
        entity: ProprietaryCatalogEntity,
        maxBlocks: Int = 18,
    ): List<ProprietarySourceBlock> {
        require(maxBlocks in 1..40)
        val loaded = load()
        val identity = physicalComponentName(entity.nameOriginal).normalizedCatalogText()
        val tokens = identity.split(Regex("\\s+"))
            .filter { it.length >= 4 && it !in STOP_WORDS }
            .distinct()
        if (identity.length < 3 || tokens.isEmpty()) return emptyList()

        val exact = loaded.normalizedTexts.withIndex()
            .filter { (_, text) -> identity in text }
            .map { it.index }
            .take(8)
            .toList()
        val anchors = if (exact.isNotEmpty()) {
            exact
        } else {
            loaded.normalizedTexts.withIndex()
                .mapNotNull { (index, text) ->
                    val score = tokens.count(text::contains)
                    if (score >= minOf(2, tokens.size)) index to score else null
                }
                .sortedByDescending { it.second }
                .take(5)
                .map { it.first }
        }

        return anchors
            .flatMap { anchor -> (anchor - 2..anchor + 4).toList() }
            .filter { it in loaded.extraction.blocks.indices }
            .distinct()
            .sorted()
            .map { loaded.extraction.blocks[it] }
            .filter { it.text.isNotBlank() }
            .take(maxBlocks)
            .map { block ->
                ProprietarySourceBlock(
                    blockId = "principal-v2-${block.blockId}",
                    kind = block.kind,
                    order = block.order,
                    recordRole = "SOURCE_DETAIL_V2",
                    sectionPath = block.sectionPath,
                    styleId = block.styleId,
                    text = block.text,
                    textHash = block.textHash,
                    entityId = entity.id,
                    parentEntityId = entity.id,
                )
            }
    }

    private fun load(): LoadedPrincipalRepairKnowledge = cache ?: synchronized(this) {
        cache ?: appContext.assets.open(PRINCIPAL_REPAIR_SOURCE_ASSET)
            .bufferedReader()
            .use { json.decodeFromString<PrincipalRepairExtraction>(it.readText()) }
            .also { extraction ->
                require(extraction.document.sourceSha256 == PRINCIPAL_REPAIR_SOURCE_SHA256) {
                    "Unexpected principal repair source"
                }
                require(extraction.blocks.size == PRINCIPAL_REPAIR_SOURCE_BLOCK_COUNT) {
                    "Incomplete principal repair source"
                }
            }
            .let { extraction ->
                LoadedPrincipalRepairKnowledge(
                    extraction = extraction,
                    normalizedTexts = extraction.blocks.map { it.text.normalizedCatalogText() },
                )
            }
            .also { cache = it }
    }

    private data class LoadedPrincipalRepairKnowledge(
        val extraction: PrincipalRepairExtraction,
        val normalizedTexts: List<String>,
    )

    private companion object {
        val STOP_WORDS = setOf(
            "para", "como", "este", "esta", "del", "con", "sistema",
            "conjunto", "parte", "pieza", "principal",
        )
    }
}

