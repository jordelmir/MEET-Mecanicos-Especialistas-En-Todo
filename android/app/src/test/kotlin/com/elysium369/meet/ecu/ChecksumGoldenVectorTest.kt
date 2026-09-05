package com.elysium369.meet.ecu

import com.elysium369.meet.ecu.checksum.BoschMe7ChecksumStrategy
import com.elysium369.meet.ecu.checksum.ChecksumBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksumGoldenVectorTest {

    private val sampleBlock = ChecksumBlock(
        startOffset = 0x0000,
        endOffset = 0x0100, // 256 bytes block
        storedChecksumOffset = 0x0100,
        invertedChecksumOffset = 0x0102,
    )

    @Test
    fun `known good Bosch ME7 block matches calculated sum and complement`() {
        val binary = ByteArray(512) { 0x01 } // 256 bytes of 0x0101 words = 128 words of 0x0101 = 0x8080 sum

        // Patch initial valid checksum
        val validBinary = BoschMe7ChecksumStrategy.recalculateAndPatch(binary, sampleBlock)

        val result = BoschMe7ChecksumStrategy.verifyBlock(validBinary, sampleBlock)
        assertTrue("Checksum must be valid on patched binary", result.isValid)
        assertEquals(result.calculatedSum, result.storedSum)
    }

    @Test
    fun `single byte modification invalidates checksum without patch`() {
        val binary = ByteArray(512) { 0x01 }
        val validBinary = BoschMe7ChecksumStrategy.recalculateAndPatch(binary, sampleBlock)

        // Modify 1 byte in calibration region
        validBinary[0x0050] = 0x99.toByte()

        val result = BoschMe7ChecksumStrategy.verifyBlock(validBinary, sampleBlock)
        assertFalse("Checksum must fail after byte modification", result.isValid)

        // Recalculating restores validity
        val repatched = BoschMe7ChecksumStrategy.recalculateAndPatch(validBinary, sampleBlock)
        val repatchedResult = BoschMe7ChecksumStrategy.verifyBlock(repatched, sampleBlock)
        assertTrue("Recalculated binary must be valid", repatchedResult.isValid)
    }
}
