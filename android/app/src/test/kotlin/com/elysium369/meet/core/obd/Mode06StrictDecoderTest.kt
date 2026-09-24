package com.elysium369.meet.core.obd

import org.junit.Assert.*
import org.junit.Test

class Mode06StrictDecoderTest {

    private val parser = Mode06Parser()

    @Test
    fun `unknown UASID never becomes a physical value or PASS`() {
        // Frame with MID $01, TID $01, unknown UASID $FF, val 100, min 50, max 150
        val unknownUasidFrame = "460101FF006400320096"
        val results = parser.parse(unknownUasidFrame, ProtocolFamily.CAN_11BIT)

        assertEquals(1, results.size)
        val result = results.first()

        assertNull("Unknown UASID must not synthesize a double value", result.valueDouble)
        assertNull("Unknown UASID must not synthesize minLimit", result.minLimitDouble)
        assertNull("Unknown UASID must not synthesize maxLimit", result.maxLimitDouble)
        assertEquals(DecodeStatus.UNKNOWN_UASID, result.decodeStatus)
        assertEquals(Mode06Verdict.UNKNOWN, result.verdict)
        assertFalse("Unknown UASID must never pass", result.passed)
    }

    @Test
    fun `missing limits evaluates to UNKNOWN verdict and NO_LIMITS status, not PASS`() {
        // Frame with MID $01, TID $01, UASID $07 (mV, 0.001), val 450 (0.45V), min FFFF, max FFFF (no limits)
        val noLimitsFrame = "4601010701C2FFFFFFFF"
        val results = parser.parse(noLimitsFrame, ProtocolFamily.CAN_11BIT)

        assertEquals(1, results.size)
        val result = results.first()

        assertEquals(DecodeStatus.NO_LIMITS, result.decodeStatus)
        assertEquals(Mode06Verdict.UNKNOWN, result.verdict)
        assertFalse("Missing limits must not produce PASS", result.passed)
        assertNotNull(result.valueDouble)
        assertNull(result.minLimitDouble)
        assertNull(result.maxLimitDouble)
    }

    @Test
    fun `valid CAN frame with passed limits evaluates correctly`() {
        // MID $01, TID $07, UASID $07 (0.001 V), val 400 (0.400 V), min 100 (0.100 V), max 900 (0.900 V)
        val validFrame = "46010707019000640384"
        val results = parser.parse(validFrame, ProtocolFamily.CAN_11BIT)

        assertEquals(1, results.size)
        val result = results.first()

        assertEquals(DecodeStatus.DECODED, result.decodeStatus)
        assertEquals(Mode06Verdict.PASS, result.verdict)
        assertTrue(result.passed)
        assertEquals(0.400, result.valueDouble!!, 0.001)
        assertEquals(0.100, result.minLimitDouble!!, 0.001)
        assertEquals(0.900, result.maxLimitDouble!!, 0.001)
        assertEquals("V", result.unit)
    }

    @Test
    fun `value exceeding maximum limit evaluates to FAIL`() {
        // MID $A1 (Misfire cyl 1), TID $0B (EWMA count), UASID $01 (1 cnt), val 25, min 0, max 10
        val failFrame = "46A10B0100190000000A"
        val results = parser.parse(failFrame, ProtocolFamily.CAN_11BIT)

        assertEquals(1, results.size)
        val result = results.first()

        assertEquals(DecodeStatus.DECODED, result.decodeStatus)
        assertEquals(Mode06Verdict.FAIL, result.verdict)
        assertFalse(result.passed)
        assertEquals(DiagnosticSeverity.HIGH, result.severity)
    }
}
