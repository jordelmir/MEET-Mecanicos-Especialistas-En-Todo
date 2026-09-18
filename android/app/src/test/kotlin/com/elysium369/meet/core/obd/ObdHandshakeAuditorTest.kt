package com.elysium369.meet.core.obd

import com.elysium369.meet.core.obd.audit.ObdHandshakeAuditor
import com.elysium369.meet.core.obd.model.AdapterChipFamily
import com.elysium369.meet.core.transport.TransportInterface
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedList

class ObdHandshakeAuditorTest {

    private class FakeTransport(
        private val cannedResponses: Map<String, String> = emptyMap()
    ) : TransportInterface {
        override var isConnected: Boolean = true
        private val _linkState = MutableStateFlow<TransportLinkState>(TransportLinkState.Connected)
        override val linkState: StateFlow<TransportLinkState> = _linkState
        private val _linkEvents = MutableSharedFlow<TransportLinkEvent>()
        override val linkEvents: SharedFlow<TransportLinkEvent> = _linkEvents

        private var lastWrittenCommand: String = ""
        private val responseQueue = LinkedList<ByteArray>()

        override suspend fun connect() {}
        override fun abortConnect() {}
        override suspend fun disconnect() { isConnected = false }
        override suspend fun drain() { responseQueue.clear() }

        override suspend fun write(data: ByteArray) {
            val cmd = String(data).trim()
            lastWrittenCommand = cmd
            val resp = cannedResponses[cmd] ?: "OK>"
            responseQueue.add(resp.toByteArray())
        }

        override suspend fun read(maxBytes: Int, timeoutMs: Long): ByteArray? {
            return if (responseQueue.isNotEmpty()) responseQueue.poll() else null
        }
    }

    @Test
    fun `audits vLinker adapter measuring latency and extracting voltage`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeTransport = FakeTransport(
            mapOf(
                "ATZ" to "ELM327 v2.2\r\n>",
                "ATE0" to "OK\r\n>",
                "ATL0" to "OK\r\n>",
                "ATS0" to "OK\r\n>",
                "ATH0" to "OK\r\n>",
                "ATI" to "vLinker FD+ v2.2\r\n>",
                "ATRV" to "12.6V\r\n>",
                "STI" to "STN vLinker FD\r\n>",
                "ATDPN" to "A6\r\n>",
            )
        )

        val auditor = ObdHandshakeAuditor(fakeTransport, testDispatcher)
        val report = auditor.performAudit(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "vLinker FD+"
        )

        assertEquals("AA:BB:CC:DD:EE:FF", report.deviceAddress)
        assertEquals("vLinker FD+", report.deviceName)
        assertEquals(AdapterChipFamily.VLINKER, report.detectedFamily)
        assertFalse(report.isClone)
        assertTrue(report.supportsCanFd)
        assertTrue(report.supportsIsoTp)
        assertNotNull(report.voltage)
        assertEquals(12.6f, report.voltage!!, 0.01f)
        assertTrue("Latency sample count must be positive", report.samplesCount > 0)
        assertTrue("Min latency must be positive", report.minLatencyMs >= 0L)
        assertTrue("Max latency must be >= min latency", report.maxLatencyMs >= report.minLatencyMs)

        val json = report.toJson()
        assertTrue(json.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(json.contains("VLINKER"))
    }

    @Test
    fun `audits generic ELM327 clone and identifies clone limitations`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val fakeTransport = FakeTransport(
            mapOf(
                "ATZ" to "ELM327 v1.5\r\n>",
                "ATE0" to "OK\r\n>",
                "ATL0" to "OK\r\n>",
                "ATS0" to "OK\r\n>",
                "ATH0" to "OK\r\n>",
                "ATI" to "ELM327 v1.5\r\n>",
                "ATRV" to "11.8V\r\n>",
                "STI" to "?\r\n>",
                "ATDPN" to "0\r\n>",
            )
        )

        val auditor = ObdHandshakeAuditor(fakeTransport, testDispatcher)
        val report = auditor.performAudit(
            deviceAddress = "11:22:33:44:55:66",
            deviceName = "OBDII Mini"
        )

        assertEquals(AdapterChipFamily.ELM327_CLONE, report.detectedFamily)
        assertTrue(report.isClone)
        assertFalse(report.supportsCanFd)
        assertFalse(report.supportsIsoTp)
        assertEquals(11.8f, report.voltage!!, 0.01f)
    }
}
