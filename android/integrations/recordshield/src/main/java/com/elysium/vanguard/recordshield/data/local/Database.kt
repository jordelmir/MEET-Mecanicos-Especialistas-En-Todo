package com.elysium.vanguard.recordshield.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * ============================================================================
 * Room Database — Local Evidence Metadata Storage
 * ============================================================================
 *
 * Why Room over raw SQLite: Room provides compile-time SQL verification,
 * coroutine/Flow integration, and automatic migration support. For an
 * evidence system, data integrity is paramount — Room's compile-time
 * checks prevent SQL injection and schema mismatch bugs.
 *
 * Why store metadata locally: Even when uploading chunks to Supabase,
 * we maintain a local copy of all metadata for:
 *   - Offline resilience (upload queue survives app restarts)
 *   - Gallery browsing without network
 *   - Upload progress tracking
 * ============================================================================
 */

// ============================================================================
// ENTITIES
// ============================================================================

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "recording_type")
    val recordingType: String, // "audio" | "video"
    val status: String, // "recording" | "completed" | "interrupted"
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,
    @ColumnInfo(name = "total_chunks")
    val totalChunks: Int = 0,
    @ColumnInfo(name = "total_size_bytes")
    val totalSizeBytes: Long = 0L,
    @ColumnInfo(name = "remote_id")
    val remoteId: String? = null // Supabase recording UUID, null until synced
)

@Entity(
    tableName = "evidence_chunks",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE // Why: Deleting a recording deletes all its chunks
        )
    ],
    indices = [
        Index(value = ["recording_id", "chunk_index"], unique = true)
    ]
)
data class ChunkEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "recording_id")
    val recordingId: String,
    @ColumnInfo(name = "chunk_index")
    val chunkIndex: Int,
    @ColumnInfo(name = "local_path")
    val localPath: String,
    @ColumnInfo(name = "storage_path")
    val storagePath: String? = null, // null until uploaded to Supabase
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Int,
    @ColumnInfo(name = "mime_type")
    val mimeType: String = "video/mp4",
    @ColumnInfo(name = "sha256_hash")
    val sha256Hash: String,
    @ColumnInfo(name = "upload_status")
    val uploadStatus: String = "PENDING", // PENDING | UPLOADING | UPLOADED | FAILED
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

// ============================================================================
// DAOs
// ============================================================================

@Dao
interface RecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: String): RecordingEntity?

    @Query("UPDATE recordings SET status = :status, ended_at = :endedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, endedAt: Long? = null)

    @Query("UPDATE recordings SET total_chunks = total_chunks + 1, total_size_bytes = total_size_bytes + :chunkSize WHERE id = :id")
    suspend fun incrementChunkCounters(id: String, chunkSize: Long)

    @Query("SELECT * FROM recordings ORDER BY started_at DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY started_at DESC")
    suspend fun getAll(): List<RecordingEntity>

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: ChunkEntity)

    @Query("SELECT * FROM evidence_chunks WHERE recording_id = :recordingId ORDER BY chunk_index ASC")
    suspend fun getChunksForRecording(recordingId: String): List<ChunkEntity>

    @Query("SELECT * FROM evidence_chunks WHERE upload_status = 'PENDING' OR upload_status = 'FAILED' ORDER BY created_at ASC")
    suspend fun getPendingChunks(): List<ChunkEntity>

    @Query("UPDATE evidence_chunks SET upload_status = :status, storage_path = :storagePath WHERE id = :id")
    suspend fun updateUploadStatus(id: String, status: String, storagePath: String? = null)

    @Query("SELECT COUNT(*) FROM evidence_chunks WHERE upload_status != 'UPLOADED'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM evidence_chunks WHERE recording_id = :recordingId AND upload_status = 'UPLOADED' ORDER BY chunk_index ASC")
    suspend fun getUploadedChunksForRecording(recordingId: String): List<ChunkEntity>
}

// ============================================================================
// DATABASE
// ============================================================================

@Database(
    entities = [RecordingEntity::class, ChunkEntity::class],
    version = 1,
    exportSchema = true // Why: Enables migration verification in tests
)
abstract class RecordShieldDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun chunkDao(): ChunkDao
}
