package com.elysium369.meet.core.telemetry

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Telemetry Binary Frame representing a timestamped sensor observation sample.
 */
data class TelemetryBinaryFrame(
    val timestampMs: Long,
    val pidHash: Int,
    val value: Float,
) {
    fun toBytes(): ByteArray {
        return ByteBuffer.allocate(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(timestampMs)
            .putInt(pidHash)
            .putFloat(value)
            .array()
    }

    companion object {
        fun fromBytes(bytes: ByteArray, offset: Int = 0): TelemetryBinaryFrame {
            val bb = ByteBuffer.wrap(bytes, offset, 16).order(ByteOrder.LITTLE_ENDIAN)
            return TelemetryBinaryFrame(
                timestampMs = bb.long,
                pidHash = bb.int,
                value = bb.float,
            )
        }
    }
}

/**
 * Compressed Binary Segment with SHA-256 Merkle Root for forensic integrity verification.
 */
data class TelemetryBinarySegment(
    val segmentId: String,
    val startTimeMonotonicMs: Long,
    val endTimeMonotonicMs: Long,
    val sampleCount: Int,
    val merkleRootHash: String,
    val compressedPayload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TelemetryBinarySegment
        if (segmentId != other.segmentId) return false
        if (merkleRootHash != other.merkleRootHash) return false
        if (!compressedPayload.contentEquals(other.compressedPayload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = segmentId.hashCode()
        result = 31 * result + merkleRootHash.hashCode()
        result = 31 * result + compressedPayload.contentHashCode()
        return result
    }
}

/**
 * CompressedTelemetryStorageEngineV2 — Compresses high-frequency vehicle telemetry
 * into forensic Merkle-verified binary segments without PII leakage.
 */
object CompressedTelemetryStorageEngineV2 {

    fun buildSegment(
        frames: List<TelemetryBinaryFrame>,
        segmentId: String = UUID.randomUUID().toString(),
    ): TelemetryBinarySegment {
        if (frames.isEmpty()) {
            return TelemetryBinarySegment(
                segmentId = segmentId,
                startTimeMonotonicMs = 0L,
                endTimeMonotonicMs = 0L,
                sampleCount = 0,
                merkleRootHash = computeSha256(ByteArray(0)),
                compressedPayload = compressBytes(ByteArray(0)),
            )
        }

        val rawBaos = ByteArrayOutputStream(frames.size * 16)
        val leafHashes = mutableListOf<String>()

        frames.forEach { frame ->
            val b = frame.toBytes()
            rawBaos.write(b)
            leafHashes.add(computeSha256(b))
        }

        val rawBytes = rawBaos.toByteArray()
        val merkleRoot = computeMerkleRoot(leafHashes)
        val compressed = compressBytes(rawBytes)

        return TelemetryBinarySegment(
            segmentId = segmentId,
            startTimeMonotonicMs = frames.first().timestampMs,
            endTimeMonotonicMs = frames.last().timestampMs,
            sampleCount = frames.size,
            merkleRootHash = merkleRoot,
            compressedPayload = compressed,
        )
    }

    fun decompressAndVerifySegment(segment: TelemetryBinarySegment): Pair<Boolean, List<TelemetryBinaryFrame>> {
        val rawBytes = decompressBytes(segment.compressedPayload)
        val frameCount = rawBytes.size / 16
        val frames = mutableListOf<TelemetryBinaryFrame>()
        val leafHashes = mutableListOf<String>()

        for (i in 0 until frameCount) {
            val frameBytes = rawBytes.copyOfRange(i * 16, (i + 1) * 16)
            frames.add(TelemetryBinaryFrame.fromBytes(frameBytes))
            leafHashes.add(computeSha256(frameBytes))
        }

        val computedMerkleRoot = if (leafHashes.isEmpty()) computeSha256(ByteArray(0)) else computeMerkleRoot(leafHashes)
        val isValid = computedMerkleRoot == segment.merkleRootHash && frames.size == segment.sampleCount

        return Pair(isValid, frames)
    }

    private fun compressBytes(input: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(input) }
        return baos.toByteArray()
    }

    private fun decompressBytes(input: ByteArray): ByteArray {
        val bais = ByteArrayInputStream(input)
        val baos = ByteArrayOutputStream()
        GZIPInputStream(bais).use { it.copyTo(baos) }
        return baos.toByteArray()
    }

    private fun computeSha256(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun computeMerkleRoot(leafHashes: List<String>): String {
        if (leafHashes.isEmpty()) return computeSha256(ByteArray(0))
        var currentLevel = leafHashes
        while (currentLevel.size > 1) {
            val nextLevel = mutableListOf<String>()
            for (i in currentLevel.indices step 2) {
                val left = currentLevel[i]
                val right = if (i + 1 < currentLevel.size) currentLevel[i + 1] else left
                val combined = computeSha256((left + right).toByteArray(Charsets.UTF_8))
                nextLevel.add(combined)
            }
            currentLevel = nextLevel
        }
        return currentLevel.first()
    }
}
