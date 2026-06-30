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
}
