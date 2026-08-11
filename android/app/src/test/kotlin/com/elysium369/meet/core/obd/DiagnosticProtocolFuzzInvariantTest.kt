package com.elysium369.meet.core.obd

import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.random.Random

class DiagnosticProtocolFuzzInvariantTest {
    @Test
    fun arbitraryBoundedElmTextNeverCrashesProtocolDecoders() {
        val random = Random(0x416)
        val alphabet = "0123456789ABCDEFabcdef :?SEARCHINGNO DATA\r\n>"
        repeat(2_000) {
            val length = random.nextInt(0, 2_049)
            val raw = buildString(length) {
                repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            DiagnosticPduDecoder.decodePdus(raw)
            DiagnosticPduDecoder.decodeResponses(raw, expectedPositiveService = 0x59, requestedService = 0x19)
            CanMultiFrameParser.parse(raw)
            Mode06Parser().parse(raw)
        }
    }

    @Test
    fun embeddedPositiveOrNegativeBytesCannotBecomeServiceAuthority() {
        val embedded = listOf(
            "7E8 06 62 F1 90 59 02 7F",
            "7E8 05 41 0C 59 02 00",
            "7E8 05 41 0D 7F 19 13",
        )
        embedded.forEach { raw ->
            val responses = DiagnosticPduDecoder.decodeResponses(raw, 0x59, 0x19)
            assertFalse(responses.any { it is ProtocolResponse.Positive || it is ProtocolResponse.Negative })
            assertFalse(DtcScanEngine.parseUdsService19ByEcu(raw, "7E0", "ECM").isNotEmpty())
        }
    }
}
