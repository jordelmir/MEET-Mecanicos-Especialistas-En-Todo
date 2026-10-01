package com.elysium369.meet.core.mesh

import androidx.room.withTransaction
import java.security.MessageDigest

/** Every admission and receipt is transactional. Custody never promotes Safety or ride truth. */
class MeshRepository(private val db: MeshDatabase, val owner: String, private val limits: MeshLimits = MeshLimits(), private val clock: () -> Long = System::currentTimeMillis) {
    init { require(owner.isNotBlank() && owner.length <= 128) }
    private val dao = db.meshDao()
    val heldCount = dao.heldCount(owner)
    private suspend fun now(): Long = maxOf(clock(), dao.clock(owner)?.highWater ?: 0).also { dao.clock(MeshClockEntity(owner, it)) }
    suspend fun storeAuthenticated(envelope: MeshEnvelope): MeshIntake = db.withTransaction {
        val at = now()
        limits.rejection(envelope, at)?.let { return@withTransaction it }
        prune(at)
        if (dao.find(owner, envelope.messageId) != null || dao.seen(owner, envelope.replayId()) != 0) return@withTransaction MeshIntake.REPLAY
        if (dao.originCount(owner, envelope.originKeyId, at - 60_000) >= limits.perOriginPerMinute) return@withTransaction MeshIntake.FLOOD
        val wire = MeshWire.encode(envelope)
        // Reserve the complete attachment budget at admission, before receiving its first chunk.
        val cost = wire.size.toLong() + envelope.attachmentBytes
        if (dao.count(owner) >= limits.maxEnvelopes || dao.storedBytes(owner) + cost > limits.maxStoredBytes || dao.replayCount(owner) >= limits.maxReplayEntries) return@withTransaction MeshIntake.CAPACITY
        dao.insertReplay(MeshReplayEntity(owner, envelope.replayId(), at + limits.maxAgeMs + limits.futureSkewMs))
        dao.insertEnvelope(MeshEnvelopeEntity(owner, envelope.messageId, envelope.originKeyId, envelope.recipientKeyId, wire, digest(MeshWire.encode(envelope.copy(hops = 0))), cost, envelope.priority, envelope.hops, envelope.maxHops, at, minOf(envelope.expiresAt, at + limits.maxAgeMs)))
        dao.event(MeshCustodyEvent(owner = owner, messageId = envelope.messageId, originKeyId = envelope.originKeyId, peerKeyId = null, kind = "ACCEPTED", at = at))
        MeshIntake.STORED
    }
    suspend fun pending(): List<MeshEnvelope> = db.withTransaction {
        val at = now(); prune(at); dao.pending(owner, at).map { MeshWire.decode(it.wire) }
    }
    suspend fun observe(peer: MeshPeerAdvertisement) = db.withTransaction {
        val at = now(); dao.purgePeers(owner, at - 900_000)
        // Bounded rotating observations: addresses and location history are deliberately omitted.
        if (dao.peerCount(owner) < 64) dao.peer(MeshPeerObservation(owner, peer.rotatingId.take(64), peer.bearer.name, at))
    }
    suspend fun recordVerifiedCustody(receipt: MeshCustodyReceipt): Boolean = db.withTransaction {
        val e = dao.find(owner, receipt.messageId) ?: return@withTransaction false
        val at = now()
        if (e.expiresAt <= at || e.digest != receipt.digest || e.custodyState != "HELD" || dao.receiptCount(owner, e.messageId) >= 20 || dao.receiptSeen(owner, e.messageId, receipt.peerKeyId) > 0) return@withTransaction false
        dao.event(MeshCustodyEvent(owner = owner, messageId = e.messageId, originKeyId = e.originKeyId, peerKeyId = receipt.peerKeyId, kind = "REMOTE_CUSTODY", at = at))
        // Retain ciphertext until expiry. A relay receipt is not a recipient delivery receipt.
        true
    }
    suspend fun chunk(messageId: String, index: Int): ByteArray? = dao.chunk(owner, messageId, index)?.bytes
    suspend fun storeChunk(messageId: String, index: Int, bytes: ByteArray): Boolean = db.withTransaction {
        val row = dao.find(owner, messageId) ?: return@withTransaction false
        if (row.expiresAt <= now()) return@withTransaction false
        val e = MeshWire.decode(row.wire)
        if (e.attachmentBytes == 0 || index < 0) return@withTransaction false
        val chunks = (e.attachmentBytes + limits.chunkBytes - 1) / limits.chunkBytes
        if (index >= chunks) return@withTransaction false
        val expected = minOf(limits.chunkBytes, e.attachmentBytes - index * limits.chunkBytes)
        if (bytes.size != expected) return@withTransaction false
        val existing = dao.chunk(owner, messageId, index)
        if (existing != null) return@withTransaction existing.bytes.contentEquals(bytes)
        dao.chunk(MeshAttachmentChunk(owner, messageId, index, bytes.copyOf()))
        true
    }
    /** Verifies complete encrypted attachment digest without allocating a contiguous 10 MB array. */
    suspend fun attachmentComplete(messageId: String): Boolean = db.withTransaction {
        val row = dao.find(owner, messageId) ?: return@withTransaction false
        if (row.expiresAt <= now()) return@withTransaction false
        val e = MeshWire.decode(row.wire)
        if (e.attachmentBytes == 0) return@withTransaction true
        val md = MessageDigest.getInstance("SHA-256")
        val count = (e.attachmentBytes + limits.chunkBytes - 1) / limits.chunkBytes
        for (index in 0 until count) {
            val piece = dao.chunk(owner, messageId, index) ?: return@withTransaction false
            md.update(piece.bytes)
        }
        md.digest().joinToString("") { "%02x".format(it) } == e.attachmentDigest
    }
    suspend fun prune() = db.withTransaction { prune(now()) }
    private suspend fun prune(at: Long) {
        dao.purgeEnvelopes(owner, at); dao.purgeReplay(owner, at)
        dao.purgeEvents(owner, at - limits.maxAgeMs - limits.futureSkewMs)
        dao.purgePeers(owner, at - 900_000)
    }
    /** User deletion clears ciphertext, chunks (FK cascade), custody, discovery and replay namespace. */
    suspend fun clear() = db.withTransaction {
        dao.clearEnvelopes(owner); dao.clearReplay(owner); dao.clearEvents(owner); dao.clearPeers(owner); dao.clearClock(owner)
    }
}
