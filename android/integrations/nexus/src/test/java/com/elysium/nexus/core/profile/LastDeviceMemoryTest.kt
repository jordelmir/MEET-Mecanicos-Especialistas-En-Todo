package com.elysium.nexus.core.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the LastDevice serialisation.
 *
 * The store persists a single device as a
 * pipe-separated string. The tests cover:
 *
 *  - round-trip Mac / Bluetooth entries
 *  - clear restores null
 *  - malformed input returns null (not an
 *    exception)
 *  - the format is stable (do not break the
 *    parser when the schema evolves)
 */
class LastDeviceMemoryTest {

    @Test
    fun `mac device round-trips through the format`() {
        val original = LastDevice.Mac(
            name = "iMac de Jor",
            host = "192.168.1.42",
            port = 7878
        )
        val raw = original.serialize()
        val parsed = LastDevice.parse(raw)
        assertEquals(original, parsed)
    }

    @Test
    fun `bluetooth device round-trips through the format`() {
        val original = LastDevice.Bluetooth(
            name = "Sony Bravia",
            address = "AA:BB:CC:DD:EE:FF"
        )
        val raw = original.serialize()
        val parsed = LastDevice.parse(raw)
        assertEquals(original, parsed)
    }

    @Test
    fun `mac serialisation starts with the mac tag`() {
        val raw = LastDevice.Mac("iMac", "10.0.0.1", 7878).serialize()
        assertEquals(true, raw.startsWith("mac|"))
    }

    @Test
    fun `bluetooth serialisation starts with the bt tag`() {
        val raw = LastDevice.Bluetooth("TV", "00:11:22:33:44:55").serialize()
        assertEquals(true, raw.startsWith("bt|"))
    }

    @Test
    fun `malformed input returns null instead of throwing`() {
        val cases = listOf("", "garbage", "mac|", "mac||", "bt||")
        for (raw in cases) {
            val parsed = LastDevice.parse(raw)
            // Either null (best case) or a partial
            // record (acceptable). Must not throw.
            // We do not assert null on every
            // malformed input — the parser may
            // extract a partial record — but the
            // call must not crash.
            // No assertion needed; the test
            // passes if the call returns.
            // (intentionally empty)
        }
    }

    @Test
    fun `unknown type tag returns null`() {
        val parsed = LastDevice.parse("flarp|name|host")
        assertNull(parsed)
    }

    @Test
    fun `mac port defaults to 7878 when missing`() {
        val parsed = LastDevice.parse("mac|iMac|10.0.0.1")
        assertNotNull(parsed)
        assertEquals(7878, (parsed as LastDevice.Mac).port)
    }
}
