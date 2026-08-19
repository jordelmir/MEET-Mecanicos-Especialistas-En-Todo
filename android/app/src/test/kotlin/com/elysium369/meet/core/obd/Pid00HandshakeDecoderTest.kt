package com.elysium369.meet.core.obd

import com.elysium369.meet.core.obd.handshake.Pid00HandshakeDecoder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Pid00HandshakeDecoderTest {

    @Test
    fun `strictly accepts valid Mode 01 PID 00 responses`() {
        // Standard ISO/KWP/CAN 4100 bitmask
        assertTrue(Pid00HandshakeDecoder.isPositivePid00Response("41 00 BE 3F A8 13"))
        assertTrue(Pid00HandshakeDecoder.isPositivePid00Response("4100BE3FA813>"))
        assertTrue(Pid00HandshakeDecoder.isPositivePid00Response("86 F1 10 41 00 BE 3F A8 13 4A"))
        assertTrue(Pid00HandshakeDecoder.isPositivePid00Response("018\r\n0: 41 00 BE 3F A8 13\r\n1: 00 00 00 00 00 00"))
    }

    @Test
    fun `strictly rejects delayed stale responses from other Mode 01 PIDs`() {
        // Stale RPM response (41 0C) must NEVER satisfy 0100 handshake
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response("41 0C 1A F8"))
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response("410C1AF8>"))

        // Stale Coolant Temp (41 05) must NEVER satisfy 0100 handshake
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response("41 05 50"))

        // Stale Speed (41 0D)
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response("41 0D 00"))

        // Stale Fuel Level (41 2F)
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response("41 2F 80"))
    }

    @Test
    fun `strictly rejects hardware and adapter bus errors`() {
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response("NO DATA"))
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response("CAN ERROR"))
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response("UNABLE TO CONNECT"))
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response("BUS INIT: ERROR"))
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response("STOPPED"))
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response("?"))
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response(null))
        assertFalse(Pid00HandshakeDecoder.isPositivePid00Response(""))
    }
}
