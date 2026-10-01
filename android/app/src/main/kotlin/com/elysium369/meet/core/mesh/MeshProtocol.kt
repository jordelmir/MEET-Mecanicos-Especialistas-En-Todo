package com.elysium369.meet.core.mesh

import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Radio addresses are hints, never authenticated device identity. No stable principal in beacons. */
data class MeshPeerAdvertisement(val rotatingId: String, val bearer: MeshBearer, val address: String, val seenAt: Long)
enum class MeshBearer(val maxFrameBytes: Int) { BLE(512), WIFI_AWARE(70_000), WIFI_DIRECT(70_000), LAN(70_000) }
interface MeshDiscoveryTransport {
    val discoveredPeers: Flow<MeshPeerAdvertisement>
    suspend fun startAdvertising(rotatingId: UUID)
    suspend fun startScanning()
    suspend fun stop()
}
interface MeshLink {
    val peerId: String
    val bearer: MeshBearer
    val inbound: Flow<ByteArray>
    suspend fun send(frame: ByteArray)
    suspend fun close()
}
interface MeshLinkFactory { suspend fun connect(peer: MeshPeerAdvertisement): MeshLink }

/** Fields through attachmentDigest are authenticated immutable metadata; hops is mutable routing. */
data class MeshEnvelope(
    val messageId: String,
    val originKeyId: String,
    val recipientKeyId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val maxHops: Int,
    val priority: Int,
    val ciphertext: ByteArray,
    val authentication: ByteArray,
    val attachmentBytes: Int = 0,
    val attachmentDigest: String = "",
    val hops: Int = 0,
) {
    fun associatedData(): ByteArray = MeshWire.metadata(this)
    fun replayId(): String = digest((originKeyId + ":" + messageId).toByteArray(Charsets.UTF_8))
}

/** A provider must authenticate relay metadata, key validity/revocation and recipient ciphertext.
 * No implementation is installed by default: transport establishment is not E2EE verification.
 */
interface MeshCryptoSuite {
    val independentlyReviewed: Boolean
    suspend fun authenticateLink(link: MeshLink): String
    suspend fun encrypt(recipientKeyId: String, plaintext: ByteArray, associatedData: ByteArray): MeshCiphertext
    suspend fun decrypt(envelope: MeshEnvelope): ByteArray
    suspend fun authenticate(envelope: MeshEnvelope): Boolean
    suspend fun verifyReceipt(receipt: MeshCustodyReceipt): Boolean
}
data class MeshCiphertext(val ciphertext: ByteArray, val authentication: ByteArray)
data class MeshCustodyReceipt(val messageId: String, val peerKeyId: String, val digest: String, val authentication: ByteArray)
class UnavailableMeshCrypto : MeshCryptoSuite {
    override val independentlyReviewed = false
    override suspend fun authenticateLink(link: MeshLink): String = unavailable()
    private fun unavailable(): Nothing = error("MESH_CRYPTO_PROVIDER_NOT_VERIFIED")
    override suspend fun encrypt(recipientKeyId: String, plaintext: ByteArray, associatedData: ByteArray): MeshCiphertext = unavailable()
    override suspend fun decrypt(envelope: MeshEnvelope): ByteArray = unavailable()
    override suspend fun authenticate(envelope: MeshEnvelope): Boolean = unavailable()
    override suspend fun verifyReceipt(receipt: MeshCustodyReceipt): Boolean = unavailable()
}

/** Resource policy is applied before allocation, crypto or persistent custody. */
data class MeshLimits(
    val maxEnvelopes: Int = 100,
    val maxStoredBytes: Long = 32L * 1024 * 1024,
    val maxCiphertextBytes: Int = 64 * 1024,
    val maxAttachmentBytes: Int = 10 * 1024 * 1024,
    val chunkBytes: Int = 64 * 1024,
    val maxAgeMs: Long = 24L * 60 * 60 * 1000,
    val futureSkewMs: Long = 5L * 60 * 1000,
    val perOriginPerMinute: Int = 20,
    val maxReplayEntries: Int = 10_000,
) {
    init {
        require(maxEnvelopes in 1..1000 && maxStoredBytes in 1..256L * 1024 * 1024)
        require(maxCiphertextBytes in 1..65536 && maxAttachmentBytes in 0..10 * 1024 * 1024)
        require(chunkBytes == 65536 && perOriginPerMinute in 1..100 && maxReplayEntries in 1..10000)
        require(maxAgeMs in 1..86_400_000 && futureSkewMs in 0..300_000)
    }
    fun rejection(e: MeshEnvelope, now: Long): MeshIntake? = when {
        !token(e.messageId) || !token(e.originKeyId) || !token(e.recipientKeyId) -> MeshIntake.MALFORMED
        e.createdAt < 0 || e.expiresAt <= e.createdAt || e.expiresAt - e.createdAt > maxAgeMs -> MeshIntake.MALFORMED
        e.createdAt > now + futureSkewMs || e.expiresAt <= now -> MeshIntake.EXPIRED
        e.maxHops !in 1..20 || e.hops !in 0 until e.maxHops || e.priority !in 0..2 -> MeshIntake.MALFORMED
        e.ciphertext.isEmpty() || e.ciphertext.size > maxCiphertextBytes || e.authentication.size !in 16..1024 -> MeshIntake.MALFORMED
        e.attachmentBytes !in 0..maxAttachmentBytes -> MeshIntake.MALFORMED
        e.attachmentBytes > 0 && !e.attachmentDigest.matches(Regex("[0-9a-f]{64}")) -> MeshIntake.MALFORMED
        e.attachmentBytes == 0 && e.attachmentDigest.isNotEmpty() -> MeshIntake.MALFORMED
        else -> null
    }
    private fun token(s: String) = s.matches(Regex("[A-Za-z0-9_-]{1,128}"))
}
enum class MeshIntake { STORED, REPLAY, EXPIRED, MALFORMED, FLOOD, CAPACITY, UNAUTHENTICATED, CRYPTO_UNAVAILABLE, DISABLED }

/** Strict versioned binary framing. This is a Mesh-only wire format, not a certified report hash. */
object MeshWire {
    const val MAX_FRAME_BYTES = 70_000
    private const val MAGIC = 0x45564d31
    fun metadata(e: MeshEnvelope): ByteArray = output { out ->
        out.writeInt(MAGIC); out.writeUTF(e.messageId); out.writeUTF(e.originKeyId); out.writeUTF(e.recipientKeyId)
        out.writeLong(e.createdAt); out.writeLong(e.expiresAt); out.writeInt(e.maxHops); out.writeInt(e.priority)
        out.writeInt(e.attachmentBytes); out.writeUTF(e.attachmentDigest)
    }
    fun encode(e: MeshEnvelope): ByteArray = output { out ->
        out.write(metadata(e)); out.writeInt(e.hops)
        out.writeInt(e.ciphertext.size); out.write(e.ciphertext)
        out.writeInt(e.authentication.size); out.write(e.authentication)
    }.also { require(it.size <= MAX_FRAME_BYTES) }
    fun decode(frame: ByteArray): MeshEnvelope {
        require(frame.size in 1..MAX_FRAME_BYTES)
        return DataInputStream(ByteArrayInputStream(frame)).use { input ->
            require(input.readInt() == MAGIC)
            val id = input.readUTF().also { require(it.length <= 128) }
            val origin = input.readUTF().also { require(it.length <= 128) }
            val recipient = input.readUTF().also { require(it.length <= 128) }
            val created = input.readLong(); val expiry = input.readLong(); val maxHops = input.readInt(); val priority = input.readInt()
            val attachmentBytes = input.readInt(); val digest = input.readUTF().also { require(it.length <= 64) }
            val hops = input.readInt()
            val cipher = input.boundedBytes(65536); val authentication = input.boundedBytes(1024)
            require(input.available() == 0)
            MeshEnvelope(id, origin, recipient, created, expiry, maxHops, priority, cipher, authentication, attachmentBytes, digest, hops)
        }
    }
    private fun DataInputStream.boundedBytes(max: Int): ByteArray {
        val n = readInt(); require(n in 1..max && n <= available())
        return ByteArray(n).also(::readFully)
    }
    private fun output(block: (DataOutputStream) -> Unit): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use(block); bytes.toByteArray()
    }
}
internal fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

sealed interface MeshPacket {
    data class Envelope(val envelope: MeshEnvelope) : MeshPacket
    data class Chunk(val messageId: String, val index: Int, val bytes: ByteArray) : MeshPacket
    data class Receipt(val receipt: MeshCustodyReceipt) : MeshPacket
}
/** Packet multiplexing reserves a version and validates lengths before allocation. */
object MeshPacketWire {
    private const val MAGIC = 0x45565031
    fun encode(packet: MeshPacket): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            when (packet) {
                is MeshPacket.Envelope -> { out.writeByte(1); val body = MeshWire.encode(packet.envelope); out.writeInt(body.size); out.write(body) }
                is MeshPacket.Chunk -> { require(packet.messageId.matches(Regex("[A-Za-z0-9_-]{1,128}")) && packet.index in 0..159 && packet.bytes.size in 1..65536); out.writeByte(2); out.writeUTF(packet.messageId); out.writeInt(packet.index); out.writeInt(packet.bytes.size); out.write(packet.bytes) }
                is MeshPacket.Receipt -> { val r = packet.receipt; require(r.messageId.length in 1..128 && r.peerKeyId.length in 1..128 && r.digest.matches(Regex("[0-9a-f]{64}")) && r.authentication.size in 16..1024); out.writeByte(3); out.writeUTF(r.messageId); out.writeUTF(r.peerKeyId); out.writeUTF(r.digest); out.writeInt(r.authentication.size); out.write(r.authentication) }
            }
        }
        bytes.toByteArray().also { require(it.size <= MeshWire.MAX_FRAME_BYTES) }
    }
    fun decode(bytes: ByteArray): MeshPacket {
        require(bytes.size in 1..MeshWire.MAX_FRAME_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC)
            val result = when (input.readUnsignedByte()) {
                1 -> MeshPacket.Envelope(MeshWire.decode(input.readBounded(MeshWire.MAX_FRAME_BYTES)))
                2 -> { val id = input.readUTF().also { require(it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) }; val index = input.readInt().also { require(it in 0..159) }; MeshPacket.Chunk(id, index, input.readBounded(65536)) }
                3 -> {
                    val id = input.readUTF().also { require(it.length in 1..128) }; val peer = input.readUTF().also { require(it.length in 1..128) }
                    val hash = input.readUTF().also { require(it.matches(Regex("[0-9a-f]{64}"))) }
                    MeshPacket.Receipt(MeshCustodyReceipt(id, peer, hash, input.readBounded(1024)))
                }
                else -> error("MESH_UNKNOWN_PACKET")
            }
            require(input.available() == 0); result
        }
    }
    private fun DataInputStream.readBounded(max: Int): ByteArray { val n = readInt(); require(n in 1..max && n <= available()); return ByteArray(n).also(::readFully) }
}
