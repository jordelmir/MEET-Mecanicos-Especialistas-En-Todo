package com.elysium369.meet.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinDecoderTest {

    @Test
    fun testValidVinCheckDigit() {
        // Valid VIN (Ford Escape 2012) with matching check digit '2' at index 8
        val validVin = "1FMCU0GZ2CUA00001"
        assertTrue(VinDecoder.validateCheckDigit(validVin))
        
        val info = VinDecoder.decode(validVin)
        assertTrue(info != null)
        assertTrue(info!!.isValid)
        assertTrue("⚠️" !in info.summary)
    }

    @Test
    fun testSuspiciousVinFailsCheckDigit() {
        // Tampered VIN (Check digit changed from '2' to '9')
        val tamperedVin = "1FMCU0GZ9CUA00001"
        assertFalse(VinDecoder.validateCheckDigit(tamperedVin))
        
        val info = VinDecoder.decode(tamperedVin)
        assertTrue(info != null)
        assertFalse(info!!.isValid)
        assertTrue("⚠️ VIN SOSPECHOSO (Check Digit Inválido)" in info.summary)
    }

    @Test
    fun testManufacturerDecoding() {
        val info = VinDecoder.decode("1FMCU0GZ2CUA00001")
        assertEquals("Ford", info?.manufacturer)
        
        val toyotaInfo = VinDecoder.decode("JT2xxxxxxxxxxxxxx")
        assertEquals("Toyota", toyotaInfo?.manufacturer)
    }

    @Test
    fun testCanMultiFrameParserMode09MultiLine() {
        // Multi-line SAE J1979 Mode 09 PID 02 response for Hyundai Accent (KMHCG51CP5U123456)
        val rawResponse = """
            49 02 01 4B 4D 48 43
            49 02 02 47 35 31 43
            49 02 03 50 35 55 31
            49 02 04 32 33 34 35
            49 02 05 36 00 00 00
        """.trimIndent()

        val decoded = CanMultiFrameParser.decodeVin(rawResponse)
        assertEquals("KMHCG51CP5U123456", decoded)
    }

    @Test
    fun testCanMultiFrameParserElmIndexedStream() {
        // ELM327 indexed response: 0: 49 02 01 ..., 1: ..., 2: ...
        val elmResponse = """
            0: 49 02 01 4B 4D 48
            1: 43 47 35 31 43 50 35
            2: 55 31 32 33 34 35 36
        """.trimIndent()

        val decoded = CanMultiFrameParser.decodeVin(elmResponse)
        assertEquals("KMHCG51CP5U123456", decoded)
    }

    @Test
    fun testCanMultiFrameParserUdsF190() {
        // UDS 0x22 0xF190 response: 62 F1 90 + 17 ASCII hex bytes
        val udsResponse = "62 F1 90 4B 4D 48 43 47 35 31 43 50 35 55 31 32 33 34 35 36"
        val decoded = CanMultiFrameParser.decodeVin(udsResponse)
        assertEquals("KMHCG51CP5U123456", decoded)
    }
}
