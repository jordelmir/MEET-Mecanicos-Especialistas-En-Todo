package com.elysium369.meet.core.transport

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RecordedDiagnosticTransportTest {
    @Test
    fun replaysCapturedChunksWithoutSpecialParserPath() = runBlocking {
        val transport = RecordedDiagnosticTransport(
            listOf(
                RecordedDiagnosticFrame(
                    sequence = 1,
                    expectedRequest = "03\r".toByteArray(),
                    responseChunks = listOf("7E8 04 43 01".toByteArray(), " 23 00\r>".toByteArray()),
                ),
            ),
        )
        transport.connect()
        transport.write("03\r".toByteArray())
        assertArrayEquals("7E8 04 43 01".toByteArray(), transport.read(1024))
        assertArrayEquals(" 23 00\r>".toByteArray(), transport.read(1024))
        assertTrue(transport.isFullyConsumed)
    }

    @Test
    fun wrongOrOutOfOrderRequestFailsClosed() = runBlocking {
        val transport = RecordedDiagnosticTransport(
            listOf(
                RecordedDiagnosticFrame(4, "1902FF".toByteArray(), listOf("5902FF".toByteArray())),
            ),
        )
        transport.connect()
        val failure = runCatching { transport.write("14FFFFFF".toByteArray()) }.exceptionOrNull()
        assertTrue(failure is RecordedTraceMismatchException)
        assertEquals(0, transport.remainingFrameCount)
    }

    @Test
    fun capturedTimeoutIsAnExplicitTransportFailure() = runBlocking {
        val transport = RecordedDiagnosticTransport(
            listOf(
                RecordedDiagnosticFrame(
                    sequence = 8,
                    expectedRequest = "07\r".toByteArray(),
                    responseChunks = emptyList(),
                    readError = "TIMEOUT",
                ),
            ),
        )
        transport.connect()
        transport.write("07\r".toByteArray())
        assertTrue(runCatching { transport.read(512, 50) }.exceptionOrNull() is IOException)
    }
}
