package com.elysium.nexus.fabric.infrared.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrSourceParserTest {

    @Test
    fun `FlipperIrParser successfully parses valid Flipper ir file`() {
        val flipperContent = """
            Filetype: IR signals file
            Version: 1
            #
            name: Power
            type: raw
            frequency: 38000
            duty_cycle: 0.330000
            data: 9000 4500 560 560 560 1690 560
            #
            name: VolUp
            type: raw
            frequency: 38000
            duty_cycle: 0.330000
            data: 9000 4500 560 1690 560 560 560
        """.trimIndent()

        val parser = FlipperIrParser()
        val result = parser.parse(flipperContent, "flipper-test-01")

        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertEquals(2, success.signals.size)
        assertEquals("Power", success.signals[0].name)
        assertEquals("VolUp", success.signals[1].name)
        assertEquals(38000, success.signals[0].carrierHz)
    }

    @Test
    fun `LicenseGate correctly evaluates source licenses`() {
        val flipperPolicy = LicenseGate.evaluate("flipper-irdb")
        assertEquals(LicenseGate.LicenseStatus.APPROVED, flipperPolicy.status)
        assertEquals("CC0-1.0", flipperPolicy.licenseSpdx)

        val globalCachePolicy = LicenseGate.evaluate("global-cache")
        assertEquals(LicenseGate.LicenseStatus.BLOCKED, globalCachePolicy.status)
    }
}
