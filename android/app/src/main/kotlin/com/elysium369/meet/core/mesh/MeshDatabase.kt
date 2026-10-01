package com.elysium369.meet.core.mesh

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "mesh_envelopes", primaryKeys = ["owner", "messageId"], indices = [Index("owner", "expiresAt"), Index("owner", "originKeyId")])
data class MeshEnvelopeEntity(
    val owner: String, val messageId: String, val originKeyId: String, val recipientKeyId: String,
    val wire: ByteArray, val digest: String, val storedBytes: Long, val priority: Int,
    val hops: Int, val maxHops: Int, val receivedAt: Long, val expiresAt: Long,
    val custodyState: String = "HELD",
)
@Entity(tableName = "mesh_custody_events", indices = [Index("owner", "messageId"), Index("owner", "originKeyId", "at")])
data class MeshCustodyEvent(@PrimaryKey(autoGenerate = true) val id: Long = 0, val owner: String, val messageId: String, val originKeyId: String, val peerKeyId: String?, val kind: String, val at: Long)
@Entity(tableName = "mesh_peer_observations", primaryKeys = ["owner", "rotatingId", "bearer"], indices = [Index("lastSeen")])
data class MeshPeerObservation(val owner: String, val rotatingId: String, val bearer: String, val lastSeen: Long)
@Entity(tableName = "mesh_replay_index", primaryKeys = ["owner", "replayId"], indices = [Index("expiresAt")])
data class MeshReplayEntity(val owner: String, val replayId: String, val expiresAt: Long)
@Entity(tableName = "mesh_attachment_chunks", primaryKeys = ["owner", "messageId", "chunkIndex"], foreignKeys = [ForeignKey(entity = MeshEnvelopeEntity::class, parentColumns = ["owner", "messageId"], childColumns = ["owner", "messageId"], onDelete = ForeignKey.CASCADE)])
data class MeshAttachmentChunk(val owner: String, val messageId: String, val chunkIndex: Int, val bytes: ByteArray)
@Entity(tableName = "mesh_clock")
data class MeshClockEntity(@PrimaryKey val owner: String, val highWater: Long)

@Dao
interface MeshDao {
    @Query("SELECT * FROM mesh_envelopes WHERE owner=:owner AND messageId=:messageId") suspend fun find(owner: String, messageId: String): MeshEnvelopeEntity?
    @Query("SELECT * FROM mesh_envelopes WHERE owner=:owner AND expiresAt>:now AND hops<maxHops AND custodyState='HELD' ORDER BY priority DESC, receivedAt ASC LIMIT 100") suspend fun pending(owner: String, now: Long): List<MeshEnvelopeEntity>
    @Query("SELECT COUNT(*) FROM mesh_envelopes WHERE owner=:owner") suspend fun count(owner: String): Int
    @Query("SELECT COALESCE(SUM(storedBytes),0) FROM mesh_envelopes WHERE owner=:owner") suspend fun storedBytes(owner: String): Long
    @Query("SELECT COUNT(*) FROM mesh_envelopes WHERE owner=:owner AND custodyState='HELD'") fun heldCount(owner: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM mesh_replay_index WHERE owner=:owner AND replayId=:id") suspend fun seen(owner: String, id: String): Int
    @Query("SELECT COUNT(*) FROM mesh_replay_index WHERE owner=:owner") suspend fun replayCount(owner: String): Int
    @Query("SELECT COUNT(*) FROM mesh_custody_events WHERE owner=:owner AND originKeyId=:origin AND kind='ACCEPTED' AND at>:after") suspend fun originCount(owner: String, origin: String, after: Long): Int
    @Query("SELECT COUNT(*) FROM mesh_custody_events WHERE owner=:owner AND messageId=:messageId AND kind='REMOTE_CUSTODY'") suspend fun receiptCount(owner: String, messageId: String): Int
    @Query("SELECT COUNT(*) FROM mesh_custody_events WHERE owner=:owner AND messageId=:messageId AND peerKeyId=:peer AND kind='REMOTE_CUSTODY'") suspend fun receiptSeen(owner: String, messageId: String, peer: String): Int
    @Insert suspend fun insertEnvelope(e: MeshEnvelopeEntity)
    @Insert suspend fun insertReplay(e: MeshReplayEntity)
    @Insert suspend fun event(e: MeshCustodyEvent)
    @Query("UPDATE mesh_envelopes SET custodyState=:state WHERE owner=:owner AND messageId=:id") suspend fun state(owner: String, id: String, state: String)
    @Query("DELETE FROM mesh_envelopes WHERE owner=:owner AND expiresAt<=:now") suspend fun purgeEnvelopes(owner: String, now: Long)
    @Query("DELETE FROM mesh_replay_index WHERE owner=:owner AND expiresAt<=:now") suspend fun purgeReplay(owner: String, now: Long)
    @Query("DELETE FROM mesh_custody_events WHERE owner=:owner AND at<=:before") suspend fun purgeEvents(owner: String, before: Long)
    @Query("DELETE FROM mesh_peer_observations WHERE owner=:owner AND lastSeen<=:before") suspend fun purgePeers(owner: String, before: Long)
    @Query("SELECT COUNT(*) FROM mesh_peer_observations WHERE owner=:owner") suspend fun peerCount(owner: String): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun peer(e: MeshPeerObservation)
    @Query("SELECT * FROM mesh_clock WHERE owner=:owner") suspend fun clock(owner: String): MeshClockEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun clock(e: MeshClockEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun chunk(e: MeshAttachmentChunk): Long
    @Query("SELECT * FROM mesh_attachment_chunks WHERE owner=:owner AND messageId=:id AND chunkIndex=:index") suspend fun chunk(owner: String, id: String, index: Int): MeshAttachmentChunk?
    @Query("SELECT * FROM mesh_attachment_chunks WHERE owner=:owner AND messageId=:id ORDER BY chunkIndex") suspend fun chunks(owner: String, id: String): List<MeshAttachmentChunk>
    @Query("DELETE FROM mesh_envelopes WHERE owner=:owner") suspend fun clearEnvelopes(owner: String)
    @Query("DELETE FROM mesh_replay_index WHERE owner=:owner") suspend fun clearReplay(owner: String)
    @Query("DELETE FROM mesh_custody_events WHERE owner=:owner") suspend fun clearEvents(owner: String)
    @Query("DELETE FROM mesh_peer_observations WHERE owner=:owner") suspend fun clearPeers(owner: String)
    @Query("DELETE FROM mesh_clock WHERE owner=:owner") suspend fun clearClock(owner: String)
}

/** Separate authority from automotive/Safety Room versions. No destructive migration fallback. */
@Database(entities = [MeshEnvelopeEntity::class, MeshCustodyEvent::class, MeshPeerObservation::class, MeshReplayEntity::class, MeshAttachmentChunk::class, MeshClockEntity::class], version = 1, exportSchema = true)
abstract class MeshDatabase : RoomDatabase() {
    abstract fun meshDao(): MeshDao
    companion object {
        fun open(context: Context): MeshDatabase = Room.databaseBuilder(context.applicationContext, MeshDatabase::class.java, "vanguard_mesh.db").build()
    }
}
