package com.elysium369.meet.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcDecoderTest {

    @Test
    fun decodeMode03StandardResponse() {
        val codes = DtcDecoder.decode("43 03 00 01 71 00 00", "03")

        assertEquals(listOf("P0300", "P0171"), codes)
    }

    @Test
    fun decodeMode03ResponseWithCountByte() {
        val codes = DtcDecoder.decode("43 02 03 00 01 71 00 00", "03")

        assertEquals(listOf("P0300", "P0171"), codes)
    }

    @Test
    fun decodeIgnoresInvalidPayloadInsteadOfInventingP0000() {
        val codes = DtcDecoder.decode("43 ZZ ZZ 00 00", "03")

        assertTrue(codes.isEmpty())
    }

    @Test
    fun parseStandardByEcuKeepsResponseAddress() {
        val records = DtcScanEngine.parseStandardByEcu(
            rawResponse = """
                7E8 43 03 00 00 00
                7E9 43 01 71 00 00
            """.trimIndent(),
            mode = "03",
            targetAddress = "7DF"
        )

        assertEquals(setOf("P0300", "P0171"), records.map { it.code }.toSet())
        assertEquals(setOf("7E8", "7E9"), records.mapNotNull { it.responseAddress }.toSet())
    }

    @Test
    fun parseUdsService19StatusPayload() {
        val records = DtcScanEngine.parseUdsService19ByEcu(
            rawResponse = "7E8 59 02 FF 03 00 00 0C",
            targetAddress = "7E0",
            moduleName = "ECM"
        )

        assertEquals(1, records.size)
        assertEquals("P0300", records.first().code)
        assertEquals(DtcBucket.ACTIVE, records.first().bucket)
        assertTrue(DtcStatusFlag.CONFIRMED in records.first().statusFlags)
    }
}
