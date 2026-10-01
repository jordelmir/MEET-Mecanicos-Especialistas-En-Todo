package com.elysium369.meet.core.mesh

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** File-backed Room tests exercise database reopen and hostile resource bounds.
 * Authenticated admission is a fixture precondition, not cryptographic or physical proof.
 */
@RunWith(AndroidJUnit4::class)
class MeshRepositoryRecoveryTest {
    private lateinit var context: Context
    private lateinit var db: MeshDatabase
    private lateinit var name: String
    private var now = 1_800_000_000_000L
    private fun repository(owner: String = "owner", limits: MeshLimits = MeshLimits()) = MeshRepository(db, owner, limits) { now }
    private fun message(id: String = UUID.randomUUID().toString(), origin: String = "origin", attachment: ByteArray? = null) = MeshEnvelope(id, origin, "recipient", now, now + 120_000, 5, 1, byteArrayOf(1, 2, 3), ByteArray(16) { 7 }, attachment?.size ?: 0, attachment?.let(::digest) ?: "")
    @Before fun open() { context = ApplicationProvider.getApplicationContext(); name = "mesh-test-${UUID.randomUUID()}.db"; db = Room.databaseBuilder(context, MeshDatabase::class.java, name).build() }
    @After fun close() { db.close(); context.deleteDatabase(name) }
    @Test fun reopenPreservesCustodyReplayAndChunks() = runBlocking {
        val attachment = ByteArray(65540) { (it % 251).toByte() }
        val e = message(attachment = attachment)
        val r = repository(); assertEquals(MeshIntake.STORED, r.storeAuthenticated(e))
        assertTrue(r.storeChunk(e.messageId, 0, attachment.copyOfRange(0, 65536)))
        assertFalse(r.attachmentComplete(e.messageId))
        db.close(); db = Room.databaseBuilder(context, MeshDatabase::class.java, name).build()
        val recovered = repository(); assertEquals(1, recovered.pending().size)
        assertEquals(MeshIntake.REPLAY, recovered.storeAuthenticated(e))
        assertTrue(recovered.storeChunk(e.messageId, 1, attachment.copyOfRange(65536, attachment.size)))
        assertTrue(recovered.attachmentComplete(e.messageId))
    }
    @Test fun replayTombstoneOutlivesCiphertextAndClockRollback() = runBlocking {
        val e = message(); val r = repository(); assertEquals(MeshIntake.STORED, r.storeAuthenticated(e))
        now += 130_000; r.prune(); assertTrue(r.pending().isEmpty())
        now -= 130_000
        assertEquals(MeshIntake.EXPIRED, r.storeAuthenticated(e))
        assertTrue(r.pending().isEmpty())
    }
    @Test fun concurrentFloodCannotExceedPerOriginQuota() = runBlocking {
        val r = repository(limits = MeshLimits(perOriginPerMinute = 4))
        val decisions = (0..19).map { async { r.storeAuthenticated(message("msg_$it")) } }.awaitAll()
        assertEquals(4, decisions.count { it == MeshIntake.STORED }); assertEquals(16, decisions.count { it == MeshIntake.FLOOD })
        assertEquals(4, r.pending().size)
    }
    @Test fun reservedAttachmentBudgetBlocksStorageExhaustionBeforeChunks() = runBlocking {
        val r = repository(limits = MeshLimits(maxStoredBytes = 500, maxAttachmentBytes = 1000))
        assertEquals(MeshIntake.CAPACITY, r.storeAuthenticated(message(attachment = ByteArray(501))))
        assertTrue(r.pending().isEmpty())
        assertFalse(r.storeChunk("unknown", 0, ByteArray(32)))
    }
    @Test fun chunksCannotChangeSizeOrOverwriteImmutablePieces() = runBlocking {
        val bytes = ByteArray(32) { 4 }; val e = message(attachment = bytes); val r = repository()
        assertEquals(MeshIntake.STORED, r.storeAuthenticated(e))
        assertFalse(r.storeChunk(e.messageId, Int.MAX_VALUE, bytes))
        assertFalse(r.storeChunk(e.messageId, 0, ByteArray(31)))
        assertTrue(r.storeChunk(e.messageId, 0, bytes)); assertTrue(r.storeChunk(e.messageId, 0, bytes))
        assertFalse(r.storeChunk(e.messageId, 0, ByteArray(32) { 5 }))
        assertTrue(r.attachmentComplete(e.messageId))
    }
    @Test fun deleteIsPrincipalScopedAndRemovesAllOwnedRows() = runBlocking {
        val first = repository("owner_a"); val second = repository("owner_b")
        assertEquals(MeshIntake.STORED, first.storeAuthenticated(message("shared_id")))
        assertEquals(MeshIntake.STORED, second.storeAuthenticated(message("shared_id")))
        first.clear(); assertTrue(first.pending().isEmpty()); assertEquals(1, second.pending().size)
        assertEquals(0, db.meshDao().replayCount("owner_a")); assertEquals(1, db.meshDao().replayCount("owner_b"))
    }
    @Test fun sybilOriginsCannotBypassGlobalCapacity() = runBlocking {
        val r = repository(limits = MeshLimits(maxEnvelopes = 3))
        repeat(3) { assertEquals(MeshIntake.STORED, r.storeAuthenticated(message("msg_$it", "origin_$it"))) }
        assertEquals(MeshIntake.CAPACITY, r.storeAuthenticated(message("extra", "new_sybil")))
        assertEquals(3, r.pending().size)
    }
}
