package com.elysium369.meet.core.transport

import org.junit.Assert.*
import org.junit.Test

class TransportFabricV2Test {

    @Test
    fun rawCanFrameHexAndIntegrity() {
        val frame = RawCanFrame(
            arbitrationId = 0x7E8,
            isExtended = false,
            isFd = false,
            data = byteArrayOf(0x03, 0x41, 0x0C, 0x1A.toByte(), 0xF8.toByte(), 0x00, 0x00, 0x00)
        )

        assertEquals(0x7E8, frame.arbitrationId)
        assertFalse(frame.isExtended)
        assertFalse(frame.isFd)
        assertEquals("03 41 0C 1A F8 00 00 00", frame.toHex())
    }

    @Test
    fun rawCanFrameExtendedFormat() {
        val frame = RawCanFrame(
            arbitrationId = 0x18DAF110,
            isExtended = true,
            isFd = true,
            data = byteArrayOf(0x02, 0x01, 0x00)
        )

        assertEquals(0x18DAF110, frame.arbitrationId)
        assertTrue(frame.isExtended)
        assertTrue(frame.isFd)
        assertEquals("02 01 00", frame.toHex())
    }
}
