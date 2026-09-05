package com.elysium369.meet.ecu.checksum

/**
 * Section 34 & 35: Deterministic Checksum Strategy for Bosch ME7.x.
 *
 * DOCTRINE:
 * No checksum strategy becomes VERIFIED without deterministic golden vectors.
 * Tests must detect endian mistakes, wrong regions, off-by-one, overflow, and algorithm variants.
 */

data class ChecksumBlock(
    val startOffset: Int,
    val endOffset: Int,
    val storedChecksumOffset: Int,
    val invertedChecksumOffset: Int,
)

data class ChecksumVerificationResult(
    val isValid: Boolean,
    val calculatedSum: Long,
    val storedSum: Long,
    val blockIndex: Int,
    val description: String,
)

object BoschMe7ChecksumStrategy {

    /**
     * Standard 16-bit word addition across designated calibration flash blocks.
     */
    fun calculateBlockSum16(binary: ByteArray, start: Int, end: Int): Int {
        var sum = 0
        var i = start
        val bound = end.coerceAtMost(binary.size - 1)
        while (i < bound) {
            val low = binary[i].toInt() and 0xFF
            val high = binary[i + 1].toInt() and 0xFF
            val word = (high shl 8) or low // Little-endian 16-bit word
            sum = (sum + word) and 0xFFFF
            i += 2
        }
        return sum
    }

    fun verifyBlock(binary: ByteArray, block: ChecksumBlock, blockIndex: Int = 0): ChecksumVerificationResult {
        if (block.storedChecksumOffset + 1 >= binary.size || block.invertedChecksumOffset + 1 >= binary.size) {
            return ChecksumVerificationResult(false, 0, 0, blockIndex, "Checksum offsets out of bounds")
        }

        val calculated = calculateBlockSum16(binary, block.startOffset, block.endOffset)

        val storedLow = binary[block.storedChecksumOffset].toInt() and 0xFF
        val storedHigh = binary[block.storedChecksumOffset + 1].toInt() and 0xFF
        val stored = (storedHigh shl 8) or storedLow

        val invLow = binary[block.invertedChecksumOffset].toInt() and 0xFF
        val invHigh = binary[block.invertedChecksumOffset + 1].toInt() and 0xFF
        val storedInverted = (invHigh shl 8) or invLow

        // Valid ME7 block requires stored + storedInverted == 0xFFFF
        val complementValid = ((stored + storedInverted) and 0xFFFF) == 0xFFFF
        val sumMatches = calculated == stored

        return ChecksumVerificationResult(
            isValid = sumMatches && complementValid,
            calculatedSum = calculated.toLong(),
            storedSum = stored.toLong(),
            blockIndex = blockIndex,
            description = if (sumMatches && complementValid) "Checksum VALID" else "Checksum MISMATCH or corrupt complement"
        )
    }

    fun recalculateAndPatch(binary: ByteArray, block: ChecksumBlock): ByteArray {
        val patched = binary.clone()
        val calculated = calculateBlockSum16(patched, block.startOffset, block.endOffset)
        val inverted = (calculated xor 0xFFFF) and 0xFFFF

        patched[block.storedChecksumOffset] = (calculated and 0xFF).toByte()
        patched[block.storedChecksumOffset + 1] = ((calculated ushr 8) and 0xFF).toByte()

        patched[block.invertedChecksumOffset] = (inverted and 0xFF).toByte()
        patched[block.invertedChecksumOffset + 1] = ((inverted ushr 8) and 0xFF).toByte()

        return patched
    }
}
