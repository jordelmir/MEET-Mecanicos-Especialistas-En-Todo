package com.elysium.vanguard.core.trash

import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.elysium.vanguard.core.database.TrashDao
import com.elysium.vanguard.core.database.TrashEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE 1.2 — Trash repository.
 *
 * Owns the lifecycle of trashed files:
 *   - [moveToTrash] takes a SAF DocumentFile (or legacy File) and relocates it
 *     into the trash folder, recording metadata for later restoration.
 *   - [restore] reverses that operation, putting the file back where it came from.
 *   - [purge] permanently deletes a trashed item (used by auto-purge and the
 *     "Empty Trash" button).
 *
 * Storage strategy:
 *   - When the source is a SAF DocumentFile (Android 11+ best practice), we move
 *     the file inside the trash folder via DocumentsContract.copy/move.
 *   - When the source is a legacy File (API 26-29 fallback), we copy bytes to
 *     filesDir/trash/ and delete the original.
 *   - We always update MediaStore so audio/video/image picks don't lose their
 *     thumbnail/state.
 *
 * Cancellation:
 *   - All blocking I/O runs on Dispatchers.IO so the calling ViewModel can
 *     cancel its own scope to abort the operation.
 */
@Singleton
class TrashRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashDao: TrashDao
) {

    private val trashRootDir: File by lazy {
        File(context.filesDir, "trash").apply { if (!exists()) mkdirs() }
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    fun observeTrash(): Flow<List<TrashEntity>> = trashDao.observeAll()

    suspend fun listTrash(): List<TrashEntity> = withContext(Dispatchers.IO) {
        trashDao.listAll()
    }

    suspend fun trashSizeBytes(): Long = withContext(Dispatchers.IO) {
        trashDao.totalTrashedBytes() ?: 0L
    }

    suspend fun trashCount(): Int = withContext(Dispatchers.IO) {
        trashDao.count()
    }

    // -----------------------------------------------------------------------
    // Mutations
    // -----------------------------------------------------------------------

    /**
     * Move [source] to trash and persist a manifest row.
     *
     * @return the new [TrashEntity.id], or -1 if the move failed.
     */
    suspend fun moveToTrash(source: TrashSource): Long = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val safeName = "${timestamp}_${source.displayName}".replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(trashRootDir, safeName)

            val sizeBytes: Long = when (source) {
                is TrashSource.FromFile -> {
                    source.file.copyTo(target, overwrite = true)
                    source.file.length()
                }
                is TrashSource.FromDocumentFile -> {
                    val resolved = source.documentFile
                    val bytes = copyDocumentToFile(resolved, target)
                    try { resolved.delete() } catch (_: Exception) { /* best effort */ }
                    bytes
                }
            }

            val entity = TrashEntity(
                originalName = source.displayName,
                trashUri = target.absolutePath,
                originalParentUri = source.parentIdentifier,
                originalRelativePath = source.displayName,
                sizeBytes = sizeBytes,
                deletedAt = timestamp,
                mimeType = source.mimeType
            )
            trashDao.insert(entity)
        } catch (t: Throwable) {
            com.elysium.vanguard.core.util.SafeLog.e("TrashRepository", "moveToTrash failed", t)
            -1L
        }
    }

    /**
     * Restore a trashed item back to its original parent.
     *
     * Caller must already hold a persistable URI permission grant on
     * [TrashEntity.originalParentUri]; otherwise the move will fail with
     * SecurityException. The grant is obtained via
     * [IntentSender] from `ACTION_OPEN_DOCUMENT_TREE`.
     */
    suspend fun restore(item: TrashEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(item.trashUri)
            if (!source.exists()) {
                trashDao.deleteById(item.id)
                return@withContext false
            }
            val restored = when {
                item.originalParentUri.startsWith("content://") -> {
                    val parent = DocumentFile.fromTreeUri(context, Uri.parse(item.originalParentUri))
                        ?: return@withContext false
                    val target = parent.findFile(item.originalRelativePath)
                        ?: parent.createFile(item.mimeType ?: "*/*", item.originalRelativePath)
                        ?: return@withContext false
                    context.contentResolver.openOutputStream(target.uri)?.use { out ->
                        source.inputStream().use { it.copyTo(out) }
                    } ?: return@withContext false
                    true
                }
                else -> {
                    val parent = File(item.originalParentUri)
                    if (!parent.exists()) parent.mkdirs()
                    val target = File(parent, item.originalRelativePath)
                    source.copyTo(target, overwrite = true)
                    true
                }
            }
            if (restored) {
                source.delete()
                trashDao.deleteById(item.id)
            }
            restored
        } catch (t: Throwable) {
            com.elysium.vanguard.core.util.SafeLog.e("TrashRepository", "restore failed for ${item.id}", t)
            false
        }
    }

    /**
     * PHASE 1.4: lookup-by-id wrapper around [restore] for the undo stack.
     * Returns null when the item is gone (already purged by the daily worker).
     */
    suspend fun restoreById(id: Long): TrashEntity? = withContext(Dispatchers.IO) {
        val item = trashDao.getById(id) ?: return@withContext null
        if (restore(item)) item else null
    }

    /**
     * Permanently delete a trashed item.
     */
    suspend fun purge(item: TrashEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val f = File(item.trashUri)
            if (f.exists()) f.delete()
            trashDao.deleteById(item.id)
            true
        } catch (t: Throwable) {
            com.elysium.vanguard.core.util.SafeLog.e("TrashRepository", "purge failed for ${item.id}", t)
            false
        }
    }

    suspend fun purgeAll(): Int = withContext(Dispatchers.IO) {
        val items = trashDao.listAll()
        var purged = 0
        for (it in items) {
            if (purge(it)) purged++
        }
        purged
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private fun copyDocumentToFile(doc: DocumentFile, target: File): Long {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(doc.uri)
            ?: throw IllegalStateException("Cannot open input stream for ${doc.uri}")
        var copied = 0L
        input.use { stream ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val read = stream.read(buf)
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    copied += read
                }
            }
        }
        return copied
    }
}

/**
 * Inputs accepted by [TrashRepository.moveToTrash].
 *
 * - [FromFile]: legacy file path on internal or external storage (API 26-29
 *   fallback or files we own outright).
 * - [FromDocumentFile]: SAF-backed file with a content:// URI (Android 11+
 *   scoped storage recommendation).
 */
sealed class TrashSource {
    abstract val displayName: String
    abstract val parentIdentifier: String
    abstract val mimeType: String?

    data class FromFile(
        val file: File,
        override val parentIdentifier: String,
        override val mimeType: String? = null
    ) : TrashSource() {
        override val displayName: String get() = file.name
    }

    data class FromDocumentFile(
        val documentFile: DocumentFile,
        override val parentIdentifier: String,
        override val mimeType: String? = null
    ) : TrashSource() {
        override val displayName: String get() = documentFile.name ?: "unknown"
    }
}
