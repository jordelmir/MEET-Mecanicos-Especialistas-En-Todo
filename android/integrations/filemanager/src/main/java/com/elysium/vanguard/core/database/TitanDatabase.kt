package com.elysium.vanguard.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomWarnings
import androidx.room.RoomDatabase
import androidx.room.Database
import androidx.room.migration.Migration
import android.content.Context
import androidx.room.Room

/**
 * TITAN SEARCH ENTITY
 * FTS5-backed entity for lightning-fast, offline semantic search.
 */
@Entity(tableName = "file_search_index")
@Fts4
data class FileSearchEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Long = 0,
    val fileName: String,
    val filePath: String,
    val contentSnippet: String?,
    val fileType: String,
    val lastModified: Long
)

@Dao
interface FileSearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileSearchEntity)

    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query("""
        SELECT rowid, * FROM file_search_index
        WHERE file_search_index MATCH :query
    """)
    suspend fun searchFiles(query: String): List<FileSearchEntity>

    @Query("DELETE FROM file_search_index")
    suspend fun clearIndex()
}

/**
 * PHASE 1.1 — Trash entity.
 */
@Entity(tableName = "trash_items")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "original_name") val originalName: String,
    @ColumnInfo(name = "trash_uri") val trashUri: String,
    @ColumnInfo(name = "original_parent_uri") val originalParentUri: String,
    @ColumnInfo(name = "original_relative_path") val originalRelativePath: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long,
    @ColumnInfo(name = "mime_type") val mimeType: String? = null
)

@Dao
interface TrashDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: TrashEntity): Long

    @Query("SELECT * FROM trash_items ORDER BY deleted_at DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash_items ORDER BY deleted_at DESC")
    suspend fun listAll(): List<TrashEntity>

    @Query("SELECT * FROM trash_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TrashEntity?

    @Query("SELECT * FROM trash_items WHERE deleted_at < :thresholdMs")
    suspend fun listOlderThan(thresholdMs: Long): List<TrashEntity>

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM trash_items")
    suspend fun clear()

    @Query("SELECT SUM(size_bytes) FROM trash_items")
    suspend fun totalTrashedBytes(): Long?

    @Query("SELECT COUNT(*) FROM trash_items")
    suspend fun count(): Int
}

@Database(
    entities = [FileSearchEntity::class, TrashEntity::class],
    version = 2,
    exportSchema = true
)
abstract class TitanDatabase : RoomDatabase() {
    abstract fun fileSearchDao(): FileSearchDao
    abstract fun trashDao(): TrashDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trash_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        original_name TEXT NOT NULL,
                        trash_uri TEXT NOT NULL,
                        original_parent_uri TEXT NOT NULL,
                        original_relative_path TEXT NOT NULL,
                        size_bytes INTEGER NOT NULL,
                        deleted_at INTEGER NOT NULL,
                        mime_type TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_items_deleted_at ON trash_items(deleted_at)")
            }
        }
    }
}
