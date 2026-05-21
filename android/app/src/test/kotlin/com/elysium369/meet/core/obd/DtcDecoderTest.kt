package com.elysium369.meet.core.obd

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DtcDecoderTest — Unit test hook to certify DtcDecoder decoding logic.
 * Calls the integrated self-test harness.
 */
class DtcDecoderTest {

    @Test
    fun testDecoderSelfTests() {
        val result = DtcDecoder.runSelfTests()
        assertTrue("DtcDecoder self-tests must pass successfully", result)
    }
}
