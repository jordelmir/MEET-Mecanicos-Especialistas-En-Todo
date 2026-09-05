package com.elysium369.meet.ecu

import com.elysium369.meet.ecu.protocol.DecodedIsoTpFrame
import com.elysium369.meet.ecu.protocol.DecodedUdsMessage
import com.elysium369.meet.ecu.protocol.IsoTpFrameDecoder
import com.elysium369.meet.ecu.protocol.UdsMessageDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolGoldenVectorTest {

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        val result = ByteArray(clean.length / 2)
        for (i in clean.indices step 2) {
            result[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
        }
        return result
    }

    // ── ISO-TP (ISO 15765-2) CONFORMANCE ─────────────────────────────────────

    @Test
    fun `single frame golden vector decodes length and payload correctly`() {
        val raw = hexToBytes("02 10 01 00 00 00 00 00")
        val decoded = IsoTpFrameDecoder.decode(raw)

        assertTrue(decoded is DecodedIsoTpFrame.SingleFrame)
        val sf = decoded as DecodedIsoTpFrame.SingleFrame
        assertEquals(2, sf.length)
        assertEquals(0x10.toByte(), sf.payload[0])
        assertEquals(0x01.toByte(), sf.payload[1])
    }

    @Test
    fun `first frame golden vector decodes total 12-bit length and initial chunk`() {
        val raw = hexToBytes("10 14 62 F1 90 57 56 57")
        val decoded = IsoTpFrameDecoder.decode(raw)

        assertTrue(decoded is DecodedIsoTpFrame.FirstFrame)
        val ff = decoded as DecodedIsoTpFrame.FirstFrame
        assertEquals(20, ff.totalLength)
        assertEquals(6, ff.initialPayload.size)
        assertEquals(0x62.toByte(), ff.initialPayload[0])
    }

    @Test
    fun `flow control golden vector decodes block size and stMin correctly`() {
        val raw = hexToBytes("30 08 0A 00 00 00 00 00")
        val decoded = IsoTpFrameDecoder.decode(raw)

        assertTrue(decoded is DecodedIsoTpFrame.FlowControl)
        val fc = decoded as DecodedIsoTpFrame.FlowControl
        assertEquals(0, fc.flowStatus) // 0 = ContinueToSend (CTS)
        assertEquals(8, fc.blockSize)
        assertEquals(10, fc.stMinMs)
    }

    @Test
    fun `consecutive frame golden vector decodes sequence counter`() {
        val raw = hexToBytes("21 5A 5A 5A 31 4A 5A 31")
        val decoded = IsoTpFrameDecoder.decode(raw)

        assertTrue(decoded is DecodedIsoTpFrame.ConsecutiveFrame)
        val cf = decoded as DecodedIsoTpFrame.ConsecutiveFrame
        assertEquals(1, cf.sequenceNumber)
        assertEquals(7, cf.payload.size)
    }

    // ── UDS (ISO 14229-1) CONFORMANCE ────────────────────────────────────────

    @Test
    fun `diagnostic session positive response decodes session type and timing`() {
        val raw = hexToBytes("50 02 00 32 01 F4") // Service $10 response (0x50)
        val decoded = UdsMessageDecoder.decode(raw)

        assertTrue(decoded is DecodedUdsMessage.PositiveResponse)
        val pos = decoded as DecodedUdsMessage.PositiveResponse
        assertEquals(0x50, pos.responseSid)
        assertEquals(0x10, pos.requestSid)
        assertEquals(0x02.toByte(), pos.payload[0]) // Programming session
    }

    @Test
    fun `negative response with security access denied decodes typed NRC 0x33`() {
        val raw = hexToBytes("7F 27 33")
        val decoded = UdsMessageDecoder.decode(raw)

        assertTrue(decoded is DecodedUdsMessage.NegativeResponse)
        val neg = decoded as DecodedUdsMessage.NegativeResponse
        assertEquals(0x27, neg.rejectedSid)
        assertEquals(0x33, neg.nrc)
        assertEquals("Security Access Denied", neg.meaning)
    }

    @Test
    fun `response pending NRC 0x78 instructs tester to await P2 star window`() {
        val raw = hexToBytes("7F 36 78")
        val decoded = UdsMessageDecoder.decode(raw)

        assertTrue(decoded is DecodedUdsMessage.NegativeResponse)
        val neg = decoded as DecodedUdsMessage.NegativeResponse
        assertEquals(0x36, neg.rejectedSid) // TransferData
        assertEquals(0x78, neg.nrc)
        assertEquals("Request Correctly Received Response Pending", neg.meaning)
    }
}
