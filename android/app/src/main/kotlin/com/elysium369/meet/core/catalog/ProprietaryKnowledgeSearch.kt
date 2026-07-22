package com.elysium369.meet.core.catalog

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json

const val PROPRIETARY_SEARCH_INDEX_ASSET = "knowledge/proprietary/search.sqlite.gzip"

data class ProprietaryKnowledgeHit(
    val rowId: Long,
    val blockId: String,
    val sectionId: String,
    val sectionTitle: String,
    val systemId: String,
    val sourceDocumentId: String,
    val sourceFileName: String,
    val sourceOrder: Int,
    val recordRole: String,
    val kind: String,
    val text: String,
    val textHash: String,
    val entityId: String?,
    val parentEntityId: String?,
    val rows: List<List<String>>?
) {
    val linkedEntityId: String?
        get() = entityId ?: parentEntityId

    fun asSourceBlock(): ProprietarySourceBlock = ProprietarySourceBlock(
        blockId = blockId,
        kind = kind,
        order = sourceOrder,
        recordRole = recordRole,
        sectionPath = listOf(sectionTitle),
        text = text,
        textHash = textHash,
        entityId = entityId,
        parentEntityId = parentEntityId,
        rows = rows
    )
}

data class ProprietaryKnowledgeIndexStatus(
    val corpusSha256: String,
    val blockCount: Int,
    val databaseBytes: Long
)

/**
 * Read-only offline search over the literal corpus. The SQLite file is derived data; the JSON
 * shards remain the authority and every hit carries the original block hash.
 */
class ProprietaryKnowledgeSearchRepository(
    context: Context,
    private val catalogRepository: ProprietaryPartsCatalogRepository = ProprietaryPartsCatalogRepository(context)
) {
    private val appContext = context.applicationContext
    private val rowsJson = Json { ignoreUnknownKeys = false }
    private val databaseLock = Any()
    @Volatile private var databaseCache: SQLiteDatabase? = null
    @Volatile private var statusCache: ProprietaryKnowledgeIndexStatus? = null

    fun status(): ProprietaryKnowledgeIndexStatus {
        database()
        return checkNotNull(statusCache)
    }

    fun search(
        query: String,
        systemId: String? = null,
        recordRoles: Set<String> = emptySet(),
        limit: Int = 200
    ): List<ProprietaryKnowledgeHit> {
        val ftsQuery = query.toSafeFtsQuery() ?: return emptyList()
        val clauses = mutableListOf("block_search MATCH ?")
        val arguments = mutableListOf(ftsQuery)
        addFilters(clauses, arguments, systemId, recordRoles)
        arguments += limit.coerceIn(1, 400).toString()
        val sql = """
            SELECT ${hitColumns("b")}
            FROM block_search
            JOIN blocks b ON b.row_id = block_search.docid
            WHERE ${clauses.joinToString(" AND ")}
            ORDER BY CASE b.record_role
                WHEN 'COMPONENT' THEN 0
                WHEN 'REAL_CASE' THEN 1
                WHEN 'TABLE' THEN 2
                WHEN 'SOURCE_DETAIL' THEN 3
                ELSE 4 END,
                LENGTH(b.text), b.source_order
            LIMIT ?
        """.trimIndent()
        return database().rawQuery(sql, arguments.toTypedArray()).use(::readHits)
    }

    fun browse(
        systemId: String? = null,
        recordRoles: Set<String> = emptySet(),
        limit: Int = 200
    ): List<ProprietaryKnowledgeHit> {
        val clauses = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        addFilters(clauses, arguments, systemId, recordRoles)
        arguments += limit.coerceIn(1, 400).toString()
        val where = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")?.let { "WHERE $it" }.orEmpty()
        val sql = """
            SELECT ${hitColumns("b")}
            FROM blocks b
            $where
            ORDER BY b.source_document_id, b.source_order
            LIMIT ?
        """.trimIndent()
        return database().rawQuery(sql, arguments.toTypedArray()).use(::readHits)
    }

    private fun database(): SQLiteDatabase = databaseCache ?: synchronized(databaseLock) {
        databaseCache ?: openValidatedDatabase().also { databaseCache = it }
    }

    private fun openValidatedDatabase(): SQLiteDatabase {
        val manifest = catalogRepository.loadManifest()
        val directory = File(appContext.noBackupFilesDir, "knowledge/proprietary").apply { mkdirs() }
        val target = File(directory, "search-${manifest.contentSha256.take(16)}.sqlite")
        if (!target.isFile) copyCompressedAssetAtomically(target)
        val database = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val metadata = database.rawQuery("SELECT key, value FROM metadata", emptyArray()).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
                }
            }
            require(metadata["schema_version"] == "1") { "Unsupported proprietary search schema" }
            require(metadata["corpus_sha256"] == manifest.contentSha256) { "Search index corpus mismatch" }
            require(metadata["block_count"] == manifest.statistics.blockCount.toString()) { "Search index is incomplete" }
            statusCache = ProprietaryKnowledgeIndexStatus(
                corpusSha256 = manifest.contentSha256,
                blockCount = manifest.statistics.blockCount,
                databaseBytes = target.length()
            )
            return database
        } catch (error: Throwable) {
            database.close()
            target.delete()
            throw error
        }
    }

    private fun copyCompressedAssetAtomically(target: File) {
        val temporary = File(target.parentFile, "${target.name}.tmp-${android.os.Process.myPid()}")
        temporary.delete()
        try {
            appContext.assets.open(PROPRIETARY_SEARCH_INDEX_ASSET).use { compressed ->
                GZIPInputStream(compressed).use { source ->
                    FileOutputStream(temporary).use { destination ->
                        source.copyTo(destination, DEFAULT_BUFFER_SIZE)
                        destination.fd.sync()
                    }
                }
            }
            check(temporary.renameTo(target)) { "Could not install proprietary search index" }
        } finally {
            temporary.delete()
        }
    }

    private fun addFilters(
        clauses: MutableList<String>,
        arguments: MutableList<String>,
        systemId: String?,
        recordRoles: Set<String>
    ) {
        if (systemId != null) {
            clauses += "b.system_id = ?"
            arguments += systemId
        }
        if (recordRoles.isNotEmpty()) {
            clauses += "b.record_role IN (${recordRoles.joinToString { "?" }})"
            arguments += recordRoles.sorted()
        }
    }

    private fun readHits(cursor: Cursor): List<ProprietaryKnowledgeHit> = buildList {
        while (cursor.moveToNext()) {
            val encodedRows = cursor.stringOrNull(14)
            add(
                ProprietaryKnowledgeHit(
                    rowId = cursor.getLong(0),
                    blockId = cursor.getString(1),
                    sectionId = cursor.getString(2),
                    sectionTitle = cursor.getString(3),
                    systemId = cursor.getString(4),
                    sourceDocumentId = cursor.getString(5),
                    sourceFileName = cursor.getString(6),
                    sourceOrder = cursor.getInt(7),
                    recordRole = cursor.getString(8),
                    kind = cursor.getString(9),
                    text = cursor.getString(10),
                    textHash = cursor.getString(11),
                    entityId = cursor.stringOrNull(12),
                    parentEntityId = cursor.stringOrNull(13),
                    rows = encodedRows?.let { rowsJson.decodeFromString<List<List<String>>>(it) }
                )
            )
        }
    }

    private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun hitColumns(alias: String): String = listOf(
        "row_id", "block_id", "section_id", "section_title", "system_id",
        "source_document_id", "source_file_name", "source_order", "record_role", "kind",
        "text", "text_hash", "entity_id", "parent_entity_id", "rows_json"
    ).joinToString { "$alias.$it" }
}

internal fun String.toSafeFtsQuery(): String? {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFC).lowercase()
    val tokens = Regex("[\\p{L}\\p{N}]+").findAll(normalized).map { it.value }.take(12).toList()
    return tokens.takeIf { it.isNotEmpty() }?.joinToString(" AND ") { "text:$it*" }
}
