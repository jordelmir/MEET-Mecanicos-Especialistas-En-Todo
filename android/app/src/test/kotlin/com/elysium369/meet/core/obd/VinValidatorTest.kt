package com.elysium369.meet.core.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VinValidatorTest {
    @Test
    fun `normalizes a canonical physical VIN`() {
        assertEquals("1HGCM82633A004352", VinValidator.normalize(" 1hgcm82633a004352 "))
    }

    @Test
    fun `rejects placeholders and non canonical lengths`() {
        assertNull(VinValidator.normalize("N/A"))
        assertNull(VinValidator.normalize("VIN123"))
        assertNull(VinValidator.normalize("1HGCM82633A00435"))
    }

    @Test
    fun `rejects forbidden VIN letters`() {
        assertNull(VinValidator.normalize("1HGCM82633I004352"))
        assertNull(VinValidator.normalize("1HGCM82633O004352"))
        assertNull(VinValidator.normalize("1HGCM82633Q004352"))
    }
}
