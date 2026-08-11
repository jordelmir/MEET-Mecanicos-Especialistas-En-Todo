package com.elysium369.meet.core.obd

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiagnosticNoFabricationInvariantTest {
    @Test
    fun malformedAndNonDtcPayloadsNeverCreateFindings() {
        val nonDtcInputs = listOf(
            "",
            "NO DATA",
            "SEARCHING...",
            "7E8 41 0C 1A F8",
            "7E8 46 A2 00 05 00 00",
            "7E8 7F 19 13",
            "7E8 59 02 FF 01",
            "7E8 59 02 FF 01 23 45",
            "7E8 43 01",
        )
        nonDtcInputs.forEach { raw ->
            assertTrue(DtcScanEngine.parseStandardByEcu(raw, "03").isEmpty())
            assertTrue(DtcScanEngine.parseUdsService19ByEcu(raw, "7E0", "ECM").isEmpty())
        }
    }

    @Test
    fun arbitraryElmTextDoesNotCrashOrCrossEcuBoundaries() {
        val random = Random(416)
        val alphabet = "0123456789ABCDEF?: SEARCHINGNO DATA\r\n"
        repeat(500) {
            val raw = buildString {
                repeat(random.nextInt(0, 96)) {
                    append(alphabet[random.nextInt(alphabet.length)])
                }
            }
            runCatching { DtcScanEngine.parseStandardByEcu(raw, "03") }.getOrThrow()
            runCatching { DtcScanEngine.parseUdsService19ByEcu(raw, "7E0", "ECM") }.getOrThrow()
        }
    }
}
