package com.elysium369.meet.core.obd

import com.elysium369.meet.core.transport.TransportInterface
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmNegotiatorLegacyEcuTest {

    private class ScriptedIso9141CloneTransport : TransportInterface {
        val commands = mutableListOf<String>()
        private var lastCommand = ""
        private var selectedProtocol = ""
        private val state = MutableStateFlow<TransportLinkState>(TransportLinkState.Connected)
        private val events = MutableSharedFlow<TransportLinkEvent>(extraBufferCapacity = 8)

        override suspend fun connect() = Unit
        override fun abortConnect() = Unit
        override suspend fun disconnect() = Unit
        override suspend fun drain() = Unit
        override val isConnected: Boolean = true
        override val linkState: StateFlow<TransportLinkState> = state
        override val linkEvents: SharedFlow<TransportLinkEvent> = events

        override suspend fun write(data: ByteArray) {
            lastCommand = String(data).trim().uppercase()
            if (lastCommand.isNotBlank()) commands += lastCommand
            if (lastCommand.startsWith("ATSP")) selectedProtocol = lastCommand.removePrefix("ATSP")
        }

        override suspend fun read(maxBytes: Int, timeoutMs: Long): ByteArray = when {
            lastCommand == "ATZ" || lastCommand == "ATI" -> "ELM327 v2.1\r>"
            lastCommand == "ATRV" -> "12.4V\r>"
            lastCommand == "STI" -> "?\r>"
            lastCommand == "0100" && selectedProtocol == "3" -> "41 00 BE 3E A8 13\r>"
            lastCommand == "0100" -> "NO DATA\r>"
            else -> "OK\r>"
        }.toByteArray()
    }

    @Test
    fun hyundaiPreCanUsesSafeIsoFunctionalPathBeforeSpeculativeKwp() = runBlocking {
        val transport = ScriptedIso9141CloneTransport()
        val evidence = mutableListOf<ElmNegotiator.NegotiationEvidence>()

        val profile = ElmNegotiator(transport).negotiate(
            manufacturerHint = "Hyundai",
            vehicleYear = 2005,
            onEvidence = evidence::add,
            onProgress = {},
        )

        assertEquals(ObdProtocol.ISO9141, profile.detectedProtocol)
        assertTrue(transport.commands.contains("ATAT0"))
        assertFalse(transport.commands.contains("ATAT1"))
        assertTrue(transport.commands.indexOf("ATIB10") < transport.commands.indexOf("ATSP3"))
        assertTrue(transport.commands.indexOf("ATSP3") < transport.commands.indexOf("ATSH686AF1"))
        assertFalse(transport.commands.any { it == "ATSP4" || it == "ATSP5" })
        assertEquals(1, transport.commands.count { it == "0100" })
        assertTrue(evidence.any {
            it.type == ElmNegotiator.EvidenceType.PROTOCOL_VERIFIED &&
                it.protocol == ObdProtocol.ISO9141
        })
    }
}
