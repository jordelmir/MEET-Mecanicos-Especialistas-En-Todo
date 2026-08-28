package com.elysium369.meet.core.transport

import org.junit.Assert.*
import org.junit.Test

class CircularByteRingBufferTest {

    @Test
    fun writeAndReadSequentialData() {
        val ring = CircularByteRingBuffer(capacity = 32)
        val data = "41 0C 1A F8\r\n>".toByteArray(Charsets.US_ASCII)
        
        val dropped = ring.write(data, 0, data.size)
        assertEquals(0, dropped)
        assertEquals(data.size, ring.size())
        assertTrue(ring.hasPromptOrCapacity(100))

        val read = ring.readAvailable()
        assertNotNull(read)
        assertEquals(String(data, Charsets.US_ASCII), String(read!!, Charsets.US_ASCII))
        assertEquals(0, ring.size())
    }

    @Test
    fun promptDetectionWithoutAllocations() {
        val ring = CircularByteRingBuffer(capacity = 64)
        val chunk1 = "SEARCHING...\r\n".toByteArray(Charsets.US_ASCII)
        ring.write(chunk1, 0, chunk1.size)
        assertFalse("Prompt '>' not yet received", ring.hasPromptOrCapacity(100))

        val chunk2 = "41 00 BE 1F B8 10\r\n>".toByteArray(Charsets.US_ASCII)
        ring.write(chunk2, 0, chunk2.size)
        assertTrue("Prompt '>' detected immediately", ring.hasPromptOrCapacity(100))
    }

    @Test
    fun overflowDropsOldestBytesAndEmitsDroppedCount() {
        val ring = CircularByteRingBuffer(capacity = 16)
        val data1 = "1234567890".toByteArray(Charsets.US_ASCII) // 10 bytes
        ring.write(data1, 0, data1.size)
        assertEquals(10, ring.size())

        val data2 = "ABCDEFGHIJ".toByteArray(Charsets.US_ASCII) // 10 bytes (total 20 -> overflow by 4)
        val dropped = ring.write(data2, 0, data2.size)
        assertEquals(4, dropped)
        assertEquals(16, ring.size())

        val read = ring.readAvailable()
        assertNotNull(read)
        // First 4 bytes of data1 ("1234") were dropped, so remaining is "567890" + "ABCDEFGHIJ" = 16 bytes
        assertEquals("567890ABCDEFGHIJ", String(read!!, Charsets.US_ASCII))
    }
}
